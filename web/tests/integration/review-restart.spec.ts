import { test, expect } from '@playwright/test'
import { authenticatedGet, login, readState, screenshots } from './support'

// 先运行 test:review，再实际重启后端（禁止重新播种），最后单独运行本文件。
test('实际重启后改期生效版本、安排、报名、日历和通知仍可读取', async ({
  page,
}) => {
  const state = readState()
  expect(state.reviewActivityId).toBeTruthy()
  expect(state.reviewChangeId).toBeTruthy()
  const activityPath = `/activities/${state.reviewActivityId}`
  await login(page, 'applicant_a', `/changes/${state.reviewChangeId}`)
  await expect(page.getByTestId('change-status')).toContainText('已生效')
  await expect(page.getByTestId('current-version')).toContainText(
    `有效版本 ${state.reviewVersion}`,
  )
  await expect(page.getByTestId('resource-calendar')).toBeVisible()
  await expect(page.locator('.notification')).not.toHaveCount(0)
  await screenshots(page, 'real-change-after-restart')
  const response = await authenticatedGet(page, activityPath)
  expect(response.ok()).toBe(true)
  const current = (await response.json()).data
  expect(current.input.plan).toEqual(state.reviewPlan)
  expect(current.effectiveVersionNo).toBe(state.reviewVersion)
  expect(current.registrationCount).toBe(1)
  expect(current.activeChangeId).toBeNull()
  const registrations = await authenticatedGet(
    page,
    `${activityPath}/registrations`,
  )
  expect(registrations.ok()).toBe(true)
  expect((await registrations.json()).data).toEqual([
    expect.objectContaining({
      status: 'ACTIVE',
      versionNo: state.reviewVersion,
    }),
  ])
  await login(page, 'student_b', activityPath)
  await expect(page.getByTestId('registration-submit')).toHaveText('退出报名')
  await expect(page.getByTestId('registration-submit')).toBeEnabled()
  await page.reload()
  await expect(page.getByTestId('current-version')).toHaveText(
    String(state.reviewVersion),
  )
})
