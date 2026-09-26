/**
 * QA-03 E2E 公共辅助（qa/tests/helpers.ts）。
 *
 * 约定来源：
 * - `agents/common/05-界面规范.md` §4：公共 `data-testid` 固定清单（逐字使用，README 见 TESTID）；
 * - `agents/common/02-数据与接口契约.md`：字段名、枚举、状态名、统一响应包装与 Idempotency-Key；
 * - `docs/testing/test-data-plan.md`：演示账号、`DEMO_DATE=2030-10-20`、资源/活动尾号。
 *
 * 定位规则（QA-03）：优先 `page.getByTestId(...)`；05 未定义 testid 的元素使用
 * 语义化可访问角色+名称（getByRole/getByLabel）并标注「testid 待 UI 提供，见 handoff」，
 * 不按按钮在页面中的位置定位。业务状态一律 `expect` 轮询，禁止固定长 sleep。
 *
 * 真实后端：本文件的 API 辅助只调用真实 REST（API-01…API-38），不是 Mock/替身。
 */
import {
  expect,
  request,
  type APIRequestContext,
  type APIResponse,
  type Browser,
  type BrowserContext,
  type Page,
} from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

// ---------------------------------------------------------------- 环境与固定数据

export const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8089';
export const API_PREFIX = '/api/v1';

/** 自动测试固定演示日期（test-data-plan §3）；禁止依赖实际当天。 */
export const DEMO_DATE = process.env.DEMO_DATE ?? '2030-10-20';

/** 演示口令经环境变量提供，仓库不写入口令（test-data-plan §3、03 §1）。 */
export const DEMO_PASSWORD = process.env.DEMO_PASSWORD ?? 'demo-password';

/** 02 契约 §2 / test-data-plan §2：ID 前缀与尾号。 */
const UUID_PREFIX = '00000000-0000-0000-0000-';
const uid = (tail: string): string => `${UUID_PREFIX}${tail.padStart(12, '0')}`;

export const USERS = {
  organizerA: 'organizer_a',
  organizerB: 'organizer_b',
  teacherA: 'teacher_a',
  resourceAdmin: 'resource_admin',
  studentA: 'student_a',
  studentB: 'student_b',
  staffA: 'staff_a',
  sysAdmin: 'sys_admin',
  teacherOther: 'teacher_other',
} as const;
export type DemoUsername = (typeof USERS)[keyof typeof USERS];

/** 02 契约 §2 枚举语义（断言页面文案时以注释写明所依状态名）。 */
export const CHANGE_STATUS = {
  awaiting: 'AWAITING_CONFIRMATIONS',
  pendingTeacher: 'PENDING_TEACHER',
  pendingResource: 'PENDING_RESOURCE',
  applied: 'APPLIED',
  rejected: 'REJECTED',
  conflicted: 'CONFLICTED',
} as const;

export const RISK = { normal: 'NORMAL', atRisk: 'AT_RISK' } as const;

export const RESOURCES = {
  venueA: { id: uid('3101'), name: '学术报告厅A' },
  venueB: { id: uid('3102'), name: '多功能厅B' },
  equipmentA: { id: uid('3201'), name: '投影设备组A' },
  equipmentB: { id: uid('3202'), name: '投影设备组B' },
} as const;

export const ACTIVITIES = {
  a: { id: uid('4001'), title: '校园技术分享会', owner: USERS.organizerA, versionNo: 1 },
  b: { id: uid('4002'), title: '社团交流会', owner: USERS.organizerB, versionNo: 1 },
} as const;

/** 05 §4 公共 data-testid 固定清单：UI 必须逐字提供。 */
export const TESTID = {
  loginSubmit: 'login-submit',
  activitySubmit: 'activity-submit',
  resourceConfirm: 'resource-confirm',
  changePreview: 'change-preview',
  alternative0: 'alternative-0',
  changeCreate: 'change-create',
  staffApprove: 'staff-approve',
  teacherApprove: 'teacher-approve',
  changeResourceApprove: 'change-resource-approve',
  changeStatus: 'change-status',
  currentVersion: 'current-version',
  conflictList: 'conflict-list',
} as const;
export type TestIdName = keyof typeof TESTID;

// ---------------------------------------------------------------- 时间/路径

