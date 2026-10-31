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

/** 演示口令由运行环境安全注入；仅实际登录测试读取，不在模块加载时强制要求。 */
export function demoPassword(): string {
  const value = process.env.DEMO_PASSWORD;
  if (!value || value.trim().length === 0) {
    throw new Error('必须通过环境变量安全提供 DEMO_PASSWORD；请勿在源码或日志中写入口令。');
  }
  return value;
}

/** 02 契约 §2 / test-data-plan §2：ID 前缀与尾号。 */
const UUID_PREFIX = '00000000-0000-0000-0000-';
const uid = (tail: string): string => `${UUID_PREFIX}${tail.padStart(12, '0')}`;

export const USERS = {
  applicantA: 'applicant_a',
  applicantB: 'applicant_b',
  approverA: 'approver_a',
  approverOther: 'approver_other',
  resourceAdmin: 'resource_admin',
  accountAdmin: 'account_admin',
  studentA: 'student_a',
  studentB: 'student_b',
  staffA: 'staff_a',
} as const;
export type DemoUsername = (typeof USERS)[keyof typeof USERS];

/** demo.json（03 §1）用户 UUID：与用户名一一对应，用于按 id 断言。 */
export const USER_IDS = {
  applicantA: uid('2001'),
  applicantB: uid('2002'),
  approverA: uid('2003'),
  resourceAdmin: uid('2004'),
  studentA: uid('2005'),
  staffA: uid('2006'),
  accountAdmin: uid('2007'),
  approverOther: uid('2008'),
  studentB: uid('2009'),
} as const;

/** 02 契约 §2 枚举语义（断言页面文案时以注释写明所依状态名）。 */
export const CHANGE_STATUS = {
  awaiting: 'AWAITING_CONFIRMATIONS',
  pendingApprover: 'PENDING_APPROVER',
  pendingResource: 'PENDING_RESOURCE',
  applied: 'APPLIED',
  rejected: 'REJECTED',
  conflicted: 'CONFLICTED',
  withdrawn: 'WITHDRAWN',
} as const;

/** 02 契约 §1 常用错误码（断言用，逐字保持一致）。 */
export const ERROR_CODES = {
  unauthenticated: 'UNAUTHENTICATED',
  forbidden: 'FORBIDDEN',
  resourceIneligible: 'RESOURCE_INELIGIBLE',
  validationError: 'VALIDATION_ERROR',
  staleVersion: 'STALE_VERSION',
} as const;

export const RISK = { normal: 'NORMAL', atRisk: 'AT_RISK' } as const;

export const RESOURCES = {
  venueA: { id: uid('3101'), name: '学术报告厅A' },
  venueB: { id: uid('3102'), name: '多功能厅B' },
  equipmentA: { id: uid('3201'), name: '投影设备组A' },
  equipmentB: { id: uid('3202'), name: '投影设备组B' },
} as const;

/**
 * 附加独立资格资源四组（03 §1）：共享 / 仅教师 / 仅学生 / 均禁止，
 * 每组含场地（尾号 3111-3114）与设备（3211-3214），按同一顺序对应。
 */
export const ELIGIBILITY_RESOURCES = {
  venueShared: { id: uid('3111'), name: '共享研讨室C' },
  venueTeacherOnly: { id: uid('3112'), name: '教师专用研讨室D' },
  venueStudentOnly: { id: uid('3113'), name: '学生活动室E' },
  venueBlocked: { id: uid('3114'), name: '封存测试厅F' },
  equipmentShared: { id: uid('3211'), name: '投影设备组C' },
  equipmentTeacherOnly: { id: uid('3212'), name: '教师投影组D' },
  equipmentStudentOnly: { id: uid('3213'), name: '学生投影组E' },
  equipmentBlocked: { id: uid('3214'), name: '封存投影组F' },
} as const;

