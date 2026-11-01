import {
  test,
  expect,
  type APIRequestContext,
  type Page,
} from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { login, password, screenshots, successful, saveState } from './support'
import type {
  Actor,
  ActivityView,
  CalendarEntry,
  ChangeView,
  ResourceView,
  UserCreate,
} from '../../src/types/api'

// 使用真实 HTTP 和隔离 campus_test 库，不拦截/伪造服务响应。
// 每项经 API 建立唯一资源和最小权限账号，不修改演示账号的既有授权。
async function call<T>(
  request: APIRequestContext,
  token: string,
  path: string,
  method = 'GET',
  data?: unknown,
): Promise<T> {
  const response = await request.fetch(`/api/v1${path}`, {
    method,
    data,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(method === 'GET' ? {} : { 'Idempotency-Key': randomUUID() }),
    },
  })
  const body = await response.json()
  expect(
    response.ok(),
    JSON.stringify({ path, status: response.status(), error: body.error }),
  ).toBe(true)
  return body.data
}
async function authenticate(request: APIRequestContext, username: string) {
  const response = await request.post('/api/v1/auth/login', {
    data: { username, password },
  })
  expect(response.ok(), `登录 ${username}`).toBe(true)
  return (await response.json()).data as { token: string; user: Actor }
}
async function setup(request: APIRequestContext) {
  const id = randomUUID().slice(0, 8)
  const account = await authenticate(request, 'account_admin')
  const manager = await authenticate(request, 'resource_admin')
  const owner = await authenticate(request, 'applicant_a')
  const staff = await authenticate(request, 'staff_a')
  const resource = await call<ResourceView>(
    request,
    manager.token,
    '/resources',
    'POST',
    {
      kind: 'VENUE',
      name: `UI评审场地 ${id}`,
      category: 'VENUE',
      capacity: 80,
      quantity: 1,
      openFrom: '08:00',
      openTo: '22:00',
      allowTeacher: true,
      allowStudent: true,
    },
  )
  const org = owner.user.organizationId
  const createUser = async (suffix: string, overrides: Partial<UserCreate>) => {
    const input: UserCreate = {
      username: `ui_review_${id}_${suffix}`,
      displayName: `UI评审${suffix} ${id}`,
      password,
      organizationId: org,
      identity: 'TEACHER',
      roles: ['APPROVER'],
      permissions: [],
      approvalScopes: [{ organizationId: org, resourceCategories: ['VENUE'] }],
      managedResourceIds: [],
      ...overrides,
    }
    const user = await call<Actor>(
      request,
      account.token,
      '/users',
      'POST',
      input,
    )
    return { username: input.username, user }
  }
  const approver = await createUser('approver', {})
  const confirmer = await createUser('resource', {
    roles: ['ADMIN'],
    permissions: ['RESOURCE_CONFIRM'],
    approvalScopes: [],
    managedResourceIds: [resource.id],
  })
  const draft = await call<ActivityView>(
    request,
    owner.token,
    '/activities',
    'POST',
    {
      title: `UI评审活动 ${id}`,
      description: `评审专用内部说明 ${id}`,
      expectedAttendees: 30,
      registrationCapacity: 20,
      staffUserIds: [staff.user.id],
      plan: {
        startAt: '2030-10-22T10:00:00+08:00',
        endAt: '2030-10-22T11:00:00+08:00',
        setupMinutes: 0,
        teardownMinutes: 0,
        venueId: resource.id,
        equipment: [],
      },
    },
  )
  return {
    account,
    manager,
    owner,
    staff,
    resource,
    approver,
    confirmer,
    draft,
    createUser,
  }
}
async function clickCommand<T>(
  page: Page,
  testId: string,
  suffix: string,
): Promise<T> {
  const response = page.waitForResponse(
    (r) => r.url().endsWith(suffix) && r.request().method() !== 'GET',
  )
  await page.getByTestId(testId).click()
  return successful(await response)
}
test.beforeEach(async ({ page }) => {
  expect((await page.request.get('/api/v1/health')).ok()).toBe(true)
  page.on('pageerror', (error) => {
    throw error
  })
})

