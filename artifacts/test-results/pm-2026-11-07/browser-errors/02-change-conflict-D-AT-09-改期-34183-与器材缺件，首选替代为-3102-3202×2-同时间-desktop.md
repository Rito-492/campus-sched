# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: change-conflict.spec.ts >> D-AT-09 改期冲突与替代方案（UI） >> TC-09-2 预览同时展示场地占用与器材缺件，首选替代为 3102+3202×2 同时间
- Location: tests\change-conflict.spec.ts:129:3

# Error details

```
Error: expect(locator).toContainText(expected) failed

Locator: getByTestId('alternative-0')
Expected pattern: /首选/
Received string:  "选择并查看影响"
Timeout: 10000ms

Call log:
  - Expect "toContainText" getByTestId('alternative-0') with timeout 10000ms
  - waiting for getByTestId('alternative-0')
    23 × locator resolved to <button type="button" class="el-button" aria-disabled="false" data-testid="alternative-0">…</button>
       - unexpected value "选择并查看影响"

```

```yaml
- button "选择并查看影响"
```

# Test source

```ts
  53  | /** 首选替代方案：同时间 15:00—16:00，换场地 3102 + 换器材 3202×2（03 §1 固定数据）。 */
  54  | const PREFERRED_PLAN: PlanDto = planFor(
  55  |   RESOURCES.venueB.id,
  56  |   [{ resourceId: RESOURCES.equipmentB.id, quantity: 2 }],
  57  |   '15:00',
  58  |   '16:00',
  59  | );
  60  | 
  61  | /** 以活动 A 当前版本创建一次可行改期，返回 changeId（A 的 staff 仅 staff_a）。 */
  62  | async function createFeasibleChange(api: ApiSession): Promise<string> {
  63  |   const activity = await getActivity(api, ACTIVITIES.a.id);
  64  |   const change = await createChange(api, ACTIVITIES.a.id, {
  65  |     baseVersionNo: activity.versionNo,
  66  |     targetPlan: PREFERRED_PLAN,
  67  |     expectedLockVersion: activity.lockVersion,
  68  |     reason: 'D-AT 改期演练（测试数据）',
  69  |   });
  70  |   return change.id;
  71  | }
  72  | 
  73  | /** 另一申请人 applicant_b 创建并确认一个真实占用首选资源的活动（预览后抢占）。 */
  74  | async function preemptPreferredResources(): Promise<void> {
  75  |   const owner = await apiLogin(USERS.applicantB);
  76  |   const approver = await apiLogin(USERS.approverA);
  77  |   const resource = await apiLogin(USERS.resourceAdmin);
  78  |   try {
  79  |     const input: ActivityInputDto = {
  80  |       title: 'D-AT-12 抢占活动（测试数据）',
  81  |       description: '用于验证预览可行后被抢占最终资源确认。（测试数据）',
  82  |       expectedAttendees: 10,
  83  |       registrationCapacity: 1,
  84  |       staffUserIds: [],
  85  |       plan: planFor(
  86  |         RESOURCES.venueB.id,
  87  |         [{ resourceId: RESOURCES.equipmentB.id, quantity: 2 }],
  88  |         '15:00',
  89  |         '16:00',
  90  |         0,
  91  |         0,
  92  |       ),
  93  |     };
  94  |     let created = await createActivity(owner, input);
  95  |     created = await submitActivity(owner, created.id, created.lockVersion);
  96  |     created = await approverDecideActivity(approver, created.id, 'APPROVE', created.lockVersion);
  97  |     created = await resourceConfirmActivity(resource, created.id, created.lockVersion);
  98  |     expect(created.status, '抢占活动应 SCHEDULED 以真实占用候选资源').toBe('SCHEDULED');
  99  |   } finally {
  100 |     await apiClose([owner, approver, resource]);
  101 |   }
  102 | }
  103 | 
  104 | /**
  105 |  * 拒绝改期（05 §3：拒绝需填理由；docs/ui/real-integration-2026-10-31.md「QA接收路径」：
  106 |  * change-reject → 弹窗 reject-reason（理由必填）→ reject-confirm；改期不同步骤互斥，
  107 |  * change-reject 只对应当前可处理步骤）。
  108 |  */
  109 | async function rejectChangeViaUi(page: Page, reason: string): Promise<void> {
  110 |   await page.getByTestId('change-reject').click();
  111 |   const dialog = page.getByRole('dialog');
  112 |   await expect(dialog).toBeVisible();
  113 |   await expect(dialog, '拒绝弹窗应有标题「拒绝改期」').toContainText('拒绝改期');
  114 |   // 理由必填：未填理由时确认按钮保持禁用（05 §3 / 契约 reason 必填）
  115 |   await expect(page.getByTestId('reject-confirm'), '未填理由时不得允许确认拒绝').toBeDisabled();
  116 |   await page.getByTestId('reject-reason').fill(reason);
  117 |   await page.getByTestId('reject-confirm').click();
  118 | }
  119 | 
  120 | /** 原安排保留：活动仍是 version1，仍为原场地（3101）与原时间（10:00）。 */
  121 | async function expectOriginalPlanPreserved(page: Page): Promise<void> {
  122 |   await page.goto(`/activities/${ACTIVITIES.a.id}`);
  123 |   await expectCurrentVersion(page, 1);
  124 |   await expect(page.getByText(RESOURCES.venueA.name).first()).toBeVisible();
  125 |   await expect(page.getByText(/10:00/).first()).toBeVisible();
  126 | }
  127 | 
  128 | test.describe('D-AT-09 改期冲突与替代方案（UI）', () => {
  129 |   test(
  130 |     'TC-09-2 预览同时展示场地占用与器材缺件，首选替代为 3102+3202×2 同时间',
  131 |     { annotation: [{ type: 'D-AT', description: 'D-AT-09' }, { type: 'TC', description: 'TC-09-2' }] },
  132 |     async ({ browser }) => {
  133 |       const sessions: UiSession[] = [];
  134 |       try {
  135 |         const organizer = await openSession(browser, USERS.applicantA);
  136 |         sessions.push(organizer);
  137 | 
  138 |         await openChangeWorkbench(organizer.page, ACTIVITIES.a.id);
  139 |         // 目标 15:00—16:00，保持原场地 3101 + 器材 3201×2 → 触发两类冲突
  140 |         await fillChangeTargetTime(organizer.page, '15:00', '16:00');
  141 |         await organizer.page.getByTestId(TESTID.changePreview).click();
  142 | 
  143 |         const conflictList = organizer.page.getByTestId(TESTID.conflictList);
  144 |         await expect(conflictList).toBeVisible();
  145 |         // VENUE_OCCUPIED：B（社团交流会）占 3101 [14:30,16:30) 与目标重叠
  146 |         await expect(conflictList).toContainText(RESOURCES.venueA.name);
  147 |         // EQUIPMENT_SHORTAGE：3201 库存 2 − B 占 1 = 可用 1 < 需求 2
  148 |         await expect(conflictList).toContainText(RESOURCES.equipmentA.name);
  149 | 
  150 |         const alternative0 = organizer.page.getByTestId(TESTID.alternative0);
  151 |         await expect(alternative0).toBeVisible();
  152 |         // 05 §3：首个方案标「当前排序首选」，不能写全局最优
> 153 |         await expect(alternative0).toContainText(/首选/);
      |                                    ^ Error: expect(locator).toContainText(expected) failed
  154 |         // 首选替代：同时间 15:00，换场地 3102、换器材 3202×2
  155 |         await expect(alternative0).toContainText(RESOURCES.venueB.name);
  156 |         await expect(alternative0).toContainText(RESOURCES.equipmentB.name);
  157 |         await expect(alternative0).toContainText('15:00');
  158 | 
  159 |         // 预览只读：活动 A 仍为 version1（未回写）
  160 |         await organizer.page.goto(`/activities/${ACTIVITIES.a.id}`);
  161 |         await expectCurrentVersion(organizer.page, 1);
  162 |       } finally {
  163 |         await closeSessions(sessions);
  164 |       }
  165 |     },
  166 |   );
  167 | });
  168 | 
  169 | test.describe('D-AT-11 拒绝改期（原安排保护）', () => {
  170 |   test(
  171 |     'TC-11-1 staff_a 拒绝 → REJECTED，原安排与版本完全保留',
  172 |     { annotation: [{ type: 'D-AT', description: 'D-AT-11' }, { type: 'TC', description: 'TC-11-1' }] },
  173 |     async ({ browser }) => {
  174 |       const sessions: UiSession[] = [];
  175 |       const apis: ApiSession[] = [];
  176 |       try {
  177 |         const organizerApi = await apiLogin(USERS.applicantA);
  178 |         apis.push(organizerApi);
  179 |         const changeId = await createFeasibleChange(organizerApi);
  180 | 
  181 |         const staff = await openSession(browser, USERS.staffA);
  182 |         sessions.push(staff);
  183 |         await staff.page.goto(`/changes/${changeId}`);
  184 |         await expectChangeStatus(staff.page, /AWAITING_CONFIRMATIONS|等待工作人员确认/);
  185 |         await rejectChangeViaUi(staff.page, '时间冲突');
  186 |         await expectChangeStatus(staff.page, /REJECTED|已拒绝|驳回/);
  187 | 
  188 |         // 持久化终态与保留性（00 §2 不只是 HTTP）
  189 |         const change = await getChange(organizerApi, changeId);
  190 |         expect(change.status, '改期应 REJECTED').toBe('REJECTED');
  191 |         expect(change.confirmations.some((c) => c.decision === 'REJECT'), '应记录 staff 拒绝').toBe(true);
  192 |         const activity = await getActivity(organizerApi, ACTIVITIES.a.id);
  193 |         expect(activity.versionNo, '原版本号不变').toBe(1);
  194 |         expect(activity.effectiveVersionNo, '有效版本仍为 1').toBe(1);
  195 | 
  196 |         const organizer = await openSession(browser, USERS.applicantA);
  197 |         sessions.push(organizer);
  198 |         await expectOriginalPlanPreserved(organizer.page);
  199 |       } finally {
  200 |         await closeSessions(sessions);
  201 |         await apiClose(apis);
  202 |       }
  203 |     },
  204 |   );
  205 | 
  206 |   test(
  207 |     'TC-11-2 批准人驳回 → REJECTED，版本指针不变且意见落库',
  208 |     { annotation: [{ type: 'D-AT', description: 'D-AT-11' }, { type: 'TC', description: 'TC-11-2' }] },
  209 |     async ({ browser }) => {
  210 |       const sessions: UiSession[] = [];
  211 |       const apis: ApiSession[] = [];
  212 |       try {
  213 |         const organizerApi = await apiLogin(USERS.applicantA);
  214 |         apis.push(organizerApi);
  215 |         const changeId = await createFeasibleChange(organizerApi);
  216 | 
  217 |         const staffApi = await apiLogin(USERS.staffA);
  218 |         apis.push(staffApi);
  219 |         await confirmChange(staffApi, changeId, 'APPROVE');
  220 | 
  221 |         const approver = await openSession(browser, USERS.approverA);
  222 |         sessions.push(approver);
  223 |         await approver.page.goto(`/changes/${changeId}`);
  224 |         await expectChangeStatus(approver.page, /PENDING_APPROVER|等待批准人复审/);
  225 |         await rejectChangeViaUi(approver.page, '不同意');
  226 |         await expectChangeStatus(approver.page, /REJECTED|已拒绝|驳回/);
  227 | 
  228 |         const change = await getChange(organizerApi, changeId);
  229 |         expect(change.status, '改期应 REJECTED').toBe('REJECTED');
  230 |         expect(change.approverDecision?.decision, 'approver_decision_json 应记录 REJECT').toBe('REJECT');
  231 |         const activity = await getActivity(organizerApi, ACTIVITIES.a.id);
  232 |         expect(activity.versionNo, '原版本指针不变').toBe(1);
  233 |         expect(activity.effectiveVersionNo, '有效版本不变').toBe(1);
  234 |       } finally {
  235 |         await closeSessions(sessions);
  236 |         await apiClose(apis);
  237 |       }
  238 |     },
  239 |   );
  240 | });
  241 | 
  242 | test.describe('D-AT-12 预览后被抢占（最终确认冲突）', () => {
  243 |   test(
  244 |     'TC-12-1 候选资源被占 → 409 RESOURCE_CONFLICT、改期 CONFLICTED、原安排保留',
  245 |     { annotation: [{ type: 'D-AT', description: 'D-AT-12' }, { type: 'TC', description: 'TC-12-1' }] },
  246 |     async ({ browser }) => {
  247 |       const sessions: UiSession[] = [];
  248 |       const apis: ApiSession[] = [];
  249 |       try {
  250 |         const organizerApi = await apiLogin(USERS.applicantA);
  251 |         apis.push(organizerApi);
  252 |         const before = await getActivity(organizerApi, ACTIVITIES.a.id);
  253 | 
```