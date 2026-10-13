/**
 * N-AT-02 资格限制（E2E，QA-04 交付）。
 *
 * 用例依据：`agents/common/03-验收与演示数据.md` §2 N-AT-02
 *   「两类身份×四种资源资格，分别从 UI 和直接 HTTP 提交；场地或任一设备不符」
 *   → 8 组资格组合正确；后端拒绝受限申请且无预约；UI 说明具体受限资源。
 * 契约依据：02 §2 ResourceEligibility/ActivityInput、§3 API-12、§1 RESOURCE_INELIGIBLE、
 *   §7「资格以活动所有者最新 identity 判断」；05 §6「资源选择、方案卡和提交失败显示具体资格限制」。
 *
 * 本 spec 覆盖教师专用组（尾号 3112 场地 / 3212 设备）：
 * - applicant_b（STUDENT）：UI 提交被拒并展示具体受限资源；直接 HTTP 提交 403 RESOURCE_INELIGIBLE；
 * - applicant_a（TEACHER）：同目标可正常走到下一步（对照，证明按 identity 而非角色判断）。
 *
 * 角色隔离：每个用例独立 context / API 会话。5 §4 未定义资格错误区 testid，使用可访问语义定位并在
 *   注释标注「testid 待 UI 提供，见 handoff」。本 spec 会真实调用后端（乐观期望独立 E2E 环境）。
 */
import { expect, test } from '@playwright/test';
import {
  ELIGIBILITY_RESOURCES,
  ERROR_CODES,
  TESTID,
  USERS,
  apiClose,
  apiLogin,
  closeSessions,
  createActivityResponse,
  createActivityViaUi,
  fillNewActivityForm,
  listActivities,
  openSession,
  planFor,
  readError,
  type ActivityInputDto,
  type ApiSession,
  type UiSession,
} from './helpers.js';

/** 仅教师可用的目标：场地 3112 + 设备 3212×1，时间 20:00—21:00（避开演示活动）。 */
const TEACHER_ONLY_PLAN = planFor(
  ELIGIBILITY_RESOURCES.venueTeacherOnly.id,
  [{ resourceId: ELIGIBILITY_RESOURCES.equipmentTeacherOnly.id, quantity: 1 }],
  '20:00',
  '21:00',
  0,
  0,
);

const TEACHER_ONLY_INPUT: ActivityInputDto = {
  title: 'N-AT-02 资格受限尝试（测试数据）',
  description: '学生身份申请仅教师资源，验证后端 403 与 UI 说明具体受限资源。（测试数据）',
  expectedAttendees: 10,
  registrationCapacity: 10,
  staffUserIds: [],
  plan: TEACHER_ONLY_PLAN,
};

function includesAny(haystack: string, values: string[]): boolean {
  return values.some((v) => haystack.includes(v));
}

