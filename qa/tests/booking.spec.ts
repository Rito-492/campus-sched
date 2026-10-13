/**
 * D-AT-03 首次预约成功主线（E2E，QA-04 交付）。
 *
 * 用例依据：`agents/common/03-验收与演示数据.md` §2 D-AT-03
 *   「新建→编辑→提交→批准人同意→资源确认→发布」；
 *   关键断言：首次资源确认前 0 条 ACTIVE 预约，确认后完整数量。
 * 契约依据：02 §2 ActivityStatus/ActivityInput、§3 API-12/15/16/18/19/20/22、§2 Conflict；
 *           05 §2 /activities/new（activity-submit）、§3「确认与结果」、§4 公共 data-testid。
 *
 * 状态语义（02 §2）：DRAFT →（提交）PENDING_APPROVER →（批准人同意）APPROVED_UNRESERVED
 *   →（资源确认 SCHEDULED）→（发布）OPEN。
 *
 * 角色隔离：applicant_a / approver_a / resource_admin / student_a 各自独立 context。
 * 说明：新建表单除 activity-submit 外的字段 testid 在 05 §4 未定义，使用可访问名称/标签定位并
 *   在注释标注「testid 待 UI 提供，见 handoff」；编辑/提交无公共 testid，用真实 API-15/16 保证
 *   确定性并保留 reload 持久化核对。本 spec 会真实写入演示库（乐观期望独立 E2E 环境）。
 */
import { expect, test } from '@playwright/test';
import {
  DEMO_DATE,
  RESOURCES,
  TESTID,
  USERS,
  USER_IDS,
  apiClose,
  apiLogin,
  closeSessions,
  createActivityViaUi,
  editActivity,
  getActivity,
  getCalendar,
  openSession,
  planFor,
  readRegistrations,
  submitActivity,
  type ActivityInputDto,
  type ApiSession,
  type UiSession,
} from './helpers.js';

/** 目标计划：避开演示活动 A/B 占用，选 18:00—19:00、场地 3102 + 器材 3202×1。 */
const NEW_PLAN = planFor(
  RESOURCES.venueB.id,
  [{ resourceId: RESOURCES.equipmentB.id, quantity: 1 }],
  '18:00',
  '19:00',
  0,
  0,
);

const NEW_ACTIVITY: ActivityInputDto = {
  title: 'D-AT-03 首次预约主线（测试数据）',
  description: '验证新建→编辑→提交→批准→资源确认→发布→报名。（测试数据）',
  expectedAttendees: 30,
  registrationCapacity: 20,
  staffUserIds: [],
  plan: NEW_PLAN,
};

/** 该活动相关的日历占用条目（API-09：资源确认后应出现完整数量的 ACTIVE 预约）。 */
async function calendarEntriesFor(api: ApiSession, activityId: string): Promise<{ resourceId: string; quantity: number }[]> {
  const entries = await getCalendar(api, `${DEMO_DATE}T00:00:00+08:00`, `${DEMO_DATE}T23:59:00+08:00`);
  return entries.filter((e) => e.activityId === activityId).map((e) => ({ resourceId: e.resourceId, quantity: e.quantity }));
}

