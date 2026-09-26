/**
 * 契约一致性检查（QA-03，任务书 QA-03 第 6 条）。
 *
 * 读取 contracts/openapi.yaml，核对：
 *   1. 38 个接口的 方法+路径（02 契约 §3 清单）
 *   2. 关键 DTO 必需字段（02 契约 §2）
 *   3. 枚举取值（Role/ActivityStatus/ChangeStatus/Decision/Risk/ResourceKind/ConflictCode）
 *   4. 响应包装（成功 data+requestId、错误 error{code,message,details}+requestId，02 契约 §1）
 *   5. 认证（登录外全部 Bearer；GET /health 免登录）
 *   6. Idempotency-Key（持久化 POST/PATCH/PUT 必需，登录/退出/只读预览除外）
 *
 * 发现差异：只输出差异报告并退出 1，交实现者或 PM（02 契约 §7），
 * **不私自改契约使其迎合错误实现**。
 *
 * 用法：node scripts/check-contract.ts [openapi路径]（默认 contracts/openapi.yaml）
 */
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '..', '..');
const specPath = process.argv[2] ?? join(repoRoot, 'contracts', 'openapi.yaml');

function fail(message: string): never {
  console.error(`[check-contract] 失败：${message}`);
  process.exit(1);
}

if (!existsSync(specPath)) {
  fail(`找不到 ${specPath}（openapi.yaml 由 PM/AN 提供；缺失时契约检查必须失败，04-协作规程 §5）`);
}

// ---------------------------------------------------------------- 期望（全部来自 02 契约）

/** 02 契约 §3：方法 + 路径（省略 /api/v1 前缀，核对时归一化） */
const EXPECTED_APIS: [string, string, string][] = [
  ['API-01', 'post', '/auth/login'],
  ['API-02', 'get', '/auth/me'],
  ['API-03', 'post', '/auth/logout'],
  ['API-04', 'get', '/users'],
  ['API-05', 'post', '/users'],
  ['API-06', 'patch', '/users/{id}/enabled'],
  ['API-07', 'get', '/resources'],
  ['API-08', 'post', '/resources'],
  ['API-09', 'get', '/resources/calendar'],
  ['API-10', 'post', '/resources/{id}/faults'],
  ['API-11', 'get', '/resources/{id}/faults'],
  ['API-12', 'post', '/activities'],
  ['API-13', 'get', '/activities'],
  ['API-14', 'get', '/activities/{id}'],
  ['API-15', 'patch', '/activities/{id}'],
  ['API-16', 'post', '/activities/{id}/submit'],
  ['API-17', 'post', '/activities/{id}/withdraw'],
  ['API-18', 'post', '/activities/{id}/teacher-decision'],
  ['API-19', 'post', '/activities/{id}/resource-confirm'],
  ['API-20', 'post', '/activities/{id}/publish'],
  ['API-21', 'post', '/activities/{id}/cancel'],
  ['API-22', 'put', '/activities/{id}/registration'],
  ['API-23', 'get', '/activities/{id}/registrations'],
  ['API-24', 'post', '/activities/{id}/schedule-preview'],
  ['API-25', 'post', '/activities/{id}/alternatives'],
  ['API-26', 'post', '/activities/{id}/change-preview'],
  ['API-27', 'post', '/activities/{id}/changes'],
  ['API-28', 'get', '/activities/{id}/changes'],
  ['API-29', 'get', '/changes/{id}'],
  ['API-30', 'post', '/changes/{id}/confirmation'],
  ['API-31', 'post', '/changes/{id}/teacher-decision'],
  ['API-32', 'post', '/changes/{id}/resource-decision'],
  ['API-33', 'post', '/changes/{id}/withdraw'],
  ['API-34', 'get', '/notifications'],
  ['API-35', 'post', '/notifications/{id}/read'],
  ['API-36', 'get', '/activities/{id}/audit'],
  ['API-37', 'get', '/health'],
  ['API-38', 'get', '/organizations'],
];