export const activityPath = (activityId: string): string => `/activities/${activityId}`;
export const changeWorkbenchPath = (activityId: string): string => `/activities/${activityId}/change`;
export const changeDetailPath = (changeId: string): string => `/changes/${changeId}`;

/** 上海自然日 2030-10-20 的 HH:mm（契约示例时区 +08:00）。 */
export const at = (hhmm: string, date: string = DEMO_DATE): string => `${date}T${hhmm}:00+08:00`;

export function lastPathSegment(url: string): string {
  const seg = new URL(url).pathname.split('/').filter(Boolean).pop();
  expect(seg, `URL 应含资源 id：${url}`).toBeTruthy();
  return seg as string;
}

export function newIdempotencyKey(): string {
  return randomUUID();
}

// ---------------------------------------------------------------- 浏览器 context 登录

export interface UiSession {
  context: BrowserContext;
  page: Page;
}

/**
 * 每个角色一个独立浏览器 context（QA-03 通则），经 /login 真实登录。
 * 05 §4 仅定义 login-submit；账号/密码输入框与错误提示 testid 待 UI 提供，见 handoff，
 * 使用可访问名称定位。
 */
export async function login(page: Page, username: string, password: string = DEMO_PASSWORD): Promise<void> {
  await page.goto('/login');
  await page.getByLabel(/账号|用户名/).fill(username);
  await page.getByLabel(/密码/).fill(password);
  await page.getByTestId(TESTID.loginSubmit).click();
  // 登录成功进入 /activities（05 §2 路由表）
  await expect(page).toHaveURL(/\/activities(?:[/?#]|$)/);
}

/** 打开一个独立 context 并完成 UI 登录；调用方负责在结束时 closeContexts。 */
export async function openSession(browser: Browser, username: string): Promise<UiSession> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await login(page, username);
  return { context, page };
}

export async function closeSessions(sessions: UiSession[]): Promise<void> {
  await Promise.all(sessions.map(async (s) => s.context.close()));
}

// ---------------------------------------------------------------- 页面语义断言

/** 断言改期状态（`change-status`）。页面中文文案由 UI 决定，传入可同时匹配枚举名或中文的表达式。 */
export async function expectChangeStatus(page: Page, expected: RegExp | string): Promise<void> {
  await expect(page.getByTestId(TESTID.changeStatus)).toContainText(expected);
}

/** 断言活动当前版本（`current-version`）：兼容 `2`、`v2`、`版本2`、`version 2` 等展示。 */
export async function expectCurrentVersion(page: Page, version: number): Promise<void> {
  await expect(page.getByTestId(TESTID.currentVersion)).toContainText(new RegExp(`\\bv?${version}\\b`, 'i'));
}

/**
 * 活动详情 → 发起改期 → 断言落到改期工作台。
 * 「发起改期」入口 testid 待 UI 提供，见 handoff；用可访问名称定位。
 */
export async function openChangeWorkbench(page: Page, activityId: string): Promise<void> {
  await page.goto(activityPath(activityId));
  const entry = page
    .getByRole('link', { name: /发起改期/ })
    .or(page.getByRole('button', { name: /发起改期/ }));
  await entry.click();
  await expect(page).toHaveURL(new RegExp(`/activities/${activityId}/change(?:[/?#]|$)`));
}

/**
 * 在改期工作台填写目标时间（页面预填原场地/器材，需保持原组合以复现 TC-09 冲突）。
 * 目标时间字段 testid 待 UI 提供，见 handoff；用可访问名称定位。
 */
export async function fillChangeTargetTime(page: Page, start: string, end: string): Promise<void> {
  await page.getByLabel(/开始时间/).fill(start);
  await page.getByLabel(/结束时间/).fill(end);
}

// ---------------------------------------------------------------- 真实 REST 辅助（非 Mock）

export interface ApiEnvelope<T> {
  data: T;
  requestId?: string;
  truncated?: boolean;
}
export interface ApiErrorBody {
  error: { code: string; message: string; details?: unknown };
  requestId?: string;
}

export interface ApiSession {
  token: string;
  client: APIRequestContext;
}

export async function apiLogin(username: string, password: string = DEMO_PASSWORD): Promise<ApiSession> {
  const client = await request.newContext({ baseURL: BASE_URL });
  const res = await client.post(`${API_PREFIX}/auth/login`, {
    data: { username, password },
    headers: { 'Content-Type': 'application/json' },
  });
  expect(res.status(), `API 登录 ${username} 应为 200`).toBe(200);
  const body = (await res.json()) as ApiEnvelope<{ token?: string }>;
  const token = body.data?.token;
  expect(token, `API 登录 ${username} 响应应含 token`).toBeTruthy();
  return { token: token as string, client };
}

export async function apiClose(sessions: ApiSession[]): Promise<void> {
  await Promise.all(sessions.map(async (s) => s.client.dispose()));
}

export function authHeaders(token: string, idempotencyKey?: string): Record<string, string> {
  const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
  return headers;
}

export interface HttpResponseLike {
  status(): number;
  json(): Promise<unknown>;
}

export async function readData<T>(res: HttpResponseLike, expectedStatus: number, message: string): Promise<T> {
  expect(res.status(), `${message}（HTTP ${res.status()}）`).toBe(expectedStatus);
  const body = (await res.json()) as ApiEnvelope<T>;
  return body.data;
}

export async function readError(res: HttpResponseLike, expectedStatus: number, message: string): Promise<ApiErrorBody> {
  expect(res.status(), `${message}（HTTP ${res.status()}）`).toBe(expectedStatus);
  return (await res.json()) as ApiErrorBody;
}

// ---------------------------------------------------------------- 领域 REST 类型（02 契约 §2 子集）

export interface EquipmentNeedDto {
  resourceId: string;
  quantity: number;
}
export interface PlanDto {
  startAt: string;
  endAt: string;
  setupMinutes: number;
  teardownMinutes: number;
  venueId: string;
  equipment: EquipmentNeedDto[];
}
export interface ActivityInputDto {
  title: string;
  description: string;
  expectedAttendees: number;
  registrationCapacity: number;
  staffUserIds: string[];
  plan: PlanDto;
}
export interface ActivityViewDto {
  id: string;
  ownerId: string;
  status: string;
  versionNo: number;
  effectiveVersionNo: number | null;
  lockVersion: number;
  registrationCount: number;
  resourceRisk: string;
  activeChangeId: string | null;
  input: ActivityInputDto;
}
export interface ConflictDto {
  code: string;
  resourceId: string;
  resourceName: string;
  startsAt: string;
  endsAt: string;
  required: number | null;
  available: number | null;
  message: string;
}
export interface CheckResultDto {
  feasible: boolean;
  conflicts: ConflictDto[];
}
export interface AlternativeDto {
  plan: PlanDto;
  timeShiftMinutes: number;
  venueChanged: boolean;
  replacedEquipmentLines: number;
  explanation: string;
}
export interface AlternativesViewDto {
  items: AlternativeDto[];
  evaluatedCount: number;
  truncated: boolean;
  offsetsMinutes: number[];
  maxEvaluations: number;
}
export interface ImpactDto {
  registeredUserIds: string[];
  notificationUserIds: string[];
  registeredCount: number;
  originalResourceRisk: string;
}
export interface ChangePreviewDto {
  check: CheckResultDto;
  alternatives: AlternativesViewDto;
  impact: ImpactDto;
}
export interface ChangeViewDto {
  id: string;
  activityId: string;
  baseVersionNo: number;
  status: string;
  appliedVersionNo: number | null;
  impact: ImpactDto;
  lastConflicts: ConflictDto[];
  confirmations: { userId: string; decision: string | null }[];
  teacherDecision: { decision: string; comment: string } | null;
}

export interface FaultInputDto {
  startAt: string;
  endAt: string;
  unavailableQuantity: number;
  reason: string;
}
export interface AffectedActivityDto {
  activityId: string;
  title: string;
  versionNo: number;
  resourceRisk: string;
}
export interface FaultViewDto extends FaultInputDto {
  id: string;
  resourceId: string;
  affectedActivities: AffectedActivityDto[];
}

export interface ProposalDto {
  baseVersionNo: number;
  targetPlan: PlanDto;
}
export interface ChangeCreateDto extends ProposalDto {
  expectedLockVersion: number;
  reason: string;
}
export interface ChangeDecisionDto {
  decision: 'APPROVE' | 'REJECT';
  comment: string;
}

// ---------------------------------------------------------------- 领域 REST 调用

export async function getActivity(api: ApiSession, activityId: string): Promise<ActivityViewDto> {
  const res = await api.client.get(`${API_PREFIX}/activities/${activityId}`, {
    headers: authHeaders(api.token),
  });
  return readData<ActivityViewDto>(res, 200, `GET /activities/${activityId} 应 200`);
}

export async function createActivity(api: ApiSession, input: ActivityInputDto): Promise<ActivityViewDto> {
  const res = await api.client.post(`${API_PREFIX}/activities`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: input,
  });
  return readData<ActivityViewDto>(res, 201, '创建活动应 201');
}

export async function submitActivity(api: ApiSession, activityId: string, expectedLockVersion: number): Promise<ActivityViewDto> {
  const res = await api.client.post(`${API_PREFIX}/activities/${activityId}/submit`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: { expectedLockVersion },
  });
  return readData<ActivityViewDto>(res, 200, '提交活动应 200');
}

