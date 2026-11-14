/**
 * D-AT-10 改期成功主线 + D-AT-23 持久化恢复（E2E）。
 *
 * 用例依据：`docs/testing/test-design.md` TC-10-1（确认链走完并原子切换到 version2）、
 *           TC-23-1（刷新后状态来自真实持久化）；
 * 契约依据：02 §2 ChangeStatus、§3 API-26/27/30/31/32、§6 原子改期顺序；
 *           05 §3「确认与结果」、§4 公共 data-testid。
 *
 * 状态语义（02 契约 §2，页面中文文案由 UI 决定）：
 *   AWAITING_CONFIRMATIONS（staff_a 确认前）→ 确认 APPROVE 后 PENDING_APPROVER
 *   → 批准人同意后 PENDING_RESOURCE → 资源确认后 APPLIED；活动 version 1→2。
 *
 * 角色隔离：applicant_a / staff_a / approver_a / resource_admin / student_a 各自独立 context。
 * 改期详情用确定性路由 /changes/{id}（05 §2）直达，避免依赖列表项在页面中的位置。
 * TC-23-1 说明：浏览器 reload 覆盖进程内持久化；`docker compose restart server` 属发布
 *   演练（D-AT-24）人工步骤，不在浏览器内执行——E2E 以真实后端写库 + reload 验证非内存结果。
 *
 * 注意：本 spec 会真实修改演示活动 A（乐观期望独立 E2E 环境，跑前重置数据）。
 */
import { expect, test } from '@playwright/test';
import {
  ACTIVITIES,
  RESOURCES,
  TESTID,
  UI_TESTID,
  USERS,
  closeSessions,
  expectChangeStatus,
  expectCurrentVersion,
  fillChangeTargetTime,
  lastPathSegment,
  openChangeWorkbench,
  openSession,
  type UiSession,
} from './helpers.js';

test.describe('D-AT-10 / D-AT-23 改期成功主线与持久化', () => {
  test(
    'TC-10-1 / TC-23-1 预览选首选方案→staff/approver/resource 确认→version2→student 收到通知（含 reload 持久化）',
    {
      annotation: [
        { type: 'D-AT', description: 'D-AT-10' },
        { type: 'D-AT', description: 'D-AT-23' },
        { type: 'TC', description: 'TC-10-1' },
        { type: 'TC', description: 'TC-23-1' },
      ],
    },
    async ({ browser }) => {
      const sessions: UiSession[] = [];
      try {
        // 1) applicant_a 发起改期：目标 15:00—16:00，选首选替代方案（3102 + 3202×2）
        const organizer = await openSession(browser, USERS.applicantA);
        sessions.push(organizer);

        await openChangeWorkbench(organizer.page, ACTIVITIES.a.id);
        await fillChangeTargetTime(organizer.page, '15:00', '16:00');
        await organizer.page.getByTestId(TESTID.changePreview).click();
        await expect(organizer.page.getByTestId(TESTID.conflictList)).toBeVisible();

        // 选择候选只是把方案填入目标；必须等对最终目标的再次预览（API-26）完成，
        // 不能沿用旧影响快照（useChangeWorkspace.select → preview，真实联调记录「QA接收路径」）
        const finalPreview = organizer.page.waitForResponse((res) =>
          res.url().includes(`/activities/${ACTIVITIES.a.id}/change-preview`),
        );
        await organizer.page.getByTestId(TESTID.alternative0).click();
        expect((await finalPreview).status(), '选候选后的最终预览应 200').toBe(200);

        // 改期原因是契约必填项（02 §3 ChangeCreateDto.reason）：
        // 未填原因时 change-create 保持禁用，填原因后才可创建
        await expect(organizer.page.getByTestId(TESTID.changeCreate), '未填改期原因不得允许创建').toBeDisabled();
        await organizer.page.getByTestId(UI_TESTID.changeReason).fill('D-AT-10 改期至 15:00（测试数据）');
        await expect(organizer.page.getByTestId(TESTID.changeCreate)).toBeEnabled();
        await organizer.page.getByTestId(TESTID.changeCreate).click();
        await expect(organizer.page).toHaveURL(/\/changes\/[0-9a-f-]{36}(?:[/?#]|$)/i);
        const changeId = lastPathSegment(organizer.page.url());

        // 2) 创建后处于 AWAITING_CONFIRMATIONS（A 的 staff 含 staff_a）
        await expectChangeStatus(organizer.page, /AWAITING_CONFIRMATIONS|等待工作人员确认/);

        // 3) TC-23-1：刷新后状态仍来自真实持久化，不是仅内存成功
        await organizer.page.reload();
        await expectChangeStatus(organizer.page, /AWAITING_CONFIRMATIONS|等待工作人员确认/);

        // 4) staff_a 确认同意 → PENDING_APPROVER
        const staff = await openSession(browser, USERS.staffA);
        sessions.push(staff);
        await staff.page.goto(`/changes/${changeId}`);
        await staff.page.getByTestId(TESTID.staffApprove).click();
        await expectChangeStatus(staff.page, /PENDING_APPROVER|等待批准人复审/);

        // 5) approver_a 同意 → PENDING_RESOURCE
        const approver = await openSession(browser, USERS.approverA);
        sessions.push(approver);
        await approver.page.goto(`/changes/${changeId}`);
        await approver.page.getByTestId(TESTID.approverApprove).click();
        await expectChangeStatus(approver.page, /PENDING_RESOURCE|待资源确认/);

        // 6) resource_admin 确认 → APPLIED（原子切换，活动保留 OPEN）
        const resource = await openSession(browser, USERS.resourceAdmin);
        sessions.push(resource);
        await resource.page.goto(`/changes/${changeId}`);
        await resource.page.getByTestId(TESTID.changeResourceApprove).click();
        await expectChangeStatus(resource.page, /APPLIED|已生效/);

        // 7) applicant_a 看到当前版本 version2，且新安排为 多功能厅B / 15:00
        await organizer.page.goto(`/activities/${ACTIVITIES.a.id}`);
        await expectCurrentVersion(organizer.page, 2);
        await expect(organizer.page.getByText(RESOURCES.venueB.name).first()).toBeVisible();
        await expect(organizer.page.getByText(/15:00/).first()).toBeVisible();

        // 8) TC-23-1：刷新后 version2 仍可见（持久化而非内存）
        await organizer.page.reload();
        await expectCurrentVersion(organizer.page, 2);
        await expect(organizer.page.getByText(RESOURCES.venueB.name).first()).toBeVisible();

        // 9) student_a（有效报名人）收到改期通知；通知消费者按 outbox 轮询，交给 expect 轮询
        const student = await openSession(browser, USERS.studentA);
        sessions.push(student);
        await student.page.goto('/notifications');
        await expect(student.page.getByText(ACTIVITIES.a.title).first()).toBeVisible();
      } finally {
        await closeSessions(sessions);
      }
    },
  );
});