test.describe('N-AT-02 资格限制（仅教师资源）', () => {
  test(
    'N-AT-02 applicant_b（STUDENT）UI 提交仅教师资源被拒，错误区展示具体受限资源',
    { annotation: [{ type: 'N-AT', description: 'N-AT-02' }] },
    async ({ browser }) => {
      const sessions: UiSession[] = [];
      try {
        const student = await openSession(browser, USERS.applicantB);
        sessions.push(student);
        await student.page.goto('/activities/new');
        await fillNewActivityForm(student.page, {
          title: TEACHER_ONLY_INPUT.title,
          description: TEACHER_ONLY_INPUT.description,
          expectedAttendees: TEACHER_ONLY_INPUT.expectedAttendees,
          registrationCapacity: TEACHER_ONLY_INPUT.registrationCapacity,
          startAt: TEACHER_ONLY_PLAN.startAt,
          endAt: TEACHER_ONLY_PLAN.endAt,
          venueName: ELIGIBILITY_RESOURCES.venueTeacherOnly.name,
          equipmentNames: [ELIGIBILITY_RESOURCES.equipmentTeacherOnly.name],
        });
        await student.page.getByTestId(TESTID.activitySubmit).click();

        // 05 §6：UI 必须说明具体受限资源，不能只写「无权限」。
        // 资格错误区 testid 待 UI 提供，见 handoff；用 alert/错误容器语义 + 文案定位。
        const errorArea = student.page
          .getByRole('alert')
          .or(student.page.locator('[data-testid*="error"], .error-message'))
          .filter({ hasText: /资格|受限|不可申请|教师专用|教师投影|RESOURCE_INELIGIBLE/ })
          .first();
        await expect(errorArea).toBeVisible();
        await expect(errorArea).toContainText(ELIGIBILITY_RESOURCES.venueTeacherOnly.name);
        await expect(errorArea).toContainText(ELIGIBILITY_RESOURCES.equipmentTeacherOnly.name);
      } finally {
        await closeSessions(sessions);
      }
    },
  );

  test(
    'N-AT-02 applicant_a（TEACHER）同目标可走到下一步（对照，按 identity 判断）',
    { annotation: [{ type: 'N-AT', description: 'N-AT-02' }] },
    async ({ browser }) => {
      const sessions: UiSession[] = [];
      const apis: ApiSession[] = [];
      try {
        const teacher = await openSession(browser, USERS.applicantA);
        sessions.push(teacher);
        const activityId = await createActivityViaUi(teacher.page, {
          title: 'N-AT-02 教师同目标对照（测试数据）',
          description: 'TEACHER 身份申请仅教师资源，应可正常进入下一步。（测试数据）',
          expectedAttendees: 10,
          registrationCapacity: 10,
          startAt: TEACHER_ONLY_PLAN.startAt,
          endAt: TEACHER_ONLY_PLAN.endAt,
          venueName: ELIGIBILITY_RESOURCES.venueTeacherOnly.name,
          equipmentNames: [ELIGIBILITY_RESOURCES.equipmentTeacherOnly.name],
        });

        // 对照：教师身份可真实写入（非被资格拦截）
        const ownerApi = await apiLogin(USERS.applicantA);
        apis.push(ownerApi);
        const mine = await listActivities(ownerApi, 'mine');
        expect(
          mine.some((a) => a.id === activityId),
          'TEACHER 申请仅教师资源应成功创建（对照 STUDENT 被拒）',
        ).toBe(true);
      } finally {
        await closeSessions(sessions);
        await apiClose(apis);
      }
    },
  );

  test(
    'N-AT-02 applicant_b 直接 HTTP 提交同目标 → 403 RESOURCE_INELIGIBLE 且无写入',
    { annotation: [{ type: 'N-AT', description: 'N-AT-02' }] },
    async () => {
      const apis: ApiSession[] = [];
      try {
        const studentApi = await apiLogin(USERS.applicantB);
        apis.push(studentApi);

        const err = await readError(
          await createActivityResponse(studentApi, TEACHER_ONLY_INPUT),
          403,
          'STUDENT 提交仅教师资源应 403',
        );
        expect(err.error.code, '错误码应为 RESOURCE_INELIGIBLE（02 §1）').toBe(ERROR_CODES.resourceIneligible);
        expect(err.error.message, '403 应含中文可读原因').toBeTruthy();

        // 02 §7：details 含受限资源及身份，不写预约
        const detailsText = JSON.stringify(err.error.details ?? {});
        expect(
          includesAny(detailsText, [ELIGIBILITY_RESOURCES.venueTeacherOnly.id, ELIGIBILITY_RESOURCES.venueTeacherOnly.name]),
          'details 应标明受限场地（3112/教师专用研讨室D）',
        ).toBe(true);
        expect(
          includesAny(detailsText, [
            ELIGIBILITY_RESOURCES.equipmentTeacherOnly.id,
            ELIGIBILITY_RESOURCES.equipmentTeacherOnly.name,
          ]),
          'details 应标明受限设备（3212/教师投影组D）',
        ).toBe(true);
        expect(detailsText, 'details 应含申请者身份 STUDENT').toContain('STUDENT');

        // 拒绝后不得留下任何活动（无预约、无写入）
        const mine = await listActivities(studentApi, 'mine');
        expect(
          mine.some((a) => a.input.title === TEACHER_ONLY_INPUT.title),
          '被拒申请不得创建活动',
        ).toBe(false);
      } finally {
        await apiClose(apis);
      }
    },
  );
});
