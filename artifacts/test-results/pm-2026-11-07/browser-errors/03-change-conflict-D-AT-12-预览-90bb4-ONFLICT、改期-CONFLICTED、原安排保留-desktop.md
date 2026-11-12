# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: change-conflict.spec.ts >> D-AT-12 预览后被抢占（最终确认冲突） >> TC-12-1 候选资源被占 → 409 RESOURCE_CONFLICT、改期 CONFLICTED、原安排保留
- Location: tests\change-conflict.spec.ts:243:3

# Error details

```
Error: 批准人审批活动应 200（HTTP 400）

expect(received).toBe(expected) // Object.is equality

Expected: 200
Received: 400
```

# Test source

```ts
  284 |  * 详情页路由 /activities/{id} 即服务端生成的活动编号。
  285 |  * 草稿保存不会自动提交审批；如需经 UI 提交，传 `submit: true` 在详情页点 activity-submit（05 §4）。
  286 |  */
  287 | export async function createActivityViaUi(
  288 |   page: Page,
  289 |   form: NewActivityForm,
  290 |   options: { submit?: boolean } = {},
  291 | ): Promise<string> {
  292 |   await page.goto('/activities/new');
  293 |   await fillNewActivityForm(page, form);
  294 |   await page.getByTestId(UI_TESTID.activitySave).click();
  295 |   await expect(page).toHaveURL(/\/activities\/[0-9a-f-]{36}(?:[/?#]|$)/i);
  296 |   const activityId = lastPathSegment(page.url());
  297 |   if (options.submit) {
  298 |     // 提交审批入口在详情页 activity-submit，保存草稿后不会自动提交
  299 |     const submitted = page.waitForResponse((res) => res.url().includes(`/activities/${activityId}/submit`));
  300 |     await page.getByTestId(TESTID.activitySubmit).click();
  301 |     expect((await submitted).status(), '提交审批应 200').toBe(200);
  302 |   }
  303 |   return activityId;
  304 | }
  305 | 
  306 | // ---------------------------------------------------------------- 页面语义断言
  307 | 
  308 | /** 断言改期状态（`change-status`）。页面中文文案由 UI 决定，传入可同时匹配枚举名或中文的表达式。 */
  309 | export async function expectChangeStatus(page: Page, expected: RegExp | string): Promise<void> {
  310 |   await expect(page.getByTestId(TESTID.changeStatus)).toContainText(expected);
  311 | }
  312 | 
  313 | /** 断言活动当前版本（`current-version`）：兼容 `2`、`v2`、`版本2`、`version 2` 等展示。 */
  314 | export async function expectCurrentVersion(page: Page, version: number): Promise<void> {
  315 |   await expect(page.getByTestId(TESTID.currentVersion)).toContainText(new RegExp(`\\bv?${version}\\b`, 'i'));
  316 | }
  317 | 
  318 | /**
  319 |  * 活动详情 → 发起改期 → 断言落到改期工作台。
  320 |  * 「发起改期」入口为 UI 扩展 testid change-entry（真实页面为 RouterLink）。
  321 |  */
  322 | export async function openChangeWorkbench(page: Page, activityId: string): Promise<void> {
  323 |   await page.goto(activityPath(activityId));
  324 |   await page.getByTestId(UI_TESTID.changeEntry).click();
  325 |   await expect(page).toHaveURL(new RegExp(`/activities/${activityId}/change(?:[/?#]|$)`));
  326 | }
  327 | 
  328 | /**
  329 |  * 在改期工作台填写目标时间（页面预填原场地/器材，需保持原组合以复现 TC-09 冲突）。
  330 |  * PlanEditor(prefix=change) 的目标时间输入为 change-start/change-end（datetime-local），
  331 |  * 传入 `HH:mm` 按 DEMO_DATE 当天展开（目标为同日改时段，03 §1）。
  332 |  */
  333 | export async function fillChangeTargetTime(page: Page, start: string, end: string): Promise<void> {
  334 |   await page.getByTestId(UI_TESTID.changeStart).fill(localDateTime(at(start)));
  335 |   await page.getByTestId(UI_TESTID.changeEnd).fill(localDateTime(at(end)));
  336 | }
  337 | 
  338 | // ---------------------------------------------------------------- 真实 REST 辅助（非 Mock）
  339 | 
  340 | export interface ApiEnvelope<T> {
  341 |   data: T;
  342 |   requestId?: string;
  343 |   truncated?: boolean;
  344 | }
  345 | export interface ApiErrorBody {
  346 |   error: { code: string; message: string; details?: unknown };
  347 |   requestId?: string;
  348 | }
  349 | 
  350 | export interface ApiSession {
  351 |   token: string;
  352 |   client: APIRequestContext;
  353 | }
  354 | 
  355 | export async function apiLogin(username: string, password: string = demoPassword()): Promise<ApiSession> {
  356 |   const client = await request.newContext({ baseURL: BASE_URL });
  357 |   const res = await client.post(`${API_PREFIX}/auth/login`, {
  358 |     data: { username, password },
  359 |     headers: { 'Content-Type': 'application/json' },
  360 |   });
  361 |   expect(res.status(), `API 登录 ${username} 应为 200`).toBe(200);
  362 |   const body = (await res.json()) as ApiEnvelope<{ token?: string }>;
  363 |   const token = body.data?.token;
  364 |   expect(token, `API 登录 ${username} 响应应含 token`).toBeTruthy();
  365 |   return { token: token as string, client };
  366 | }
  367 | 
  368 | export async function apiClose(sessions: ApiSession[]): Promise<void> {
  369 |   await Promise.all(sessions.map(async (s) => s.client.dispose()));
  370 | }
  371 | 
  372 | export function authHeaders(token: string, idempotencyKey?: string): Record<string, string> {
  373 |   const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
  374 |   if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
  375 |   return headers;
  376 | }
  377 | 
  378 | export interface HttpResponseLike {
  379 |   status(): number;
  380 |   json(): Promise<unknown>;
  381 | }
  382 | 
  383 | export async function readData<T>(res: HttpResponseLike, expectedStatus: number, message: string): Promise<T> {
> 384 |   expect(res.status(), `${message}（HTTP ${res.status()}）`).toBe(expectedStatus);
      |                                                            ^ Error: 批准人审批活动应 200（HTTP 400）
  385 |   const body = (await res.json()) as ApiEnvelope<T>;
  386 |   return body.data;
  387 | }
  388 | 
  389 | export async function readError(res: HttpResponseLike, expectedStatus: number, message: string): Promise<ApiErrorBody> {
  390 |   expect(res.status(), `${message}（HTTP ${res.status()}）`).toBe(expectedStatus);
  391 |   return (await res.json()) as ApiErrorBody;
  392 | }
  393 | 
  394 | // ---------------------------------------------------------------- 领域 REST 类型（02 契约 §2 子集）
  395 | 
  396 | export interface EquipmentNeedDto {
  397 |   resourceId: string;
  398 |   quantity: number;
  399 | }
  400 | export interface PlanDto {
  401 |   startAt: string;
  402 |   endAt: string;
  403 |   setupMinutes: number;
  404 |   teardownMinutes: number;
  405 |   venueId: string;
  406 |   equipment: EquipmentNeedDto[];
  407 | }
  408 | export interface ActivityInputDto {
  409 |   title: string;
  410 |   description: string;
  411 |   expectedAttendees: number;
  412 |   registrationCapacity: number;
  413 |   staffUserIds: string[];
  414 |   plan: PlanDto;
  415 | }
  416 | export interface ActivityViewDto {
  417 |   id: string;
  418 |   ownerId: string;
  419 |   status: string;
  420 |   versionNo: number;
  421 |   effectiveVersionNo: number | null;
  422 |   lockVersion: number;
  423 |   registrationCount: number;
  424 |   resourceRisk: string;
  425 |   activeChangeId: string | null;
  426 |   myRegistrationStatus?: 'ACTIVE' | 'WITHDRAWN' | 'CANCELLED' | null;
  427 |   input: ActivityInputDto;
  428 | }
  429 | export interface ConflictDto {
  430 |   code: string;
  431 |   resourceId: string;
  432 |   resourceName: string;
  433 |   startsAt: string;
  434 |   endsAt: string;
  435 |   required: number | null;
  436 |   available: number | null;
  437 |   message: string;
  438 | }
  439 | export interface CheckResultDto {
  440 |   feasible: boolean;
  441 |   conflicts: ConflictDto[];
  442 | }
  443 | export interface AlternativeDto {
  444 |   plan: PlanDto;
  445 |   timeShiftMinutes: number;
  446 |   venueChanged: boolean;
  447 |   replacedEquipmentLines: number;
  448 |   explanation: string;
  449 | }
  450 | export interface AlternativesViewDto {
  451 |   items: AlternativeDto[];
  452 |   evaluatedCount: number;
  453 |   truncated: boolean;
  454 |   offsetsMinutes: number[];
  455 |   maxEvaluations: number;
  456 | }
  457 | export interface ImpactDto {
  458 |   registeredUserIds: string[];
  459 |   notificationUserIds: string[];
  460 |   registeredCount: number;
  461 |   originalResourceRisk: string;
  462 | }
  463 | export interface ChangePreviewDto {
  464 |   check: CheckResultDto;
  465 |   alternatives: AlternativesViewDto;
  466 |   impact: ImpactDto;
  467 | }
  468 | export interface ChangeViewDto {
  469 |   id: string;
  470 |   activityId: string;
  471 |   baseVersionNo: number;
  472 |   status: string;
  473 |   appliedVersionNo: number | null;
  474 |   impact: ImpactDto;
  475 |   lastConflicts: ConflictDto[];
  476 |   confirmations: { userId: string; decision: string | null }[];
  477 |   approverDecision: { decision: string; comment: string } | null;
  478 | }
  479 | 
  480 | export interface FaultInputDto {
  481 |   startAt: string;
  482 |   endAt: string;
  483 |   unavailableQuantity: number;
  484 |   reason: string;
```