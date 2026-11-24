/** N-AT-02：两种身份各验证共享、教师专用、学生专用和封存资源。 */
import { expect, test } from '@playwright/test';
import {
  ELIGIBILITY_RESOURCES as resources,
  ERROR_CODES,
  TESTID,
  UI_TESTID,
  USERS,
  apiLogin,
  authHeaders,
  createActivity,
  createActivityViaUi,
  getActivity,
  newIdempotencyKey,
  openSession,
  planFor,
  readData,
  readError,
  type ActivityInputDto,
  type ActivityViewDto,
} from './helpers.js';

const combinations = [
  { name: '共享', venue: resources.venueShared, equipment: resources.equipmentShared, teacher: true, student: true },
  { name: '教师专用', venue: resources.venueTeacherOnly, equipment: resources.equipmentTeacherOnly, teacher: true, student: false },
  { name: '学生专用', venue: resources.venueStudentOnly, equipment: resources.equipmentStudentOnly, teacher: false, student: true },
  { name: '封存', venue: resources.venueBlocked, equipment: resources.equipmentBlocked, teacher: false, student: false },
] as const;

const identities = [
  { name: '教师', code: 'TEACHER', user: USERS.applicantA, key: 'teacher' },
  { name: '学生', code: 'STUDENT', user: USERS.applicantB, key: 'student' },
] as const;

test.describe('N-AT-02 资源申请资格', () => {
  for (const identity of identities) {
    for (const group of combinations) {
      test(`${identity.name} × ${group.name}：界面和 HTTP 提交`, {
        annotation: [{ type: 'N-AT', description: 'N-AT-02' }],
      }, async ({ browser }) => {
        const allowed = group[identity.key];
        const plan = planFor(group.venue.id, [{ resourceId: group.equipment.id, quantity: 1 }], '20:00', '21:00', 0, 0);
        const input: ActivityInputDto = {
          title: `资格验证 ${identity.name} ${group.name}`,
          description: '验证场地与器材的申请资格。',
          expectedAttendees: 10,
          registrationCapacity: 10,
          staffUserIds: [],
          plan,
        };
        const ui = await openSession(browser, identity.user);
        try {
          const api = await apiLogin(identity.user);
          try {
            const draft = await createActivity(api, { ...input, title: `${input.title} HTTP` });
            const response = await api.client.post(`/api/v1/activities/${draft.id}/submit`, {
              headers: authHeaders(api.token, newIdempotencyKey()),
              data: { expectedLockVersion: draft.lockVersion },
            });

            if (allowed) {
              const submitted = await readData<ActivityViewDto>(response, 200, '合资格请求应进入审批');
              expect(submitted.status).toBe('PENDING_APPROVER');
              const id = await createActivityViaUi(ui.page, {
                title: input.title,
                description: input.description,
                expectedAttendees: input.expectedAttendees,
                registrationCapacity: input.registrationCapacity,
                startAt: plan.startAt,
                endAt: plan.endAt,
                venueName: group.venue.name,
                equipmentNames: [group.equipment.name],
              }, { submit: true });
              expect((await getActivity(api, id)).status).toBe('PENDING_APPROVER');
            } else {
              const error = await readError(response, 403, '不合资格请求应被拒绝');
              expect(error.error.code).toBe(ERROR_CODES.resourceIneligible);
              expect(error.error.details).toMatchObject({
                identity: identity.code,
                resourceIds: expect.arrayContaining([group.venue.id, group.equipment.id]),
              });
              // 模拟用户打开资格已变化的旧草稿，检查真实页面上的提交反馈。
              await ui.page.goto(`/activities/${draft.id}`);
              const rejected = ui.page.waitForResponse((r) => r.url().endsWith(`/activities/${draft.id}/submit`) && r.request().method() === 'POST');
              await ui.page.getByTestId(TESTID.activitySubmit).click();
              expect((await rejected).status()).toBe(403);
              const notice = ui.page.getByTestId('eligibility-error');
              await expect(notice).toContainText(group.venue.name);
              await expect(notice).toContainText(group.equipment.name);
              expect(await getActivity(api, draft.id)).toEqual(draft);

              await ui.page.goto('/activities/new');
              await ui.page.getByTestId(UI_TESTID.activityTitle).fill(input.title);
              await ui.page.getByLabel(/预计(参加)?人数/).fill('10');
              await ui.page.getByLabel('报名容量').fill('10');
              await ui.page.getByRole('button', { name: '下一步' }).click();
              await ui.page.getByTestId(UI_TESTID.activityVenue).click();
              const venue = ui.page.getByRole('option', { name: group.venue.name });
              await expect(venue).toHaveAttribute('aria-disabled', 'true');
              await expect(venue).toContainText('当前身份不可申请');
              await ui.page.keyboard.press('Escape');
              await ui.page.getByRole('button', { name: '添加设备' }).click();
              await ui.page.getByRole('combobox', { name: '设备' })
                .locator('xpath=ancestor::*[contains(concat(" ", normalize-space(@class), " "), " el-select ")][1]').click();
              const equipment = ui.page.getByRole('option', { name: group.equipment.name });
              await expect(equipment).toHaveAttribute('aria-disabled', 'true');
              await expect(equipment).toContainText('当前身份不可申请');
            }

            const calendar = await api.client.get('/api/v1/resources/calendar', {
              headers: authHeaders(api.token),
              params: { from: plan.startAt, to: plan.endAt },
            });
            const entries = await readData<{ activityId?: string }[]>(calendar, 200, '读取预约日历');
            expect(entries.filter((entry) => entry.activityId === draft.id)).toHaveLength(0);
          } finally {
            await api.client.dispose();
          }
        } finally {
          await ui.context.close();
        }
      });
    }
  }
});