test('真实纯角色与跨组织工作人员展示，撤销审批范围后刷新隐藏旧详情', async ({
  page,
}) => {
  test.setTimeout(120000)
  const f = await setup(page.request)
  const path = `/activities/${f.draft.id}`
  await call(page.request, f.owner.token, `${path}/submit`, 'POST', {
    expectedLockVersion: f.draft.lockVersion,
  })
  const staffRequests: string[] = []
  page.on('request', (r) => {
    if (r.url().includes('/users?purpose=staff')) staffRequests.push(r.url())
  })
  const checkStaff = async (username: string) => {
    await login(page, username, path)
    await expect(
      page.getByTestId(`activity-staff-${f.staff.user.id}`),
    ).toHaveText(f.staff.user.id)
    await expect(page.getByTestId('staff-name-unavailable')).toBeVisible()
    await expect(page.getByTestId('api-error')).toHaveCount(0)
    expect(staffRequests).toHaveLength(0)
  }
  await checkStaff(f.approver.username)
  const organizations = await call<{ id: string }[]>(
    page.request,
    f.account.token,
    '/organizations',
  )
  const otherOrg = organizations.find(
    (o) => o.id !== f.owner.user.organizationId,
  )!
  expect(otherOrg).toBeDefined()
  const cross = await f.createUser('cross', {
    roles: ['APPLICANT', 'APPROVER'],
    organizationId: otherOrg.id,
  })
  await checkStaff(cross.username)
  await screenshots(page, 'real-cross-org-staff')
  await call(
    page.request,
    f.account.token,
    `/users/${cross.user.id}/authorization`,
    'PATCH',
    {
      identity: cross.user.identity,
      roles: cross.user.roles,
      permissions: [],
      approvalScopes: [],
      managedResourceIds: [],
    },
  )
  const denied = page.waitForResponse((r) => r.url().endsWith(`/api/v1${path}`))
  await page.getByTestId('activity-refresh').click()
  expect((await denied).status()).toBe(403)
  await expect(page.getByTestId('activity-records')).toHaveCount(0)
  await expect(page.getByTestId('registration-submit')).toHaveCount(0)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('活动详情')
  await expect(page.locator('main')).not.toContainText(
    f.draft.input.description,
  )
  await screenshots(page, 'real-revoked-detail')
  await checkStaff(f.approver.username)
  // 空批准意见触发当前 AN/CM 事件校验缺陷，单独保留失败证据；本主线明确填写意见。
  await page.locator('#comment').fill('范围与安排已核对，同意活动')
  const approved = await clickCommand<ActivityView>(
    page,
    'approver-approve',
    `${path}/approver-decision`,
  )
  expect(approved.status).toBe('APPROVED_UNRESERVED')
  await checkStaff(f.confirmer.username)
})

