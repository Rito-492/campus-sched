# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: booking.spec.ts >> D-AT-03 首次预约主线（新建→编辑→提交→批准→资源确认→发布→报名） >> TC-03-1 applicant_a 新建并提交，approver_a 同意、resource_admin 确认、发布后 student_a 报名成功
- Location: tests\booking.spec.ts:68:3

# Error details

```
Error: 批准人同意应 200

expect(received).toBe(expected) // Object.is equality

Expected: 200
Received: 400
```

# Test source

```ts
  27  |   apiLogin,
  28  |   closeSessions,
  29  |   createActivityViaUi,
  30  |   editActivity,
  31  |   getActivity,
  32  |   getCalendar,
  33  |   openSession,
  34  |   planFor,
  35  |   readRegistrations,
  36  |   submitActivity,
  37  |   type ActivityInputDto,
  38  |   type ApiSession,
  39  |   type UiSession,
  40  | } from './helpers.js';
  41  | 
  42  | /** 目标计划：避开演示活动 A/B 占用，选 18:00—19:00、场地 3102 + 器材 3202×1。 */
  43  | const NEW_PLAN = planFor(
  44  |   RESOURCES.venueB.id,
  45  |   [{ resourceId: RESOURCES.equipmentB.id, quantity: 1 }],
  46  |   '18:00',
  47  |   '19:00',
  48  |   0,
  49  |   0,
  50  | );
  51  | 
  52  | const NEW_ACTIVITY: ActivityInputDto = {
  53  |   title: 'D-AT-03 首次预约主线（测试数据）',
  54  |   description: '验证新建→编辑→提交→批准→资源确认→发布→报名。（测试数据）',
  55  |   expectedAttendees: 30,
  56  |   registrationCapacity: 20,
  57  |   staffUserIds: [],
  58  |   plan: NEW_PLAN,
  59  | };
  60  | 
  61  | /** 该活动相关的日历占用条目（API-09：资源确认后应出现完整数量的 ACTIVE 预约）。 */
  62  | async function calendarEntriesFor(api: ApiSession, activityId: string): Promise<{ resourceId: string; quantity: number }[]> {
  63  |   const entries = await getCalendar(api, `${DEMO_DATE}T00:00:00+08:00`, `${DEMO_DATE}T23:59:00+08:00`);
  64  |   return entries.filter((e) => e.activityId === activityId).map((e) => ({ resourceId: e.resourceId, quantity: e.quantity }));
  65  | }
  66  | 
  67  | test.describe('D-AT-03 首次预约主线（新建→编辑→提交→批准→资源确认→发布→报名）', () => {
  68  |   test(
  69  |     'TC-03-1 applicant_a 新建并提交，approver_a 同意、resource_admin 确认、发布后 student_a 报名成功',
  70  |     { annotation: [{ type: 'D-AT', description: 'D-AT-03' }, { type: 'TC', description: 'TC-03-1' }] },
  71  |     async ({ browser }) => {
  72  |       const sessions: UiSession[] = [];
  73  |       const apis: ApiSession[] = [];
  74  |       try {
  75  |         // 1) applicant_a 登录并经 UI 新建活动
  76  |         const applicant = await openSession(browser, USERS.applicantA);
  77  |         sessions.push(applicant);
  78  |         const activityId = await createActivityViaUi(applicant.page, {
  79  |           title: NEW_ACTIVITY.title,
  80  |           description: NEW_ACTIVITY.description,
  81  |           expectedAttendees: NEW_ACTIVITY.expectedAttendees,
  82  |           registrationCapacity: NEW_ACTIVITY.registrationCapacity,
  83  |           startAt: NEW_ACTIVITY.plan.startAt,
  84  |           endAt: NEW_ACTIVITY.plan.endAt,
  85  |           venueName: RESOURCES.venueB.name,
  86  |           equipmentNames: [RESOURCES.equipmentB.name],
  87  |         });
  88  | 
  89  |         const ownerApi = await apiLogin(USERS.applicantA);
  90  |         const approverApi = await apiLogin(USERS.approverA);
  91  |         const resourceApi = await apiLogin(USERS.resourceAdmin);
  92  |         const studentApi = await apiLogin(USERS.studentA);
  93  |         apis.push(ownerApi, approverApi, resourceApi, studentApi);
  94  | 
  95  |         // 2) 编辑（API-15，无公共 testid）：标题更新且锁版本推进
  96  |         let activity = await getActivity(ownerApi, activityId);
  97  |         expect(activity.status, '新建后应 DRAFT（02 §2）').toBe('DRAFT');
  98  |         const edited = await editActivity(ownerApi, activityId, {
  99  |           expectedLockVersion: activity.lockVersion,
  100 |           input: { ...NEW_ACTIVITY, title: `${NEW_ACTIVITY.title}（已编辑）` },
  101 |         });
  102 |         expect(edited.input.title, '编辑应真实保存新标题').toBe(`${NEW_ACTIVITY.title}（已编辑）`);
  103 | 
  104 |         // 3) 提交（API-16）→ PENDING_APPROVER
  105 |         activity = await submitActivity(ownerApi, activityId, edited.lockVersion);
  106 |         expect(activity.status, '提交后应 PENDING_APPROVER（02 §2）').toBe('PENDING_APPROVER');
  107 | 
  108 |         // 4) reload 持久化：提交状态来自真实后端而非内存
  109 |         //    活动状态 badge 用 statusLabels 中文（PENDING_APPROVER→「待批准」，format.ts），
  110 |         //    exact 匹配 badge 文案，避开「草稿 → 待批准 → …」常驻说明段落的假阳性
  111 |         await applicant.page.goto(`/activities/${activityId}`);
  112 |         await expect(applicant.page.getByText('待批准', { exact: true })).toBeVisible();
  113 |         await applicant.page.reload();
  114 |         await expect(applicant.page.getByText('待批准', { exact: true })).toBeVisible();
  115 | 
  116 |         // 5) 首次资源确认前：0 条 ACTIVE 预约（D-AT-03 关键断言）
  117 |         expect(await calendarEntriesFor(ownerApi, activityId), '资源确认前不得有预约占用').toEqual([]);
  118 | 
  119 |         // 6) approver_a 经 UI 同意（05 §4 approver-approve）→ APPROVED_UNRESERVED
  120 |         const approver = await openSession(browser, USERS.approverA);
  121 |         sessions.push(approver);
  122 |         await approver.page.goto(`/activities/${activityId}`);
  123 |         const approveResponse = approver.page.waitForResponse((res) =>
  124 |           res.url().includes(`/activities/${activityId}/approver-decision`),
  125 |         );
  126 |         await approver.page.getByTestId(TESTID.approverApprove).click();
> 127 |         expect((await approveResponse).status(), '批准人同意应 200').toBe(200);
      |                                                                ^ Error: 批准人同意应 200
  128 |         expect((await getActivity(ownerApi, activityId)).status, '批准后应 APPROVED_UNRESERVED').toBe('APPROVED_UNRESERVED');
  129 | 
  130 |         // 7) resource_admin 经 UI 确认（05 §4 resource-confirm）→ SCHEDULED，完整数量预约
  131 |         const resource = await openSession(browser, USERS.resourceAdmin);
  132 |         sessions.push(resource);
  133 |         await resource.page.goto(`/activities/${activityId}`);
  134 |         const confirmResponse = resource.page.waitForResponse((res) =>
  135 |           res.url().includes(`/activities/${activityId}/resource-confirm`),
  136 |         );
  137 |         await resource.page.getByTestId(TESTID.resourceConfirm).click();
  138 |         expect((await confirmResponse).status(), '资源确认应 200').toBe(200);
  139 |         expect((await getActivity(ownerApi, activityId)).status, '资源确认后应 SCHEDULED').toBe('SCHEDULED');
  140 | 
  141 |         const entries = await calendarEntriesFor(ownerApi, activityId);
  142 |         expect(
  143 |           entries.some((e) => e.resourceId === RESOURCES.venueB.id),
  144 |           '资源确认后应出现场地 3102 的 ACTIVE 预约',
  145 |         ).toBe(true);
  146 |         expect(
  147 |           entries.some((e) => e.resourceId === RESOURCES.equipmentB.id && e.quantity > 0),
  148 |           '资源确认后应出现器材 3202 的 ACTIVE 预约且数量完整',
  149 |         ).toBe(true);
  150 | 
  151 |         // 8) applicant_a 发布（05 §4 之外 UI 扩展 testid activity-publish）→ OPEN
  152 |         await applicant.page.goto(`/activities/${activityId}`);
  153 |         const publishButton = applicant.page.getByTestId(UI_TESTID.activityPublish);
  154 |         await publishButton.click();
  155 |         // OPEN 的 badge 文案为「开放报名」（format.ts statusLabels），exact 匹配避开常驻说明段落
  156 |         await expect(applicant.page.getByText('开放报名', { exact: true })).toBeVisible();
  157 |         expect((await getActivity(ownerApi, activityId)).status, '发布后应 OPEN（02 §2）').toBe('OPEN');
  158 | 
  159 |         // 9) student_a 登录并报名（报名入口为 UI 扩展 testid registration-submit）
  160 |         const student = await openSession(browser, USERS.studentA);
  161 |         sessions.push(student);
  162 |         await student.page.goto(`/activities/${activityId}`);
  163 |         await student.page.getByTestId(UI_TESTID.registrationSubmit).click();
  164 |         await expect(student.page.getByText(/报名成功|已报名|ACTIVE/).first()).toBeVisible();
  165 | 
  166 |         // 10) 断言名额与状态：报名 ACTIVE、计数 1 且不超过 registrationCapacity。
  167 |         //     API-23 完整报名名单仅所有者/授权角色可读（D-AT-21）：
  168 |         //     名单用 ownerApi 读取，学生只断言 myRegistrationStatus，不以学生身份取名单。
  169 |         const registrations = await readRegistrations(ownerApi, activityId);
  170 |         const mine = registrations.find((r) => r.userId === USER_IDS.studentA);
  171 |         expect(mine?.status, 'student_a 报名应为 ACTIVE').toBe('ACTIVE');
  172 |         expect((await getActivity(studentApi, activityId)).myRegistrationStatus, '本人报名状态应为 ACTIVE').toBe('ACTIVE');
  173 |         const afterJoin = await getActivity(ownerApi, activityId);
  174 |         expect(afterJoin.registrationCount, '报名人数应为 1').toBe(1);
  175 |         expect(afterJoin.registrationCount, '报名人数不得超过 registrationCapacity').toBeLessThanOrEqual(
  176 |           afterJoin.input.registrationCapacity,
  177 |         );
  178 | 
  179 |         // 11) reload 后报名状态仍在（真实持久化）
  180 |         await student.page.reload();
  181 |         await expect(student.page.getByText(/已报名|报名成功|ACTIVE/).first()).toBeVisible();
  182 |       } finally {
  183 |         await closeSessions(sessions);
  184 |         await apiClose(apis);
  185 |       }
  186 |     },
  187 |   );
  188 | });
  189 | 
```