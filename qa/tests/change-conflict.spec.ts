/**
 * D-AT-09 / D-AT-11 / D-AT-12 / D-AT-18 改期冲突、拒绝、抢占与资源故障（E2E）。
 *
 * 用例依据：`docs/testing/test-design.md` TC-09-2、TC-11-1、TC-11-2、TC-12-1、TC-18-1；
 * 契约依据：02 §2（Conflict/ConflictCode、ChangeStatus、Risk）、§3（API-24/25/26/27/30/31/32、API-10/11）、
 *           §6（原子改期与 CONFLICTED 语义）、01 §6/§7/§8；
 *           05 §3 冲突与替代方案 / 变更差异。
 *
 * 状态语义（02 §2）：预览只读不改库；最终资源确认失去可行性 → 409 RESOURCE_CONFLICT、
 *   改期 CONFLICTED、原安排保留；工作人员/教师拒绝 → REJECTED、原版本与预约完全保留。
 * 资源故障：原 reservation 仍在但 ActivityView.resourceRisk=AT_RISK，页面必须展示风险提示。
 *
 * 数据准备说明：抢占活动与故障登记通过真实 REST（API-12/16/18/19、API-10）准备，
 *   不是 Mock；UI 主线（预览、拒绝、最终确认、风险展示）仍走浏览器。
 * 注意：本 spec 会真实修改演示活动 A（乐观期望独立 E2E 环境，跑前重置数据）。
 */
import { expect, test, type Page } from '@playwright/test';
import {
  ACTIVITIES,
  RESOURCES,
  RISK,
  TESTID,
  USERS,
  apiClose,
  apiLogin,
  at,
  changePreview,
  changeTeacherDecision,
  closeSessions,
  confirmChange,
  createActivity,
  createChange,
  createResourceFault,
  expectChangeStatus,
  expectCurrentVersion,
  fillChangeTargetTime,
  getActivity,
  getChange,
  getResourceFaults,
  openChangeWorkbench,
  openSession,
  planFor,
  readError,
  resourceConfirmActivity,
  submitActivity,
  teacherDecideActivity,
  type ActivityInputDto,
  type ApiSession,
  type PlanDto,
  type UiSession,
} from './helpers.js';

/** 首选替代方案：同时间 15:00—16:00，换场地 3102 + 换器材 3202×2（03 §1 固定数据）。 */
const PREFERRED_PLAN: PlanDto = planFor(
  RESOURCES.venueB.id,
  [{ resourceId: RESOURCES.equipmentB.id, quantity: 2 }],
  '15:00',
  '16:00',
);

/** 以活动 A 当前版本创建一次可行改期，返回 changeId（A 的 staff 仅 staff_a）。 */
async function createFeasibleChange(api: ApiSession): Promise<string> {
  const activity = await getActivity(api, ACTIVITIES.a.id);
  const change = await createChange(api, ACTIVITIES.a.id, {
    baseVersionNo: activity.versionNo,
    targetPlan: PREFERRED_PLAN,
    expectedLockVersion: activity.lockVersion,
    reason: 'D-AT 改期演练（测试数据）',
  });
  return change.id;
}

/** 另一组织者 organizer_b 创建并确认一个真实占用首选资源的活动（预览后抢占）。 */
async function preemptPreferredResources(): Promise<void> {
  const owner = await apiLogin(USERS.organizerB);
  const teacher = await apiLogin(USERS.teacherA);
  const resource = await apiLogin(USERS.resourceAdmin);
  try {
    const input: ActivityInputDto = {
      title: 'D-AT-12 抢占活动（测试数据）',
      description: '用于验证预览可行后被抢占最终资源确认。（测试数据）',
      expectedAttendees: 10,
      registrationCapacity: 1,
      staffUserIds: [],
      plan: planFor(
        RESOURCES.venueB.id,
        [{ resourceId: RESOURCES.equipmentB.id, quantity: 2 }],
        '15:00',
        '16:00',
        0,
        0,
      ),
    };
    let created = await createActivity(owner, input);
    created = await submitActivity(owner, created.id, created.lockVersion);
    created = await teacherDecideActivity(teacher, created.id, 'APPROVE', created.lockVersion);
    created = await resourceConfirmActivity(resource, created.id, created.lockVersion);
    expect(created.status, '抢占活动应 SCHEDULED 以真实占用候选资源').toBe('SCHEDULED');
  } finally {
    await apiClose([owner, teacher, resource]);
  }
}