test('真实首次预约报名与改期本人确认、审批、生效和日历通知刷新', async ({
  page,
}) => {
  test.setTimeout(180000)
  const f = await setup(page.request)
  const path = `/activities/${f.draft.id}`
  await login(page, 'applicant_a', path)
  expect(
    (
      await clickCommand<ActivityView>(
        page,
        'activity-submit',
        `${path}/submit`,
      )
    ).status,
  ).toBe('PENDING_APPROVER')
  await login(page, f.approver.username, path)
  await page.locator('#comment').fill('范围与安排已核对，同意活动')
  expect(
    (
      await clickCommand<ActivityView>(
        page,
        'approver-approve',
        `${path}/approver-decision`,
      )
    ).status,
  ).toBe('APPROVED_UNRESERVED')
  await login(page, f.confirmer.username, path)
  expect(
    (
      await clickCommand<ActivityView>(
        page,
        'resource-confirm',
        `${path}/resource-confirm`,
      )
    ).status,
  ).toBe('SCHEDULED')
  await login(page, 'applicant_a', path)
  expect(
    (
      await clickCommand<ActivityView>(
        page,
        'activity-publish',
        `${path}/publish`,
      )
    ).status,
  ).toBe('OPEN')
  await login(page, 'student_b', path)
  expect(
    (
      await clickCommand<{ status: string }>(
        page,
        'registration-submit',
        `${path}/registration`,
      )
    ).status,
  ).toBe('ACTIVE')
  await expect(page.getByTestId('registration-submit')).toHaveText('退出报名')
  expect(
    (
      await clickCommand<{ status: string }>(
        page,
        'registration-submit',
        `${path}/registration`,
      )
    ).status,
  ).toBe('WITHDRAWN')
  await expect(page.getByTestId('registration-submit')).toHaveText('报名参加')
  expect(
    (
      await clickCommand<{ status: string }>(
        page,
        'registration-submit',
        `${path}/registration`,
      )
    ).status,
  ).toBe('ACTIVE')
  await login(page, 'applicant_a', `${path}/change`)
  await page.getByTestId('change-start').fill('2030-10-22T12:00')
  await page.getByTestId('change-end').fill('2030-10-22T13:00')
  await clickCommand(page, 'change-preview', `${path}/change-preview`)
  await page
    .getByTestId('change-reason')
    .fill('工作人员安排调整，验证真实改期链路')
  const change = await clickCommand<ChangeView>(
    page,
    'change-create',
    `${path}/changes`,
  )
  expect(change.status).toBe('AWAITING_CONFIRMATIONS')
  const changePath = `/changes/${change.id}`
  await login(page, 'student_b', path)
  await expect(page.getByTestId('registration-submit')).toBeDisabled()
  await expect(page.locator('main')).toContainText('改期处理中')
  await login(page, 'staff_a', changePath)
  expect(
    (
      await clickCommand<ChangeView>(
        page,
        'staff-approve',
        `${changePath}/confirmation`,
      )
    ).status,
  ).toBe('PENDING_APPROVER')
  await login(page, f.approver.username, changePath)
  expect(
    (
      await clickCommand<ChangeView>(
        page,
        'approver-approve',
        `${changePath}/approver-decision`,
      )
    ).status,
  ).toBe('PENDING_RESOURCE')
  await login(page, f.confirmer.username, changePath)
  const applied = await clickCommand<ChangeView>(
    page,
    'change-resource-approve',
    `${changePath}/resource-decision`,
  )
  expect(applied.status).toBe('APPLIED')
  await login(page, 'applicant_a', changePath)
  await expect(page.getByTestId('current-version')).toContainText(
    `有效版本 ${applied.appliedVersionNo}`,
  )
  await expect(page.getByTestId('resource-calendar')).toBeVisible()
  await expect
    .poll(async () =>
      (
        await call<{ activityId: string; versionNo: number }[]>(
          page.request,
          f.owner.token,
          '/notifications',
        )
      ).some(
        (n) =>
          n.activityId === f.draft.id &&
          n.versionNo === applied.appliedVersionNo,
      ),
    )
    .toBe(true)
  await page.getByTestId('change-refresh').click()
  await expect(page.locator('.notification')).not.toHaveCount(0)
  await screenshots(page, 'real-change-applied')
  await page.reload()
  await expect(page.getByTestId('change-status')).toContainText('已生效')
  const current = await call<ActivityView>(page.request, f.owner.token, path)
  expect(current.input.plan).toEqual(applied.targetPlan)
  expect(current.registrationCount).toBe(1)
  const calendar = await call<
    { activityId: string | null; startsAt: string }[]
  >(
    page.request,
    f.owner.token,
    `/resources/calendar?from=2030-10-22T00:00:00%2B08:00&to=2030-10-23T00:00:00%2B08:00&resourceId=${f.resource.id}`,
  )
  expect(
    calendar
      .filter((c) => c.activityId === current.id)
      .some(
        (c) =>
          Date.parse(c.startsAt) === Date.parse(applied.targetPlan.startAt),
      ),
  ).toBe(true)
  saveState({
    reviewActivityId: current.id,
    reviewChangeId: change.id,
    reviewVersion: current.effectiveVersionNo,
    reviewPlan: current.input.plan,
  })
  await expectStaffHistory(page, f, current.id, change.id, '改期已生效')
  await screenshots(page, 'real-staff-applied-history')
  await login(page, 'student_b', path)
  const deniedHistory = page.waitForResponse((r) =>
    r.url().endsWith(`${path}/changes`),
  )
  await page.getByRole('tab', { name: '改期记录', exact: true }).click()
  expect((await deniedHistory).status()).toBe(403)
  await expect(page.getByTestId('api-error')).toBeVisible()
  await expect(page.getByTestId('change-history')).toHaveCount(0)
  await expect(page.locator('main')).not.toContainText('暂无改期记录')
})

async function expectStaffHistory(
  page: Page,
  f: Awaited<ReturnType<typeof setup>>,
  activityId: string,
  changeId: string,
  label: string,
) {
  const path = `/activities/${activityId}`
  await login(page, 'staff_a', path)
  const current = await call<ActivityView>(page.request, f.staff.token, path)
  expect(current.input.staffUserIds).toEqual([])
  const historyResponse = page.waitForResponse((r) =>
    r.url().endsWith(`${path}/changes`),
  )
  await page.getByRole('tab', { name: '改期记录', exact: true }).click()
  expect((await historyResponse).status()).toBe(200)
  const link = page.getByTestId(`change-record-${changeId}`)
  await expect(link).toContainText(label)
  await link.click()
  await expect(page).toHaveURL(new RegExp(`/changes/${changeId}$`))
  await expect(page.getByTestId('change-status')).toHaveText(label)
}