/** 免登录接口（02 契约 §1/§3：登录外全部 Bearer；/health 免登录） */
const NO_AUTH = new Set(['post /auth/login', 'get /health']);

/**
 * 不要求 Idempotency-Key 的写操作（02 契约 §1）：
 * 登录/退出；只读 preview 与 alternatives（API-24/25/26）。
 */
const NO_IDEMPOTENCY = new Set([
  'post /auth/login',
  'post /auth/logout',
  'post /activities/{id}/schedule-preview',
  'post /activities/{id}/alternatives',
  'post /activities/{id}/change-preview',
]);

/** 02 契约 §2 枚举取值 */
const EXPECTED_ENUMS: Record<string, string[]> = {
  Role: ['STUDENT', 'ORGANIZER', 'TEACHER', 'RESOURCE_ADMIN', 'SYS_ADMIN'],
  ActivityStatus: ['DRAFT', 'PENDING_TEACHER', 'APPROVED_UNRESERVED', 'SCHEDULED', 'OPEN', 'CANCELLED'],
  ChangeStatus: [
    'AWAITING_CONFIRMATIONS', 'PENDING_TEACHER', 'PENDING_RESOURCE', 'APPLIED',
    'REJECTED', 'CONFLICTED', 'WITHDRAWN', 'CANCELLED_BY_ACTIVITY',
  ],
  Decision: ['APPROVE', 'REJECT'],
  Risk: ['NORMAL', 'AT_RISK'],
  ResourceKind: ['VENUE', 'EQUIPMENT'],
  ConflictCode: ['VENUE_CAPACITY', 'OUTSIDE_HOURS', 'VENUE_OCCUPIED', 'EQUIPMENT_SHORTAGE', 'RESOURCE_FAULT'],
};

/** 02 契约 §2 关键 DTO 必需字段（以 TypeScript 定义为准） */
const EXPECTED_DTO_FIELDS: Record<string, string[]> = {
  ActivityInput: ['title', 'description', 'expectedAttendees', 'registrationCapacity', 'staffUserIds', 'plan'],
  Plan: ['startAt', 'endAt', 'setupMinutes', 'teardownMinutes', 'venueId', 'equipment'],
  EquipmentNeed: ['resourceId', 'quantity'],
  ActivityView: [
    'id', 'organizationId', 'ownerId', 'status', 'versionNo', 'effectiveVersionNo', 'lockVersion',
    'input', 'registrationCount', 'resourceRisk', 'activeChangeId', 'myRegistrationStatus', 'createdAt',
  ],
  ResourceCreate: ['kind', 'name', 'category', 'capacity', 'quantity', 'openFrom', 'openTo'],
  Conflict: ['code', 'resourceId', 'resourceName', 'startsAt', 'endsAt', 'required', 'available', 'message'],
  CheckResult: ['feasible', 'conflicts'],
  AlternativesView: ['items', 'evaluatedCount', 'truncated', 'offsetsMinutes', 'maxEvaluations'],
  ChangeCreate: ['baseVersionNo', 'targetPlan', 'expectedLockVersion', 'reason'],
  ChangeView: [
    'id', 'activityId', 'baseVersionNo', 'targetPlan', 'reason', 'status', 'impact', 'confirmations',
    'teacherDecision', 'resourceDecision', 'lastConflicts', 'appliedVersionNo', 'createdBy', 'createdAt', 'updatedAt',
  ],
  RegistrationCommand: ['action', 'expectedVersionNo'],
  NotificationView: ['id', 'eventId', 'activityId', 'versionNo', 'title', 'body', 'readAt', 'createdAt'],
};

// ---------------------------------------------------------------- 解析 openapi

interface OperationObject {
  parameters?: { name: string; in: string; required?: boolean; schema?: { type?: string; format?: string } }[];
  requestBody?: { required?: boolean; content?: Record<string, { schema?: unknown }> };
  responses?: Record<string, unknown>;
  security?: unknown[];
}
interface Spec {
  paths?: Record<string, Record<string, OperationObject | undefined>>;
  components?: {
    schemas?: Record<string, { type?: string; required?: string[]; properties?: Record<string, unknown>; enum?: unknown[] }>;
    securitySchemes?: Record<string, unknown>;
  };
  security?: unknown[];
}