export async function teacherDecideActivity(
  api: ApiSession,
  activityId: string,
  decision: 'APPROVE' | 'REJECT',
  expectedLockVersion: number,
  comment = '',
): Promise<ActivityViewDto> {
  const res = await api.client.post(`${API_PREFIX}/activities/${activityId}/teacher-decision`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: { decision, comment, expectedLockVersion },
  });
  return readData<ActivityViewDto>(res, 200, '教师审批活动应 200');
}

export async function resourceConfirmActivity(api: ApiSession, activityId: string, expectedLockVersion: number): Promise<ActivityViewDto> {
  const res = await api.client.post(`${API_PREFIX}/activities/${activityId}/resource-confirm`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: { expectedLockVersion },
  });
  return readData<ActivityViewDto>(res, 200, '资源确认活动应 200');
}

export async function publishActivity(api: ApiSession, activityId: string, expectedLockVersion: number): Promise<ActivityViewDto> {
  const res = await api.client.post(`${API_PREFIX}/activities/${activityId}/publish`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: { expectedLockVersion },
  });
  return readData<ActivityViewDto>(res, 200, '发布活动应 200');
}

export async function changePreview(api: ApiSession, activityId: string, proposal: ProposalDto): Promise<ChangePreviewDto> {
  const res = await api.client.post(`${API_PREFIX}/activities/${activityId}/change-preview`, {
    headers: authHeaders(api.token),
    data: proposal,
  });
  return readData<ChangePreviewDto>(res, 200, 'change-preview 应 200');
}