// 失败分支使用真实API准备前置状态，关键决定和反馈通过页面验证。
async function openActivity(
  page: Page,
  f: Awaited<ReturnType<typeof setup>>,
  draft = f.draft,
  ownerToken = f.owner.token,
) {
  const path = `/activities/${draft.id}`
  const approver = await authenticate(page.request, f.approver.username)
  const confirmer = await authenticate(page.request, f.confirmer.username)
  let current = await call<ActivityView>(
    page.request,
    ownerToken,
    `${path}/submit`,
    'POST',
    { expectedLockVersion: draft.lockVersion },
  )
  current = await call<ActivityView>(
    page.request,
    approver.token,
    `${path}/approver-decision`,
    'POST',
    {
      expectedLockVersion: current.lockVersion,
      decision: 'APPROVE',
      comment: '已核对范围及安排',
    },
  )
  current = await call<ActivityView>(
    page.request,
    confirmer.token,
    `${path}/resource-confirm`,
    'POST',
    { expectedLockVersion: current.lockVersion },
  )
  return call<ActivityView>(
    page.request,
    ownerToken,
    `${path}/publish`,
    'POST',
    { expectedLockVersion: current.lockVersion },
  )
}
async function expectOriginalReservation(
  page: Page,
  f: Awaited<ReturnType<typeof setup>>,
  original: ActivityView,
) {
  const entries = await call<CalendarEntry[]>(
    page.request,
    f.owner.token,
    `/resources/calendar?from=2030-10-22T00:00:00%2B08:00&to=2030-10-23T00:00:00%2B08:00&resourceId=${f.resource.id}`,
  )
  expect(
    entries
      .filter((e) => e.activityId === original.id)
      .map((e) => ({
        resourceId: e.resourceId,
        start: Date.parse(e.startsAt),
        end: Date.parse(e.endsAt),
        quantity: e.quantity,
      })),
  ).toEqual([
    {
      resourceId: f.resource.id,
      start: Date.parse(original.input.plan.startAt),
      end: Date.parse(original.input.plan.endAt),
      quantity: 1,
    },
  ])
}

async function createPending(page: Page, f: Awaited<ReturnType<typeof setup>>) {
  const current = await call<ActivityView>(
    page.request,
    f.owner.token,
    `/activities/${f.draft.id}`,
  )
  return call<ChangeView>(
    page.request,
    f.owner.token,
    `/activities/${current.id}/changes`,
    'POST',
    {
      expectedLockVersion: current.lockVersion,
      baseVersionNo: current.versionNo,
      targetPlan: {
        ...current.input.plan,
        startAt: '2030-10-22T12:00:00+08:00',
        endAt: '2030-10-22T13:00:00+08:00',
      },
      reason: '真实失败分支验证',
    },
  )
}