export const ACTIVITIES = {
  a: { id: uid('4001'), title: '校园技术分享会', owner: USERS.applicantA, versionNo: 1 },
  b: { id: uid('4002'), title: '社团交流会', owner: USERS.applicantB, versionNo: 1 },
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
  approverApprove: 'approver-approve',
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
export async function login(page: Page, username: string, password: string = demoPassword()): Promise<void> {
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

// ---------------------------------------------------------------- 新建活动表单（05 §2 /activities/new）

/**
 * 分步表单的可见字段（05 §4 仅定义 activity-submit）。
 * 其余字段 testid 待 UI 提供，见 handoff；此处用标签/可访问名称定位。
 */
export interface NewActivityForm {
  title: string;
  description: string;
  expectedAttendees: number;
  registrationCapacity: number;
  startAt: string;
  endAt: string;
  venueName: string;
  equipmentNames: string[];
}

/** datetime-local 输入需要 `YYYY-MM-DDTHH:mm`（去掉契约 ISO8601 的时区与秒）。 */
const localDateTime = (instant: string): string => instant.slice(0, 16);

export async function fillNewActivityForm(page: Page, form: NewActivityForm): Promise<void> {
  await page.getByLabel(/标题|活动名称/).fill(form.title);
  await page.getByLabel(/简介|描述|说明/).fill(form.description);
  await page.getByLabel(/预计(参加)?人数/).fill(String(form.expectedAttendees));
  await page.getByLabel(/报名(人数)?(名额|上限)|名额/).fill(String(form.registrationCapacity));
  await page.getByLabel(/开始时间/).fill(localDateTime(form.startAt));
  await page.getByLabel(/结束时间/).fill(localDateTime(form.endAt));
  // 场地/器材选择器 testid 待 UI 提供，见 handoff；Element Plus 选择器以可访问名称选择
  await page.getByLabel(/场地/).first().selectOption({ label: form.venueName });
  for (const name of form.equipmentNames) {
    await page.getByLabel(/器材|设备/).first().selectOption({ label: name });
  }
}

export async function createActivityViaUi(page: Page, form: NewActivityForm): Promise<string> {
  await page.goto('/activities/new');
  await fillNewActivityForm(page, form);
  await page.getByTestId(TESTID.activitySubmit).click();
  await expect(page).toHaveURL(/\/activities\/[0-9a-f-]{36}(?:[/?#]|$)/i);
  return lastPathSegment(page.url());
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

export async function apiLogin(username: string, password: string = demoPassword()): Promise<ApiSession> {
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
  myRegistrationStatus?: 'ACTIVE' | 'WITHDRAWN' | 'CANCELLED' | null;
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
  approverDecision: { decision: string; comment: string } | null;
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

/** API-12 原始响应（用于断言 403 RESOURCE_INELIGIBLE 等失败分支，不吞错误码）。 */
export async function createActivityResponse(api: ApiSession, input: ActivityInputDto): Promise<APIResponse> {
  return api.client.post(`${API_PREFIX}/activities`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: input,
  });
}

/** API-13 活动列表（scope=mine/public/review）。 */
export async function listActivities(api: ApiSession, scope: 'mine' | 'public' | 'review'): Promise<ActivityViewDto[]> {
  const res = await api.client.get(`${API_PREFIX}/activities`, {
    headers: authHeaders(api.token),
    params: { scope },
  });
  return readData<ActivityViewDto[]>(res, 200, `查询活动 scope=${scope} 应 200`);
}

export async function editActivity(
  api: ApiSession,
  activityId: string,
  edit: { expectedLockVersion: number; input: ActivityInputDto },
): Promise<ActivityViewDto> {
  const res = await api.client.patch(`${API_PREFIX}/activities/${activityId}`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: edit,
  });
  return readData<ActivityViewDto>(res, 200, '编辑活动应 200');
}

export async function submitActivity(api: ApiSession, activityId: string, expectedLockVersion: number): Promise<ActivityViewDto> {
  const res = await api.client.post(`${API_PREFIX}/activities/${activityId}/submit`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: { expectedLockVersion },
  });
  return readData<ActivityViewDto>(res, 200, '提交活动应 200');
}

export async function approverDecideActivity(
  api: ApiSession,
  activityId: string,
  decision: 'APPROVE' | 'REJECT',
  expectedLockVersion: number,
  comment = '',
): Promise<ActivityViewDto> {
  const res = await api.client.post(`${API_PREFIX}/activities/${activityId}/approver-decision`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: { decision, comment, expectedLockVersion },
  });
  return readData<ActivityViewDto>(res, 200, '批准人审批活动应 200');
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

export async function changeApproverDecision(api: ApiSession, changeId: string, decision: 'APPROVE' | 'REJECT', comment = ''): Promise<ChangeViewDto> {
  const res = await api.client.post(`${API_PREFIX}/changes/${changeId}/approver-decision`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: { decision, comment },
  });
  return readData<ChangeViewDto>(res, 200, '批准人审批改期应 200');
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

export interface RegistrationViewDto {
  activityId: string;
  userId: string;
  status: 'ACTIVE' | 'WITHDRAWN' | 'CANCELLED';
  versionNo: number;
}

/** API-22 报名/退出：本人 STUDENT 身份且 APPLICANT（幂等键 + 期望版本）。 */
export async function registerActivity(
  api: ApiSession,
  activityId: string,
  action: 'JOIN' | 'WITHDRAW',
  expectedVersionNo: number,
): Promise<RegistrationViewDto> {
  const res = await api.client.put(`${API_PREFIX}/activities/${activityId}/registration`, {
    headers: authHeaders(api.token, newIdempotencyKey()),
    data: { action, expectedVersionNo },
  });
  return readData<RegistrationViewDto>(res, 200, `报名 ${action} 应 200`);
}

export async function readRegistrations(api: ApiSession, activityId: string): Promise<RegistrationViewDto[]> {
  const res = await getRegistrations(api, activityId);
  return readData<RegistrationViewDto[]>(res, 200, '读取报名名单应 200');
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