/** 拒绝改期（05 §3：拒绝需填理由；弹窗字段 testid 待 UI 提供，见 handoff，用 dialog 语义定位）。 */
async function rejectChangeViaUi(page: Page, reason: string): Promise<void> {
  await page.getByRole('button', { name: /拒绝|驳回/ }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await dialog.getByLabel(/理由|原因/).fill(reason);
  await dialog.getByRole('button', { name: /确定|确认|提交/ }).click();
}

/** 原安排保留：活动仍是 version1，仍为原场地（3101）与原时间（10:00）。 */
async function expectOriginalPlanPreserved(page: Page): Promise<void> {
  await page.goto(`/activities/${ACTIVITIES.a.id}`);
  await expectCurrentVersion(page, 1);
  await expect(page.getByText(RESOURCES.venueA.name).first()).toBeVisible();
  await expect(page.getByText(/10:00/).first()).toBeVisible();
}

test.describe('D-AT-09 改期冲突与替代方案（UI）', () => {
  test(
    'TC-09-2 预览同时展示场地占用与器材缺件，首选替代为 3102+3202×2 同时间',
    { annotation: [{ type: 'D-AT', description: 'D-AT-09' }, { type: 'TC', description: 'TC-09-2' }] },
    async ({ browser }) => {
      const sessions: UiSession[] = [];
      try {
        const organizer = await openSession(browser, USERS.organizerA);
        sessions.push(organizer);

        await openChangeWorkbench(organizer.page, ACTIVITIES.a.id);
        // 目标 15:00—16:00，保持原场地 3101 + 器材 3201×2 → 触发两类冲突
        await fillChangeTargetTime(organizer.page, '15:00', '16:00');
        await organizer.page.getByTestId(TESTID.changePreview).click();

        const conflictList = organizer.page.getByTestId(TESTID.conflictList);
        await expect(conflictList).toBeVisible();
        // VENUE_OCCUPIED：B（社团交流会）占 3101 [14:30,16:30) 与目标重叠
        await expect(conflictList).toContainText(RESOURCES.venueA.name);
        // EQUIPMENT_SHORTAGE：3201 库存 2 − B 占 1 = 可用 1 < 需求 2
        await expect(conflictList).toContainText(RESOURCES.equipmentA.name);

        const alternative0 = organizer.page.getByTestId(TESTID.alternative0);
        await expect(alternative0).toBeVisible();
        // 05 §3：首个方案标「当前排序首选」，不能写全局最优
        await expect(alternative0).toContainText(/首选/);
        // 首选替代：同时间 15:00，换场地 3102、换器材 3202×2
        await expect(alternative0).toContainText(RESOURCES.venueB.name);
        await expect(alternative0).toContainText(RESOURCES.equipmentB.name);
        await expect(alternative0).toContainText('15:00');

        // 预览只读：活动 A 仍为 version1（未回写）
        await organizer.page.goto(`/activities/${ACTIVITIES.a.id}`);
        await expectCurrentVersion(organizer.page, 1);
      } finally {
        await closeSessions(sessions);
      }
    },
  );
});

test.describe('D-AT-11 拒绝改期（原安排保护）', () => {
  test(
    'TC-11-1 staff_a 拒绝 → REJECTED，原安排与版本完全保留',
    { annotation: [{ type: 'D-AT', description: 'D-AT-11' }, { type: 'TC', description: 'TC-11-1' }] },
    async ({ browser }) => {
      const sessions: UiSession[] = [];
      const apis: ApiSession[] = [];
      try {
        const organizerApi = await apiLogin(USERS.organizerA);
        apis.push(organizerApi);
        const changeId = await createFeasibleChange(organizerApi);

        const staff = await openSession(browser, USERS.staffA);
        sessions.push(staff);
        await staff.page.goto(`/changes/${changeId}`);
        await expectChangeStatus(staff.page, /AWAITING_CONFIRMATIONS|待确认/);
        await rejectChangeViaUi(staff.page, '时间冲突');
        await expectChangeStatus(staff.page, /REJECTED|已拒绝|驳回/);

        // 持久化终态与保留性（00 §2 不只是 HTTP）
        const change = await getChange(organizerApi, changeId);
        expect(change.status, '改期应 REJECTED').toBe('REJECTED');
        expect(change.confirmations.some((c) => c.decision === 'REJECT'), '应记录 staff 拒绝').toBe(true);
        const activity = await getActivity(organizerApi, ACTIVITIES.a.id);
        expect(activity.versionNo, '原版本号不变').toBe(1);
        expect(activity.effectiveVersionNo, '有效版本仍为 1').toBe(1);

        const organizer = await openSession(browser, USERS.organizerA);
        sessions.push(organizer);
        await expectOriginalPlanPreserved(organizer.page);
      } finally {
        await closeSessions(sessions);
        await apiClose(apis);
      }
    },
  );

  test(
    'TC-11-2 教师驳回 → REJECTED，版本指针不变且意见落库',
    { annotation: [{ type: 'D-AT', description: 'D-AT-11' }, { type: 'TC', description: 'TC-11-2' }] },
    async ({ browser }) => {
      const sessions: UiSession[] = [];
      const apis: ApiSession[] = [];
      try {
        const organizerApi = await apiLogin(USERS.organizerA);
        apis.push(organizerApi);
        const changeId = await createFeasibleChange(organizerApi);

        const staffApi = await apiLogin(USERS.staffA);
        apis.push(staffApi);
        await confirmChange(staffApi, changeId, 'APPROVE');

        const teacher = await openSession(browser, USERS.teacherA);
        sessions.push(teacher);
        await teacher.page.goto(`/changes/${changeId}`);
        await expectChangeStatus(teacher.page, /PENDING_TEACHER|待教师审批/);
        await rejectChangeViaUi(teacher.page, '不同意');
        await expectChangeStatus(teacher.page, /REJECTED|已拒绝|驳回/);

        const change = await getChange(organizerApi, changeId);
        expect(change.status, '改期应 REJECTED').toBe('REJECTED');
        expect(change.teacherDecision?.decision, 'teacher_decision_json 应记录 REJECT').toBe('REJECT');
        const activity = await getActivity(organizerApi, ACTIVITIES.a.id);
        expect(activity.versionNo, '原版本指针不变').toBe(1);
        expect(activity.effectiveVersionNo, '有效版本不变').toBe(1);
      } finally {
        await closeSessions(sessions);
        await apiClose(apis);
      }
    },
  );
});

test.describe('D-AT-12 预览后被抢占（最终确认冲突）', () => {
  test(
    'TC-12-1 候选资源被占 → 409 RESOURCE_CONFLICT、改期 CONFLICTED、原安排保留',
    { annotation: [{ type: 'D-AT', description: 'D-AT-12' }, { type: 'TC', description: 'TC-12-1' }] },
    async ({ browser }) => {
      const sessions: UiSession[] = [];
      const apis: ApiSession[] = [];
      try {
        const organizerApi = await apiLogin(USERS.organizerA);
        apis.push(organizerApi);
        const before = await getActivity(organizerApi, ACTIVITIES.a.id);

        // 抢占前预览可行（无冲突）
        const preview = await changePreview(organizerApi, ACTIVITIES.a.id, {
          baseVersionNo: before.versionNo,
          targetPlan: PREFERRED_PLAN,
        });
        expect(preview.check.feasible, '初始预览应可行').toBe(true);
        expect(preview.check.conflicts, '初始预览应无冲突').toEqual([]);

        // 另一活动先占用候选资源 3102 + 3202×2
        await preemptPreferredResources();

        const changeId = await createFeasibleChange(organizerApi);
        const staffApi = await apiLogin(USERS.staffA);
        apis.push(staffApi);
        await confirmChange(staffApi, changeId, 'APPROVE');
        const teacherApi = await apiLogin(USERS.teacherA);
        apis.push(teacherApi);
        await changeTeacherDecision(teacherApi, changeId, 'APPROVE');

        // resource_admin 最终确认：重新检查发现冲突 → 409，事务正常提交 CONFLICTED 记录
        const resource = await openSession(browser, USERS.resourceAdmin);
        sessions.push(resource);
        await resource.page.goto(`/changes/${changeId}`);
        const decisionResponse = resource.page.waitForResponse((res) =>
          res.url().includes(`/changes/${changeId}/resource-decision`),
        );
        await resource.page.getByTestId(TESTID.changeResourceApprove).click();
        const err = await readError(
          await decisionResponse,
          409,
          '最终资源确认应 409 RESOURCE_CONFLICT',
        );
        expect(err.error.code, '错误码应为 RESOURCE_CONFLICT').toBe('RESOURCE_CONFLICT');
        const details = err.error.details as { change?: { status?: string } } | undefined;
        expect(details?.change?.status, '409 details.change 应为 CONFLICTED（02 §3）').toBe('CONFLICTED');

        await expectChangeStatus(resource.page, /CONFLICTED|冲突/);
        // UI 收到 409 显示原因（02 §1：保留未提交表单/展示原因）
        await expect(resource.page.getByRole('alert').filter({ hasText: /冲突|占用|不可行|失败/ })).toBeVisible();

        // 原有效安排保留：无新版本、旧预约仍 ACTIVE
        const after = await getActivity(organizerApi, ACTIVITIES.a.id);
        expect(after.versionNo, '抢占失败不得产生新版本').toBe(1);
        expect(after.effectiveVersionNo, '有效版本保持 1').toBe(1);

        const organizer = await openSession(browser, USERS.organizerA);
        sessions.push(organizer);
        await expectOriginalPlanPreserved(organizer.page);
      } finally {
        await closeSessions(sessions);
        await apiClose(apis);
      }
    },
  );
});

test.describe('D-AT-18 资源故障与风险提示', () => {
  test(
    'TC-18-1 故障使 A 原安排标 AT_RISK，页面展示风险提示且不谎报可用',
    { annotation: [{ type: 'D-AT', description: 'D-AT-18' }, { type: 'TC', description: 'TC-18-1' }] },
    async ({ browser }) => {
      const sessions: UiSession[] = [];
      const apis: ApiSession[] = [];
      try {
        const organizerApi = await apiLogin(USERS.organizerA);
        apis.push(organizerApi);
        const resourceApi = await apiLogin(USERS.resourceAdmin);
        apis.push(resourceApi);

        // 对 A 当前实际占用的场地创建重叠故障（venue quantity=1）
        const activity = await getActivity(organizerApi, ACTIVITIES.a.id);
        const plan = activity.input.plan;
        const fault = await createResourceFault(resourceApi, plan.venueId, {
          startAt: plan.startAt,
          endAt: plan.endAt,
          unavailableQuantity: 1,
          reason: '设备检修（测试数据）',
        });
        expect(
          fault.affectedActivities.map((a) => a.activityId),
          'FaultView.affectedActivities 应含活动 A（02 §2）',
        ).toContain(ACTIVITIES.a.id);

        const faults = await getResourceFaults(resourceApi, plan.venueId, at('00:00'), at('23:59'));
        expect(faults.length, 'resource_fault 应落库').toBeGreaterThanOrEqual(1);

        // 原 reservation 仍在但查询时计算为 AT_RISK
        const refreshed = await getActivity(organizerApi, ACTIVITIES.a.id);
        expect(refreshed.resourceRisk, 'ActivityView.resourceRisk 应为 AT_RISK').toBe(RISK.atRisk);

        // 页面必须展示风险提示，不能把原安排显示为可正常使用
        const organizer = await openSession(browser, USERS.organizerA);
        sessions.push(organizer);
        await organizer.page.goto(`/activities/${ACTIVITIES.a.id}`);
        await expect(
          organizer.page.getByText(/AT_RISK|资源风险|受影响|不可用/).first(),
          '页面应展示 AT_RISK/风险提示',
        ).toBeVisible();
        await expect(
          organizer.page.getByText(/可正常使用|安排正常/),
          '页面不得把原安排显示为可正常使用',
        ).toHaveCount(0);
      } finally {
        await closeSessions(sessions);
        await apiClose(apis);
      }
    },
  );
});
