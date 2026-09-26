/**
 * D-AT-21 隐私与读取隔离（E2E）。
 *
 * 用例依据：`docs/testing/test-design.md` TC-21-1（日历脱敏）、TC-21-2（名单与他人通知 403）、
 *           TC-21-3（工作人员视角的变更详情裁剪）；
 * 契约依据：02 §2（CalendarEntry/ChangeView.impact）、§3（API-09/23/29/34/35、§3 可见性规则）、
 *           01 §3（角色范围：日历无权条目只显示「已占用」、资源及时间）。
 *
 * 角色隔离：student_a / organizer_a / staff_a 各自独立 context 或真实 REST 会话（非 Mock）。
 * 说明：报名名单与部分通知页元素在 05 §4 未定义 testid，使用可访问角色/语义定位并在注释标注
 *       「testid 待 UI 提供，见 handoff」；核心隔离断言以真实 API 数据为准（驱动页面渲染）。
 */
import { expect, test } from '@playwright/test';
import {
  ACTIVITIES,
  DEMO_DATE,
  RESOURCES,
  USERS,
  apiClose,
  apiLogin,
  at,
  closeSessions,
  createChange,
  getActivity,
  getCalendar,
  getChange,
  getNotifications,
  getRegistrations,
  markNotificationRead,
  openSession,
  planFor,
  readError,
  withdrawChange,
  type ApiSession,
  type PlanDto,
  type UiSession,
} from './helpers.js';

const PREFERRED_PLAN: PlanDto = planFor(
  RESOURCES.venueB.id,
  [{ resourceId: RESOURCES.equipmentB.id, quantity: 2 }],
  '15:00',
  '16:00',
);