let spec: Spec;
try {
  spec = parse(readFileSync(specPath, 'utf8')) as Spec;
} catch (e) {
  fail(`openapi.yaml 解析失败：${(e as Error).message}`);
}

const paths = spec.paths ?? {};
const schemas = spec.components?.schemas ?? {};
const problems: string[] = [];

/** 归一化路径：补 /api/v1 前缀、{xxx} 占位符统一 */
const norm = (p: string) => {
  let s = p.startsWith('/api/v1') ? p.slice('/api/v1'.length) : p;
  s = s.replace(/\{[^}]+\}/g, '{id}');
  return s === '' ? '/' : s;
};

// 1. 接口清单：38 个都存在，且无多余接口
const actualOps = new Map<string, string>(); // "method path" -> spec路径
for (const [p, item] of Object.entries(paths)) {
  for (const method of ['get', 'post', 'put', 'patch', 'delete'] as const) {
    if (item[method]) actualOps.set(`${method} ${norm(p)}`, p);
  }
}
for (const [id, method, path] of EXPECTED_APIS) {
  const key = `${method} ${norm(path)}`;
  if (!actualOps.has(key)) problems.push(`${id} 缺失：${method.toUpperCase()} ${path}`);
}
const expectedKeys = new Set(EXPECTED_APIS.map(([, m, p]) => `${m} ${norm(p)}`));
for (const key of actualOps.keys()) {
  if (!expectedKeys.has(key)) problems.push(`契约外接口：${key.toUpperCase()}（首版 38 个接口之外，02 契约 §3）`);
}

// 2. 每个接口：认证、Idempotency-Key、参数中的 Idempotency-Key 格式
for (const [id, method, path] of EXPECTED_APIS) {
  const specPathKey = actualOps.get(`${method} ${norm(path)}`);
  if (!specPathKey) continue;
  const op = paths[specPathKey]?.[method];
  if (!op) continue;
  const key = `${method} ${norm(path)}`;

  const declaredSecurity = op.security ?? spec.security;
  const isSecure = Array.isArray(declaredSecurity) && declaredSecurity.length > 0;
  if (!NO_AUTH.has(key) && !isSecure) problems.push(`${id} 未声明 Bearer 认证：${key}`);
  if (NO_AUTH.has(key) && isSecure) problems.push(`${id} 应免登录但声明了认证：${key}`);

  const isWrite = method === 'post' || method === 'put' || method === 'patch';
  if (isWrite) {
    const headerParams = op.parameters ?? [];
    const hasIdem = headerParams.some(
      (p) => p.name.toLowerCase() === 'idempotency-key' && p.in === 'header',
    );
    if (!NO_IDEMPOTENCY.has(key) && !hasIdem) {
      problems.push(`${id} 缺少 Idempotency-Key header 参数：${key}（02 契约 §1）`);
    }
    if (!NO_IDEMPOTENCY.has(key) && hasIdem) {
      const p = headerParams.find((q) => q.name.toLowerCase() === 'idempotency-key');
      if (p && p.required !== true) problems.push(`${id} Idempotency-Key 必须 required=true：${key}`);
      if (p?.schema && p.schema.format !== 'uuid') {
        problems.push(`${id} Idempotency-Key 应为 UUID 格式：${key}`);
      }
    }
    const bodyRequired = op.requestBody?.required !== false && op.requestBody !== undefined;
    if (!NO_IDEMPOTENCY.has(key) && op.requestBody && !bodyRequired) {
      problems.push(`${id} 持久化请求的 requestBody 应 required=true：${key}`);
    }
  }
}

