import { test, expect } from '@playwright/test'
import { authenticatedGet, login, readState } from './support'

// 必须先运行real-service，随后由操作者重启真实服务（关闭重新播种）再单独运行本文件。
test('服务重启后保留草稿、资源资格、完整授权和停用状态', async ({ page }) => {
  const state = readState()
  await login(page, 'applicant_b', `/activities/${state.activityId}`)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(
    state.activityTitle,
  )
  const activity = (
    await (
      await authenticatedGet(page, `/activities/${state.activityId}`)
    ).json()
  ).data
  expect(activity.lockVersion).toBe(state.lockVersion)
  expect(activity.status).toBe('DRAFT')
  expect(activity.input.plan.venueId).toBe(state.activityVenueId)
  await page.getByRole('button', { name: '退出', exact: true }).click()
  await login(page, 'resource_admin', '/resources/manage')
  await page.getByTestId(`resource-select-${state.resourceId}`).click()
  await expect(
    page.getByTestId('eligibility-teacher').locator('input'),
  ).toBeChecked()
  await expect(
    page.getByTestId('eligibility-student').locator('input'),
  ).toBeChecked()
  await page.getByRole('button', { name: '退出', exact: true }).click()
  await login(page, 'account_admin', '/admin/users')
  await expect(page.getByTestId(`user-${state.userId}`)).toContainText('教师')
  await expect(page.getByTestId(`user-${state.userId}`)).toContainText('已停用')
})
