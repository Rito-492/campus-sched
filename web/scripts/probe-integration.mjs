// 真实服务诊断；仅在UI隔离测试库、real-service完成后运行，不属于通过的业务E2E。
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { randomUUID } from 'node:crypto'
const password = process.env.UI_DEMO_PASSWORD
if (!password) throw new Error('缺少 UI_DEMO_PASSWORD')
const base = process.env.UI_API_URL || 'http://127.0.0.1:8080/api/v1'
const state = JSON.parse(
  readFileSync('.integration-runtime/smoke-state.json', 'utf8'),
)
const report = []
async function call(token, method, path, body) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(method !== 'GET' ? { 'Idempotency-Key': randomUUID() } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  return { status: response.status, payload: await response.json() }
}
async function authenticate(username) {
  const r = await call(null, 'POST', '/auth/login', { username, password })
  if (r.status !== 200) throw new Error(`真实登录失败 ${r.status}`)
  return r.payload.data.token
}
async function probe(token, method, path, body) {
  const r = await call(token, method, path, body)
  report.push({
    method,
    path,
    ...(body ? { body } : {}),
    status: r.status,
    requestId: r.payload.requestId,
    ...(r.payload.error ? { error: r.payload.error } : {}),
  })
  return r
}
const applicant = await authenticate('applicant_b')
const admin = await authenticate('resource_admin')
const activity = (
  await call(applicant, 'GET', `/activities/${state.activityId}`)
).payload.data
await probe(applicant, 'GET', '/activities?scope=public')
await probe(applicant, 'GET', '/notifications?unreadOnly=false')
await probe(applicant, 'GET', `/activities/${state.activityId}/changes`)
await probe(applicant, 'GET', `/activities/${state.activityId}/audit`)
await probe(
  applicant,
  'POST',
  `/activities/${state.activityId}/change-preview`,
  { baseVersionNo: activity.versionNo, targetPlan: activity.input.plan },
)
await probe(applicant, 'POST', `/activities/${state.activityId}/submit`, {
  expectedLockVersion: activity.lockVersion,
})
await probe(admin, 'POST', `/resources/${state.resourceId}/faults`, {
  startAt: '2030-10-21T10:00:00+08:00',
  endAt: '2030-10-21T11:00:00+08:00',
  unavailableQuantity: 1,
  reason: 'UI隔离环境验证服务缺口',
})
const after = (await call(applicant, 'GET', `/activities/${state.activityId}`))
  .payload.data
const out = resolve(
  '../artifacts/test-results/ui-integration-2026-10-31/endpoint-probes.json',
)
mkdirSync(resolve(out, '..'), { recursive: true })
writeFileSync(
  out,
  JSON.stringify(
    {
      time: new Date().toISOString(),
      baseline: 'feb0d70',
      report,
      draftAfterFailure: {
        id: after.id,
        status: after.status,
        lockVersion: after.lockVersion,
      },
      unchanged:
        after.status === activity.status &&
        after.lockVersion === activity.lockVersion,
    },
    null,
    2,
  ),
)
await call(applicant, 'POST', '/auth/logout')
await call(admin, 'POST', '/auth/logout')
console.log(
  JSON.stringify(
    report.map(({ method, path, status, error }) => ({
      method,
      path,
      status,
      code: error?.code,
    })),
    null,
    2,
  ),
)