test.describe('D-AT-21 隐私与读取隔离', () => {
  test(
    'TC-21-1 学生日历只显示无权限占用信息，不泄露活动名称/组织者/名单',
    { annotation: [{ type: 'D-AT', description: 'D-AT-21' }, { type: 'TC', description: 'TC-21-1' }] },
    async ({ browser }) => {
      const apis: ApiSession[] = [];
      const sessions: UiSession[] = [];
      try {
        const studentApi = await apiLogin(USERS.studentA);
        apis.push(studentApi);

        // API-09 返回的条目就是日历渲染的数据源：断言脱敏语义
        const entries = await getCalendar(studentApi, at('00:00'), at('23:59'));
        expect(entries.length, '日历应返回占用条目（A/B 占用 3101）').toBeGreaterThan(0);
        for (const entry of entries) {
          expect(entry.label, '无权条目 label 不得含活动标题').not.toContain(ACTIVITIES.b.title);
          expect(Object.keys(entry), '日历条目不得含 ownerId').not.toContain('ownerId');
          expect(Object.keys(entry), '日历条目不得含报名名单').not.toContain('registeredUserIds');
        }
        const venueEntries = entries.filter((e) => e.resourceId === RESOURCES.venueA.id);
        expect(venueEntries.length, '应含 3101 的占用条目').toBeGreaterThan(0);
        expect(
          venueEntries.some((e) => e.label.includes(ACTIVITIES.b.title)),
          'B（SCHEDULED、学生未报名）标题不得出现',
        ).toBe(false);

        // UI 冒烟：日历页可见资源列，且不出现 B 标题/组织者
        const student = await openSession(browser, USERS.studentA);
        sessions.push(student);
        await student.page.goto('/resources/calendar');
        // 日期导航 testid 待 UI 提供，见 handoff；若存在日期字段则切到 DEMO_DATE
        const dateInput = student.page.getByLabel(/日期/);
        if ((await dateInput.count()) > 0) {
          await dateInput.first().fill(DEMO_DATE);
          await dateInput.first().press('Enter');
        }
        await expect(student.page.getByText(RESOURCES.venueA.name).first()).toBeVisible();
        await expect(student.page.getByText(ACTIVITIES.b.title)).toHaveCount(0);
        await expect(student.page.getByText('组织者B（演示）')).toHaveCount(0);
      } finally {
        await closeSessions(sessions);
        await apiClose(apis);
      }
    },
  );

  test(
    'TC-21-2 学生读取他人活动报名名单被拒 403，UI 不展示名单',
    { annotation: [{ type: 'D-AT', description: 'D-AT-21' }, { type: 'TC', description: 'TC-21-2' }] },
    async ({ browser }) => {
      const apis: ApiSession[] = [];
      const sessions: UiSession[] = [];
      try {
        const studentApi = await apiLogin(USERS.studentA);
        apis.push(studentApi);

        // API-23：仅所有者/同组织教师/资源管理员；活动 A 学生已报名仍无权看名单
        const errA = await readError(
          await getRegistrations(studentApi, ACTIVITIES.a.id),
          403,
          '学生读取活动 A 报名名单应 403',
        );
        expect(errA.error.code, '错误码应为 FORBIDDEN').toBe('FORBIDDEN');
        expect(errA.error.message, '403 应含中文可读文案').toBeTruthy();

        // 无权查看的活动 B 更不得泄露名单
        await readError(
          await getRegistrations(studentApi, ACTIVITIES.b.id),
          403,
          '学生读取无权活动 B 名单应 403',
        );

        // UI：学生活动 A 详情不渲染报名名单（名单区 testid 待 UI 提供，见 handoff）
        const student = await openSession(browser, USERS.studentA);
        sessions.push(student);
        await student.page.goto(`/activities/${ACTIVITIES.a.id}`);
        await expect(student.page.getByText(/报名名单|报名人员名单/)).toHaveCount(0);
        await expect(student.page.getByText('学生B（演示）')).toHaveCount(0);
      } finally {
        await closeSessions(sessions);
        await apiClose(apis);
      }
    },
  );

  test(
    'TC-21-2 学生只能查看自己的通知，读取他人通知被拒',
    { annotation: [{ type: 'D-AT', description: 'D-AT-21' }, { type: 'TC', description: 'TC-21-2' }] },
    async ({ browser }) => {
      const apis: ApiSession[] = [];
      const sessions: UiSession[] = [];
      try {
        const organizerApi = await apiLogin(USERS.organizerA);
        const staffApi = await apiLogin(USERS.staffA);
        const studentApi = await apiLogin(USERS.studentA);
        apis.push(organizerApi, staffApi, studentApi);

        // 真实业务产生通知：创建改期后需 staff_a 确认，outbox 投递其通知
        const activity = await getActivity(organizerApi, ACTIVITIES.a.id);
        const change = await createChange(organizerApi, ACTIVITIES.a.id, {
          baseVersionNo: activity.versionNo,
          targetPlan: PREFERRED_PLAN,
          expectedLockVersion: activity.lockVersion,
          reason: 'D-AT-21 通知隔离（测试数据）',
        });

        await expect
          .poll(async () => (await getNotifications(staffApi)).length, {
            message: 'staff_a 应在改期创建后收到通知',
            timeout: 15_000,
          })
          .toBeGreaterThan(0);
        const staffNoticeId = (await getNotifications(staffApi))[0].id;

        // 学生通知列表不得出现他人通知；读取他人通知被拒（API-34/35 本人限定）
        const studentNotices = await getNotifications(studentApi);
        expect(studentNotices.map((n) => n.id), '学生列表不得包含他人通知').not.toContain(staffNoticeId);
        const readOther = await markNotificationRead(studentApi, staffNoticeId);
        expect([403, 404], '学生读取他人通知应被拒').toContain(readOther.status());

        // UI 冒烟：学生通知页可访问且不报错
        const student = await openSession(browser, USERS.studentA);
        sessions.push(student);
        await student.page.goto('/notifications');
        await expect(student.page).toHaveURL(/\/notifications(?:[/?#]|$)/);
        await expect(student.page.getByRole('alert').filter({ hasText: /无权限|禁止|403/ })).toHaveCount(0);

        // 清理待处理改期，避免阻塞后续用例
        await withdrawChange(organizerApi, change.id);
      } finally {
        await closeSessions(sessions);
        await apiClose(apis);
      }
    },
  );

  test(
    'TC-21-3 staff_a 读取改期详情时名单字段被裁剪但计数真实',
    { annotation: [{ type: 'D-AT', description: 'D-AT-21' }, { type: 'TC', description: 'TC-21-3' }] },
    async () => {
      const apis: ApiSession[] = [];
      try {
        const organizerApi = await apiLogin(USERS.organizerA);
        const staffApi = await apiLogin(USERS.staffA);
        apis.push(organizerApi, staffApi);

        const activity = await getActivity(organizerApi, ACTIVITIES.a.id);
        const change = await createChange(organizerApi, ACTIVITIES.a.id, {
          baseVersionNo: activity.versionNo,
          targetPlan: PREFERRED_PLAN,
          expectedLockVersion: activity.lockVersion,
          reason: 'D-AT-21 字段裁剪（测试数据）',
        });

        // API-29：工作人员读取时两个名单数组为空，registeredCount 保持真实（02 §3）
        const staffView = await getChange(staffApi, change.id);
        expect(staffView.impact.registeredUserIds, '工作人员读取时 registeredUserIds 应为空数组').toEqual([]);
        expect(staffView.impact.notificationUserIds, '工作人员读取时 notificationUserIds 应为空数组').toEqual([]);
        expect(staffView.impact.registeredCount, 'registeredCount 保持真实（demo A 报名 1 人）').toBe(1);

        // 对照：所有者可见真实名单，证明裁剪按角色而非数据缺失
        const ownerView = await getChange(organizerApi, change.id);
        expect(ownerView.impact.registeredUserIds.length, '所有者应可见报名名单').toBeGreaterThan(0);

        await withdrawChange(organizerApi, change.id);
      } finally {
        await apiClose(apis);
      }
    },
  );
});