// 3. 枚举取值
for (const [name, values] of Object.entries(EXPECTED_ENUMS)) {
  const schema = schemas[name];
  if (!schema) {
    // 允许枚举内联在属性里：只要任何 schema 属性声明了同名枚举且取值一致即可
    let found = false;
    for (const [ownerName, owner] of Object.entries(schemas)) {
      for (const [propName, prop] of Object.entries(owner.properties ?? {})) {
        const p = prop as { enum?: unknown[] };
        if (p.enum && propName.toLowerCase() === name.toLowerCase()) {
          found = true;
          const actual = p.enum.map(String);
          if (JSON.stringify(actual) !== JSON.stringify(values)) {
            problems.push(`枚举 ${ownerName}.${propName} 取值不符：${actual.join(',')} ≠ ${values.join(',')}`);
          }
        }
      }
    }
    if (!found) problems.push(`缺少枚举定义 ${name}（02 契约 §2）`);
    continue;
  }
  const actual = (schema.enum ?? []).map(String);
  if (JSON.stringify(actual) !== JSON.stringify(values)) {
    problems.push(`枚举 ${name} 取值不符：${actual.join(',')} ≠ ${values.join(',')}`);
  }
}

// 4. 关键 DTO 必需字段
for (const [name, fields] of Object.entries(EXPECTED_DTO_FIELDS)) {
  const schema = schemas[name];
  if (!schema) {
    problems.push(`缺少 DTO 定义 ${name}（02 契约 §2）`);
    continue;
  }
  const required = schema.required ?? [];
  const props = Object.keys(schema.properties ?? {});
  for (const f of fields) {
    if (!props.includes(f)) problems.push(`${name} 缺少字段 ${f}`);
    else if (!required.includes(f)) problems.push(`${name} 字段 ${f} 未列入 required`);
  }
}

// 5. 响应包装（02 契约 §1：成功 {data,requestId}；错误 {error:{code,message,details},requestId}）
const successSchema = schemas['SuccessEnvelope'] ?? schemas['ApiResponse'] ?? null;
const errorSchema = schemas['ErrorEnvelope'] ?? schemas['ApiError'] ?? null;
if (successSchema) {
  const req = successSchema.required ?? [];
  for (const f of ['data', 'requestId']) {
    if (!req.includes(f)) problems.push(`成功信封必需字段 ${f} 未声明 required`);
  }
} else {
  problems.push('缺少成功响应包装定义（{data,requestId}，02 契约 §1）— 如以其他命名声明，请在报告中人工核对');
}
if (errorSchema) {
  const req = errorSchema.required ?? [];
  for (const f of ['error', 'requestId']) {
    if (!req.includes(f)) problems.push(`错误信封必需字段 ${f} 未声明 required`);
  }
  const err = (errorSchema.properties?.error as { required?: string[] } | undefined) ?? {};
  for (const f of ['code', 'message', 'details']) {
    if (!(err.required ?? []).includes(f)) problems.push(`error 对象必需字段 ${f} 未声明 required`);
  }
} else {
  problems.push('缺少错误响应包装定义（{error:{code,message,details},requestId}，02 契约 §1）— 如以其他命名声明，请在报告中人工核对');
}

// 6. securitySchemes 存在 Bearer 类型
const schemes = spec.components?.securitySchemes ?? {};
const hasBearer = Object.values(schemes).some((s) => {
  const t = s as { type?: string; scheme?: string };
  return t?.type === 'http' && t?.scheme?.toLowerCase() === 'bearer';
});
if (!hasBearer && Object.keys(schemes).length > 0) {
  problems.push('securitySchemes 中缺少 http bearer（02 契约 §1：Authorization: Bearer <token>）');
}

// ---------------------------------------------------------------- 报告

console.log(`[check-contract] 核对 ${EXPECTED_APIS.length} 个接口、${Object.keys(EXPECTED_ENUMS).length} 组枚举、${Object.keys(EXPECTED_DTO_FIELDS).length} 个 DTO`);
if (problems.length > 0) {
  for (const p of problems) console.error(`[check-contract] 差异：${p}`);
  console.error(`[check-contract] 共 ${problems.length} 处差异——交实现者或 PM 处理（02 契约 §7），不私自改契约迎合实现。`);
  process.exit(1);
}
console.log('[check-contract] 全部通过 ✓');