test.describe('D-AT-03 首次预约主线（新建→编辑→提交→批准→资源确认→发布→报名）', () => {
  test(
    'TC-03-1 applicant_a 新建并提交，approver_a 同意、resource_admin 确认、发布后 student_a 报名成功',
    { annotation: [{ type: 'D-AT', description: 'D-AT-03' }, { type: 'TC', description: 'TC-03-1' }] },
    async ({ browser }) => {
      const sessions: UiSession[] = [];
      const apis: ApiSession[] = [];
      try {
        // 1) applicant_a 登录并经 UI 新建活动
        const applicant = await openSession(browser, USERS.applicantA);
        sessions.push(applicant);
        const activityId = await createActivityViaUi(applicant.page, {
          title: NEW_ACTIVITY.title,
          description: NEW_ACTIVITY.description,
          expectedAttendees: NEW_ACTIVITY.expectedAttendees,
          registrationCapacity: NEW_ACTIVITY.registrationCapacity,
          startAt: NEW_ACTIVITY.plan.startAt,
          endAt: NEW_ACTIVITY.plan.endAt,
          venueName: RESOURCES.venueB.name,
          equipmentNames: [RESOURCES.equipmentB.name],
        });

        const ownerApi = await apiLogin(USERS.applicantA);
        const approverApi = await apiLogin(USERS.approverA);
        const resourceApi = await apiLogin(USERS.resourceAdmin);
        const studentApi = await apiLogin(USERS.studentA);
        apis.push(ownerApi, approverApi, resourceApi, studentApi);

        // 2) 编辑（API-15，无公共 testid）：标题更新且锁版本推进
        let activity = await getActivity(ownerApi, activityId);
        expect(activity.status, '新建后应 DRAFT（02 §2）').toBe('DRAFT');
        const edited = await editActivity(ownerApi, activityId, {
          expectedLockVersion: activity.lockVersion,
          input: { ...NEW_ACTIVITY, title: `${NEW_ACTIVITY.title}（已编辑）` },
        });
        expect(edited.input.title, '编辑应真实保存新标题').toBe(`${NEW_ACTIVITY.title}（已编辑）`);

        // 3) 提交（API-16）→ PENDING_APPROVER
        activity = await submitActivity(ownerApi, activityId, edited.lockVersion);
        expect(activity.status, '提交后应 PENDING_APPROVER（02 §2）').toBe('PENDING_APPROVER');

        // 4) reload 持久化：提交状态来自真实后端而非内存
        await applicant.page.goto(`/activities/${activityId}`);
        await expect(applicant.page.getByText(/PENDING_APPROVER|待批准人审批|待审批/).first()).toBeVisible();
        await applicant.page.reload();
        await expect(applicant.page.getByText(/PENDING_APPROVER|待批准人审批|待审批/).first()).toBeVisible();

        // 5) 首次资源确认前：0 条 ACTIVE 预约（D-AT-03 关键断言）
        expect(await calendarEntriesFor(ownerApi, activityId), '资源确认前不得有预约占用').toEqual([]);

        // 6) approver_a 经 UI 同意（05 §4 approver-approve）→ APPROVED_UNRESERVED
        const approver = await openSession(browser, USERS.approverA);
        sessions.push(approver);
        await approver.page.goto(`/activities/${activityId}`);
        const approveResponse = approver.page.waitForResponse((res) =>
          res.url().includes(`/activities/${activityId}/approver-decision`),
        );
        await approver.page.getByTestId(TESTID.approverApprove).click();
        expect((await approveResponse).status(), '批准人同意应 200').toBe(200);
        expect((await getActivity(ownerApi, activityId)).status, '批准后应 APPROVED_UNRESERVED').toBe('APPROVED_UNRESERVED');

        // 7) resource_admin 经 UI 确认（05 §4 resource-confirm）→ SCHEDULED，完整数量预约
        const resource = await openSession(browser, USERS.resourceAdmin);
        sessions.push(resource);
        await resource.page.goto(`/activities/${activityId}`);
        const confirmResponse = resource.page.waitForResponse((res) =>
          res.url().includes(`/activities/${activityId}/resource-confirm`),
        );
        await resource.page.getByTestId(TESTID.resourceConfirm).click();
        expect((await confirmResponse).status(), '资源确认应 200').toBe(200);
        expect((await getActivity(ownerApi, activityId)).status, '资源确认后应 SCHEDULED').toBe('SCHEDULED');

        const entries = await calendarEntriesFor(ownerApi, activityId);
        expect(
          entries.some((e) => e.resourceId === RESOURCES.venueB.id),
          '资源确认后应出现场地 3102 的 ACTIVE 预约',
        ).toBe(true);
        expect(
          entries.some((e) => e.resourceId === RESOURCES.equipmentB.id && e.quantity > 0),
          '资源确认后应出现器材 3202 的 ACTIVE 预约且数量完整',
        ).toBe(true);

        // 8) applicant_a 发布（无公共 testid，用可访问名称）→ OPEN
        const publishButton = applicant.page.getByRole('button', { name: /发布/ });
        await applicant.page.goto(`/activities/${activityId}`);
        await publishButton.click();
        await expect(applicant.page.getByText(/OPEN|报名中/).first()).toBeVisible();
        expect((await getActivity(ownerApi, activityId)).status, '发布后应 OPEN（02 §2）').toBe('OPEN');

        // 9) student_a 登录并报名（报名入口 testid 待 UI 提供，见 handoff，用可访问名称）
        const student = await openSession(browser, USERS.studentA);
        sessions.push(student);
        await student.page.goto(`/activities/${activityId}`);
        await student.page.getByRole('button', { name: /报名|参加/ }).first().click();
        await expect(student.page.getByText(/报名成功|已报名|ACTIVE/).first()).toBeVisible();

        // 10) 断言名额与状态：报名 ACTIVE、计数 1 且不超过 registrationCapacity
        const registrations = await readRegistrations(studentApi, activityId);
        const mine = registrations.find((r) => r.userId === USER_IDS.studentA);
        expect(mine?.status, 'student_a 报名应为 ACTIVE').toBe('ACTIVE');
        expect((await getActivity(studentApi, activityId)).myRegistrationStatus, '本人报名状态应为 ACTIVE').toBe('ACTIVE');
        const afterJoin = await getActivity(ownerApi, activityId);
        expect(afterJoin.registrationCount, '报名人数应为 1').toBe(1);
        expect(afterJoin.registrationCount, '报名人数不得超过 registrationCapacity').toBeLessThanOrEqual(
          afterJoin.input.registrationCapacity,
        );

        // 11) reload 后报名状态仍在（真实持久化）
        await student.page.reload();
        await expect(student.page.getByText(/已报名|报名成功|ACTIVE/).first()).toBeVisible();
      } finally {
        await closeSessions(sessions);
        await apiClose(apis);
      }
    },
  );
});
