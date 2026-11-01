import { test, expect } from '@playwright/test'
import {
  authenticatedGet,
  login,
  password,
  saveState,
  screenshots,
  successful,
} from './support'
import { randomUUID } from 'node:crypto'

// 这是UI局部真实联调，不是QA完整E2E，不使用route.fulfill或生产替身。
test.beforeEach(async ({ page }) => {
  const health = await page.request.get('/api/v1/health')
  expect(health.ok()).toBe(true)
  page.on('pageerror', (error) => {
    throw error
  })
})

test('真实登录、教师无隐式审批、刷新会话与退出', async ({ page }) => {
  await login(page, 'applicant_a', '/resources/calendar')
  await expect(
    page.getByRole('navigation').getByRole('link', { name: '审批与待办' }),
  ).toHaveCount(0)
  await expect(
    page.getByRole('navigation').getByRole('link', { name: '账号与授权' }),
  ).toHaveCount(0)
  await page.getByLabel('日期', { exact: true }).fill('2030-10-20')
  await expect(page.getByTestId('resource-calendar')).toBeVisible()
  await screenshots(page, 'real-calendar')
  await page.reload()
  await expect(page.locator('.account')).toContainText('申请人A')
  expect(
    await page.evaluate(() => !!sessionStorage.getItem('campus-sched.session')),
  ).toBe(true)
  expect(
    await page.evaluate(() => localStorage.getItem('campus-sched.session')),
  ).toBeNull()
  expect((await authenticatedGet(page, '/users')).status()).toBe(403)
  await page.goto('/admin/users')
  await expect(page).toHaveURL(/\/forbidden$/)
  await page.getByRole('button', { name: '退出', exact: true }).click()
  await expect(page).toHaveURL(/\/login/)
  await page.goto('/admin/users')
  await expect(page).toHaveURL(/\/login\?redirect=/)
})

test('真实账号创建、完整授权保存与启停持久化', async ({ page }) => {
  const username = `ui_${Date.now()}`
  await login(page, 'account_admin', '/admin/users')
  await expect(page.getByTestId('user-create-open')).toBeEnabled()
  await page.getByTestId('user-create-open').click()
  let dialog = page.getByRole('dialog')
  await dialog.getByTestId('user-username').fill(username)
  await dialog.getByTestId('user-display-name').fill(`UI联调账号 ${username}`)
  await dialog.getByTestId('user-password').fill(password)
  await dialog.getByTestId('user-organization').click()
  await page.getByRole('option', { name: '软件学院', exact: true }).click()
  const creating = page.waitForResponse(
    (r) => r.url().endsWith('/api/v1/users') && r.request().method() === 'POST',
  )
  await dialog.getByTestId('user-save').click()
  const user = await successful(await creating)
  saveState({ userId: user.id, username })
  await expect(dialog).not.toBeVisible()
  await page.getByTestId(`user-authorize-${user.id}`).click()
  dialog = page.getByRole('dialog')
  await dialog.getByTestId('authorization-identity').selectOption('TEACHER')
  await dialog.getByTestId('authorization-confirm').check()
  const updating = page.waitForResponse((r) =>
    r.url().endsWith(`/users/${user.id}/authorization`),
  )
  await dialog.getByTestId('user-save').click()
  expect((await successful(await updating)).identity).toBe('TEACHER')
  await expect(dialog).not.toBeVisible()
  await page.reload()
  await expect(page.getByTestId(`user-${user.id}`)).toContainText('教师')
  await page.getByTestId(`user-toggle-${user.id}`).click()
  const disabling = page.waitForResponse((r) =>
    r.url().endsWith(`/users/${user.id}/enabled`),
  )
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await successful(await disabling)
  await page.reload()
  await expect(page.getByTestId(`user-${user.id}`)).toContainText('已停用')
  await page.getByTestId('users-search').fill(username)
  await screenshots(page, 'real-authorization')
})

test('真实资源创建、资格更新与刷新', async ({ page }) => {
  await login(page, 'resource_admin', '/resources/manage')
  const name = `UI联调场地 ${Date.now()}`
  await page.getByTestId('resource-new').click()
  await page.getByTestId('resource-name').fill(name)
  await page
    .locator('.resource-create')
    .getByText('允许教师申请', { exact: true })
    .click()
  const creating = page.waitForResponse(
    (r) =>
      r.url().endsWith('/api/v1/resources') && r.request().method() === 'POST',
  )
  await page.getByTestId('resource-create').click()
  const resource = await successful(await creating)
  saveState({ resourceId: resource.id, resourceName: name })
  await page.getByTestId(`resource-select-${resource.id}`).click()
  await expect(
    page.getByTestId('eligibility-teacher').locator('input'),
  ).toBeChecked()
  await page.getByTestId('eligibility-student').click()
  await page.getByTestId('eligibility-save').click()
  const updating = page.waitForResponse((r) =>
    r.url().endsWith(`/resources/${resource.id}/eligibility`),
  )
  await page.locator('.el-popconfirm').getByRole('button').last().click()
  expect((await successful(await updating)).allowStudent).toBe(true)
  await page.reload()
  await page.getByTestId(`resource-select-${resource.id}`).click()
  await expect(
    page.getByTestId('eligibility-student').locator('input'),
  ).toBeChecked()
  await screenshots(page, 'real-resource')
})