export async function createChange(api: ApiSession, activityId: string, body: ChangeCreateDto): Promise<ChangeViewDto> {
  const res = await api.client.post(`${API_PREFIX}/activities/${activityId}/changes`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: body,
  });
  return readData<ChangeViewDto>(res, 201, '创建改期应 201');
}

export async function confirmChange(api: ApiSession, changeId: string, decision: 'APPROVE' | 'REJECT', comment = ''): Promise<ChangeViewDto> {
  const res = await api.client.post(`${API_PREFIX}/changes/${changeId}/confirmation`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: { decision, comment },
  });
  return readData<ChangeViewDto>(res, 200, '工作人员确认改期应 200');
}

export async function changeTeacherDecision(api: ApiSession, changeId: string, decision: 'APPROVE' | 'REJECT', comment = ''): Promise<ChangeViewDto> {
  const res = await api.client.post(`${API_PREFIX}/changes/${changeId}/teacher-decision`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: { decision, comment },
  });
  return readData<ChangeViewDto>(res, 200, '教师审批改期应 200');
}

export async function changeResourceDecision(api: ApiSession, changeId: string, decision: 'APPROVE' | 'REJECT', comment = ''): Promise<ChangeViewDto> {
  const res = await api.client.post(`${API_PREFIX}/changes/${changeId}/resource-decision`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: { decision, comment },
  });
  return readData<ChangeViewDto>(res, 200, '资源确认改期应 200');
}

export async function getChange(api: ApiSession, changeId: string): Promise<ChangeViewDto> {
  const res = await api.client.get(`${API_PREFIX}/changes/${changeId}`, {
    headers: authHeaders(api.token),
  });
  return readData<ChangeViewDto>(res, 200, `GET /changes/${changeId} 应 200`);
}

export async function createResourceFault(api: ApiSession, resourceId: string, fault: FaultInputDto): Promise<FaultViewDto> {
  const res = await api.client.post(`${API_PREFIX}/resources/${resourceId}/faults`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: fault,
  });
  return readData<FaultViewDto>(res, 201, '登记资源故障应 201');
}

