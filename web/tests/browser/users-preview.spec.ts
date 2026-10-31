import { test, expect } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
const evidence = resolve(
  process.env.UI_EVIDENCE_DIR ||
    '../artifacts/screenshots/ui-accounts-2026-10-31',
)
mkdirSync(evidence, { recursive: true })
for (const [width, height] of [
  [1366, 768],
  [1920, 1080],
  [390, 844],
]) {
  test(`账号管理与完整授权开发预览 ${width}x${height}`, async ({ page }) => {
    await page.setViewportSize({ width, height })
    const apiCalls: string[] = [],
      errors: string[] = []
    page.on('request', (request) => {
      if (new URL(request.url()).pathname.startsWith('/api/'))
        apiCalls.push(request.url())
    })
    page.on('pageerror', (error) => errors.push(error.message))
    await page.goto(
      '/__prototype/workspace?persona=accounts&page=%2Fadmin%2Fusers',
    )
    await expect(
      page.getByRole('heading', { name: '账号与授权', exact: true }),
    ).toBeVisible()
    await expect(page.getByTestId('users-list').locator('article')).toHaveCount(
      5,
    )
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true)
    await page.screenshot({
      path: resolve(evidence, `accounts-${width}x${height}.png`),
      fullPage: true,
    })
    await expect(
      page.getByTestId('user-toggle-preview-applicant'),
    ).toBeDisabled()
    await page.getByTestId('user-authorize-preview-accounts').click()
    const dialog = page.getByRole('dialog')
    await expect(
      dialog.getByText('正在编辑本人账号。', { exact: false }),
    ).toBeVisible()
    await expect(dialog.getByTestId('permission-USER_MANAGE')).toBeChecked()
    await dialog.getByTestId('role-ADMIN').uncheck()
    await expect(dialog.getByTestId('permission-USER_MANAGE')).toHaveCount(0)
    await dialog.getByTestId('role-ADMIN').check()
    await expect(dialog.getByTestId('permission-USER_MANAGE')).not.toBeChecked()
    await dialog.getByTestId('user-save').scrollIntoViewIfNeeded()
    await expect(dialog.getByTestId('user-save')).toBeVisible()
    await expect(dialog.getByTestId('user-save')).toBeDisabled()
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true)
    await page.screenshot({
      path: resolve(evidence, `authorization-${width}x${height}.png`),
      fullPage: false,
    })
    await dialog.getByRole('button', { name: '取消', exact: true }).click()
    await page.getByTestId('user-create-open').click()
    await expect(
      page.getByRole('dialog').getByTestId('user-password'),
    ).toHaveValue('')
    await expect(
      page.getByRole('dialog').getByTestId('role-APPLICANT'),
    ).toBeChecked()
    await expect(
      page.getByRole('dialog').getByTestId('role-APPROVER'),
    ).not.toBeChecked()
    await expect(
      page.getByRole('dialog').getByTestId('user-save'),
    ).toBeDisabled()
    expect(apiCalls).toEqual([])
    expect(errors).toEqual([])
    expect(
      await page.evaluate(() => sessionStorage.getItem('campus-sched.session')),
    ).toBeNull()
  })
}