test('真实分步创建草稿、修改、调度预览与刷新持久化', async ({ page }) => {
  const title = `UI真实草稿 ${Date.now()}`
  await login(page, 'applicant_b', '/activities/new')
  await page.getByTestId('activity-title').fill(title)
  await page
    .getByLabel('活动简介', { exact: true })
    .fill('面向软件学院的校园交流活动，先确认资源与人员安排，再提交审批。')
  await page.getByRole('button', { name: '下一步', exact: true }).click()
  await page.getByTestId('activity-start').fill('2030-10-21T10:00')
  await page.getByTestId('activity-end').fill('2030-10-21T11:00')
  await page.getByTestId('activity-venue').click()
  await page
    .getByRole('option')
    .filter({ hasText: /^学生活动室E/ })
    .click()
  await page.getByRole('button', { name: '下一步', exact: true }).click()
  await page.getByRole('button', { name: '下一步', exact: true }).click()
  const creating = page.waitForResponse(
    (r) =>
      r.url().endsWith('/api/v1/activities') && r.request().method() === 'POST',
  )
  await page.getByTestId('activity-save').click()
  const activity = await successful(await creating)
  await expect(page).toHaveURL(new RegExp(`/activities/${activity.id}$`))
  await page.getByTestId('draft-edit').click()
  await page.getByTestId('draft-title').fill(`${title} · 已修改`)
  const editing = page.waitForResponse(
    (r) =>
      r.url().endsWith(`/activities/${activity.id}`) &&
      r.request().method() === 'PATCH',
  )
  await page.getByTestId('draft-save').click()
  const edited = await successful(await editing)
  saveState({
    activityId: activity.id,
    activityTitle: edited.input.title,
    activityVenueId: edited.input.plan.venueId,
    lockVersion: edited.lockVersion,
  })
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(
    edited.input.title,
  )
  const previewing = page.waitForResponse((r) =>
    r.url().endsWith(`/activities/${activity.id}/schedule-preview`),
  )
  await page.getByTestId('activity-preview').click()
  expect((await successful(await previewing)).feasible).toBe(true)
  await page.reload()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(
    edited.input.title,
  )
  const saved = await authenticatedGet(page, `/activities/${activity.id}`)
  expect((await saved.json()).data.lockVersion).toBe(edited.lockVersion)
  await screenshots(page, 'real-draft')
})

test('管理员更新资格后，真实预览解释具体受限场地并保留草稿', async ({
  page,
}) => {
  // 通过真实API创建本项专用数据，不读取上一测试或上次运行遗留的状态。
  const auth = await page.request.post('/api/v1/auth/login', {
    data: { username: 'resource_admin', password },
  })
  expect(auth.ok()).toBe(true)
  const token = (await auth.json()).data.token
  const resourceName = `UI资格联调场地 ${Date.now()}`
  const resourceResponse = await page.request.post('/api/v1/resources', {
    headers: {
      Authorization: `Bearer ${token}`,
      'Idempotency-Key': randomUUID(),
    },
    data: {
      kind: 'VENUE',
      name: resourceName,
      category: 'VENUE',
      capacity: 80,
      quantity: 1,
      openFrom: '08:00',
      openTo: '22:00',
      allowTeacher: true,
      allowStudent: true,
    },
  })
  expect(resourceResponse.ok()).toBe(true)
  const resource = (await resourceResponse.json()).data
  await login(page, 'applicant_b', '/activities/new')
  const applicantToken = await page.evaluate(() =>
    sessionStorage.getItem('campus-sched.session'),
  )
  const activityTitle = `资格变更演示 ${Date.now()}`
  const activityResponse = await page.request.post('/api/v1/activities', {
    headers: {
      Authorization: `Bearer ${applicantToken}`,
      'Idempotency-Key': randomUUID(),
    },
    data: {
      title: activityTitle,
      description: '核对申请人身份与最新资源资格。',
      expectedAttendees: 30,
      registrationCapacity: 20,
      staffUserIds: [],
      plan: {
        startAt: '2030-10-21T10:00:00+08:00',
        endAt: '2030-10-21T11:00:00+08:00',
        setupMinutes: 0,
        teardownMinutes: 0,
        venueId: resource.id,
        equipment: [],
      },
    },
  })
  expect(activityResponse.ok()).toBe(true)
  const activity = (await activityResponse.json()).data
  await page.goto(`/activities/${activity.id}`)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(
    activityTitle,
  )
  const setEligibility = (enabled: boolean) =>
    page.request.patch(`/api/v1/resources/${resource.id}/eligibility`, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Idempotency-Key': randomUUID(),
      },
      data: { allowTeacher: enabled, allowStudent: enabled },
    })
  try {
    expect((await setEligibility(false)).ok()).toBe(true)
    const previewing = page.waitForResponse((r) =>
      r.url().endsWith(`/activities/${activity.id}/schedule-preview`),
    )
    await page.getByTestId('activity-preview').click()
    expect((await previewing).status()).toBe(403)
    await expect(page.getByTestId('eligibility-error')).toContainText(
      resourceName,
    )
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(
      activityTitle,
    )
    await screenshots(page, 'real-eligibility-error')
  } finally {
    expect((await setEligibility(true)).ok()).toBe(true)
    await page.request.post('/api/v1/auth/logout', {
      headers: { Authorization: `Bearer ${token}` },
    })
  }
})