test('真实通知已读不代签，拒绝及撤回解除报名冻结并保留原安排', async ({
  page,
}) => {
  test.setTimeout(180000)
  const f = await setup(page.request)
  const original = await openActivity(page, f)
  const path = `/activities/${original.id}`
  const change = await createPending(page, f)
  const changePath = `/changes/${change.id}`
  await expectStaffHistory(page, f, original.id, change.id, '等待工作人员确认')
  await login(page, 'student_b', path)
  await expect(page.getByTestId('registration-submit')).toBeDisabled()
  await expect
    .poll(async () =>
      (
        await call<{ activityId: string }[]>(
          page.request,
          f.staff.token,
          '/notifications',
        )
      ).some((n) => n.activityId === original.id),
    )
    .toBe(true)
  await login(page, 'staff_a', '/notifications')
  const notification = page
    .getByTestId('notification-item')
    .filter({ hasText: original.input.title })
    .first()
  const read = page.waitForResponse((r) =>
    /\/notifications\/[^/]+\/read$/.test(r.url()),
  )
  await notification.getByTestId('notification-read').click()
  await successful(await read)
  const stillPending = await call<ChangeView>(
    page.request,
    f.staff.token,
    changePath,
  )
  expect(
    stillPending.confirmations.find((c) => c.userId === f.staff.user.id)
      ?.decision,
  ).toBeNull()
  await page.goto(changePath)
  await page.getByTestId('change-reject').click()
  await page.getByTestId('reject-reason').fill('本人新时段无法参加')
  const rejected = await clickCommand<ChangeView>(
    page,
    'reject-confirm',
    `${changePath}/confirmation`,
  )
  expect(rejected.status).toBe('REJECTED')
  await expect(page.getByTestId('change-status')).toHaveText('已拒绝')
  await expectStaffHistory(page, f, original.id, change.id, '已拒绝')
  let current = await call<ActivityView>(page.request, f.owner.token, path)
  expect(current.input.plan).toEqual(original.input.plan)
  expect(current.activeChangeId).toBeNull()
  await login(page, 'student_b', path)
  await expectOriginalReservation(page, f, original)
  expect(
    (
      await clickCommand<{ status: string }>(
        page,
        'registration-submit',
        `${path}/registration`,
      )
    ).status,
  ).toBe('ACTIVE')
  const second = await createPending(page, f)
  await page.reload()
  await expect(page.getByTestId('registration-submit')).toBeDisabled()
  await login(page, 'applicant_a', `/changes/${second.id}`)
  await page.getByTestId('change-comment').fill('保留原安排，撤回申请')
  await page.getByTestId('change-withdraw').click()
  const withdrawn = page.waitForResponse((r) =>
    r.url().endsWith(`/changes/${second.id}/withdraw`),
  )
  await page.locator('.el-popconfirm').getByRole('button').last().click()
  expect((await successful(await withdrawn)).status).toBe('WITHDRAWN')
  await expect(page.getByTestId('change-status')).toHaveText('已撤回')
  await expectStaffHistory(page, f, original.id, second.id, '已撤回')
  await screenshots(page, 'real-withdrawn')
  current = await call<ActivityView>(page.request, f.owner.token, path)
  expect(current.input.plan).toEqual(original.input.plan)
  expect(current.effectiveVersionNo).toBe(original.effectiveVersionNo)
  await login(page, 'student_b', path)
  await expectOriginalReservation(page, f, original)
  expect(
    (
      await clickCommand<{ status: string }>(
        page,
        'registration-submit',
        `${path}/registration`,
      )
    ).status,
  ).toBe('WITHDRAWN')
})

test('真实改期资源竞争返回409保留旧安排，故障标为AT_RISK', async ({ page }) => {
  test.setTimeout(180000)
  const f = await setup(page.request)
  const original = await openActivity(page, f)
  const change = await createPending(page, f)
  const changePath = `/changes/${change.id}`
  await call(
    page.request,
    f.staff.token,
    `${changePath}/confirmation`,
    'POST',
    { decision: 'APPROVE', comment: '' },
  )
  const approver = await authenticate(page.request, f.approver.username)
  await call(
    page.request,
    approver.token,
    `${changePath}/approver-decision`,
    'POST',
    { decision: 'APPROVE', comment: '' },
  )
  const competitor = await authenticate(page.request, 'applicant_b')
  const competingDraft = await call<ActivityView>(
    page.request,
    competitor.token,
    '/activities',
    'POST',
    {
      ...original.input,
      title: `${original.input.title} · 占用目标`,
      staffUserIds: [],
      plan: change.targetPlan,
    },
  )
  await openActivity(page, f, competingDraft, competitor.token)
  await login(page, f.confirmer.username, changePath)
  const conflict = page.waitForResponse((r) =>
    r.url().endsWith(`${changePath}/resource-decision`),
  )
  await page.getByTestId('change-resource-approve').click()
  const conflictResponse = await conflict
  expect(conflictResponse.status()).toBe(409)
  expect((await conflictResponse.json()).error.code).toBe('RESOURCE_CONFLICT')
  await expect(page.getByTestId('change-status')).toHaveText('资源冲突')
  await expect(page.getByTestId('conflict-list')).toContainText(f.resource.name)
  await expect(page.locator('main')).toContainText('原安排未被本次改期修改')
  const current = await call<ActivityView>(
    page.request,
    f.owner.token,
    `/activities/${original.id}`,
  )
  expect(current.input.plan).toEqual(original.input.plan)
  expect(current.effectiveVersionNo).toBe(original.effectiveVersionNo)
  expect(current.activeChangeId).toBeNull()
  await call(
    page.request,
    f.manager.token,
    `/resources/${f.resource.id}/faults`,
    'POST',
    {
      startAt: original.input.plan.startAt,
      endAt: original.input.plan.endAt,
      unavailableQuantity: 1,
      reason: '隔离测试场地维护',
    },
  )
  await login(page, 'applicant_a', `/activities/${original.id}`)
  await expect(page.getByTestId('risk-banner')).toContainText('故障')
  const atRisk = await call<ActivityView>(
    page.request,
    f.owner.token,
    `/activities/${original.id}`,
  )
  expect(atRisk.resourceRisk).toBe('AT_RISK')
  expect(atRisk.input.plan).toEqual(original.input.plan)
  await expectOriginalReservation(page, f, original)
  await screenshots(page, 'real-conflict-risk')
})