export async function getResourceFaults(api: ApiSession, resourceId: string, from: string, to: string): Promise<FaultViewDto[]> {
  const res = await api.client.get(`${API_PREFIX}/resources/${resourceId}/faults`, {
    headers: authHeaders(api.token),
    params: { from, to },
  });
  return readData<FaultViewDto[]>(res, 200, '查询资源故障应 200');
}

export interface CalendarEntryDto {
  resourceId: string;
  startsAt: string;
  endsAt: string;
  quantity: number;
  activityId: string | null;
  label: string;
  risk: string;
  segment: string;
}

export async function getCalendar(api: ApiSession, from: string, to: string): Promise<CalendarEntryDto[]> {
  const res = await api.client.get(`${API_PREFIX}/resources/calendar`, {
    headers: authHeaders(api.token),
    params: { from, to },
  });
  return readData<CalendarEntryDto[]>(res, 200, '查询资源日历应 200');
}

export async function getRegistrations(api: ApiSession, activityId: string): Promise<APIResponse> {
  return api.client.get(`${API_PREFIX}/activities/${activityId}/registrations`, {
    headers: authHeaders(api.token),
  });
}

export async function withdrawChange(api: ApiSession, changeId: string, reason = 'E2E 清理'): Promise<ChangeViewDto> {
  const res = await api.client.post(`${API_PREFIX}/changes/${changeId}/withdraw`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: { reason },
  });
  return readData<ChangeViewDto>(res, 200, '撤销改期应 200');
}

export async function markNotificationRead(api: ApiSession, notificationId: string): Promise<APIResponse> {
  return api.client.post(`${API_PREFIX}/notifications/${notificationId}/read`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: {},
  });
}

export async function getNotifications(api: ApiSession): Promise<{ id: string; activityId: string | null; versionNo: number | null; title: string }[]> {
  const res = await api.client.get(`${API_PREFIX}/notifications`, { headers: authHeaders(api.token) });
  return readData(res, 200, 'GET /notifications 应 200');
}

/** 目标计划：保持活动时长与布撤场（01 §4），换场/换器材由替代方案给出。 */
export function planFor(venueId: string, equipment: EquipmentNeedDto[], start: string, end: string, setup = 30, teardown = 30): PlanDto {
  return { startAt: at(start), endAt: at(end), setupMinutes: setup, teardownMinutes: teardown, venueId, equipment };
}

// ---------------------------------------------------------------- 视觉核对辅助（ui-layout.spec）

export async function expectNoHorizontalOverflow(page: Page): Promise<void> {
  const metrics = await page.evaluate(() => {
    const el = document.scrollingElement ?? document.documentElement;
    return { scrollWidth: el.scrollWidth, clientWidth: el.clientWidth };
  });
  expect(
    metrics.scrollWidth,
    `横向溢出：scrollWidth=${metrics.scrollWidth} > clientWidth=${metrics.clientWidth}`,
  ).toBeLessThanOrEqual(metrics.clientWidth + 1);
}

/** 「无错误横幅」：可见 alert/错误文案不应出现在核心页面（05 §5）。 */
export async function expectNoErrorBanner(page: Page): Promise<void> {
  const banner = page.getByRole('alert').filter({ hasText: /错误|失败|异常|请求失败|未知错误|ERROR/i });
  await expect(banner).toHaveCount(0);
}

/** 「文字可读」：正文计算字号不小于 12px（05 §1）。 */
export async function expectReadableBodyText(page: Page): Promise<void> {
  const fontSize = await page.evaluate(() => parseFloat(getComputedStyle(document.body).fontSize));
  expect(Number.isFinite(fontSize), '无法读取 body 字号').toBe(true);
  expect(fontSize, `正文字号 ${fontSize}px 小于 12px`).toBeGreaterThanOrEqual(12);
}

const screenshotsDir = fileURLToPath(new URL('../../artifacts/screenshots/', import.meta.url));

export async function saveScreenshot(page: Page, fileName: string): Promise<string> {
  mkdirSync(screenshotsDir, { recursive: true });
  const filePath = join(screenshotsDir, fileName);
  await page.screenshot({ path: filePath, fullPage: true });
  return filePath;
}