test('真实报名响应丢失后同key同body重试，不重复增加人数', async ({ page }) => {
  test.setTimeout(120000)
  const f = await setup(page.request)
  const original = await openActivity(page, f)
  const path = `/activities/${original.id}`
  await login(page, 'student_b', path)
  const sent: { key: string | undefined; body: string | null }[] = []
  // 只丢弃一次真实后端响应，不合成成功数据；第二次原样转发给同一后端。
  await page.route(`**/api/v1${path}/registration`, async (route) => {
    const request = route.request()
    sent.push({
      key: request.headers()['idempotency-key'],
      body: request.postData(),
    })
    if (sent.length === 1) {
      const actual = await route.fetch()
      expect(actual.ok()).toBe(true)
      await route.abort('failed')
    } else await route.continue()
  })
  await page.getByTestId('registration-submit').click()
  await expect(page.getByTestId('mutation-retry')).toBeVisible()
  await expect(page.getByTestId('registration-submit')).toBeDisabled()
  expect(
    (await call<ActivityView>(page.request, f.owner.token, path))
      .registrationCount,
  ).toBe(1)
  await page.getByTestId('mutation-retry').click()
  await expect(page.getByTestId('registration-submit')).toHaveText('退出报名')
  await expect(page.getByTestId('registration-submit')).toBeEnabled()
  expect(sent).toHaveLength(2)
  expect(sent[0].key).toBeTruthy()
  expect(sent[1]).toEqual(sent[0])
  expect(
    (await call<ActivityView>(page.request, f.owner.token, path))
      .registrationCount,
  ).toBe(1)
})

test('真实资源资格被外部关闭后刷新与保存均保持服务端最新值', async ({
  page,
}) => {
  const manager = await authenticate(page.request, 'resource_admin')
  const resource = await call<ResourceView>(
    page.request,
    manager.token,
    '/resources',
    'POST',
    {
      kind: 'VENUE',
      name: `UI资格刷新 ${randomUUID().slice(0, 8)}`,
      category: 'VENUE',
      capacity: 40,
      quantity: 1,
      openFrom: '08:00',
      openTo: '22:00',
      allowTeacher: true,
      allowStudent: true,
    },
  )
  await login(page, 'resource_admin', '/resources/manage')
  await page.getByTestId(`resource-select-${resource.id}`).click()
  const teacher = page.getByTestId('eligibility-teacher').locator('input')
  const student = page.getByTestId('eligibility-student').locator('input')
  await expect(teacher).toBeChecked()
  await expect(student).toBeChecked()
  await call(
    page.request,
    manager.token,
    `/resources/${resource.id}/eligibility`,
    'PATCH',
    {
      allowTeacher: false,
      allowStudent: false,
    },
  )
  const refreshed = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === '/api/v1/resources' &&
      r.request().method() === 'GET',
  )
  await page
    .getByRole('button', { name: '刷新资源与权限', exact: true })
    .click()
  expect((await refreshed).status()).toBe(200)
  await expect(teacher).not.toBeChecked()
  await expect(student).not.toBeChecked()
  await page.getByTestId('eligibility-save').click()
  const saved = page.waitForResponse(
    (r) =>
      r.url().endsWith(`/resources/${resource.id}/eligibility`) &&
      r.request().method() === 'PATCH',
  )
  await page.locator('.el-popconfirm').getByRole('button').last().click()
  const response = await saved
  expect(response.request().postDataJSON()).toEqual({
    allowTeacher: false,
    allowStudent: false,
  })
  const confirmed = await successful(response)
  expect(confirmed.allowTeacher).toBe(false)
  expect(confirmed.allowStudent).toBe(false)
  const reread = await call<ResourceView[]>(
    page.request,
    manager.token,
    '/resources',
  )
  expect(reread.find((r) => r.id === resource.id)).toMatchObject({
    allowTeacher: false,
    allowStudent: false,
  })
  await screenshots(page, 'real-eligibility-refresh-saved')
})
