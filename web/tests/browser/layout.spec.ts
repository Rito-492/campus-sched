import { test, expect } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
const evidence = resolve(
  process.env.UI_EVIDENCE_DIR || '../artifacts/screenshots/ui-2026-10-31',
)
mkdirSync(evidence, { recursive: true })
// 本套仅验证真实登录外壳和显著标注的开发组件原型，不是业务E2E，不伪造API成功。
for (const [width, height] of [
  [1366, 768],
  [1920, 1080],
  [390, 844],
]) {
  test(`登录及开发原型布局 ${width}x${height}`, async ({ page }) => {
    await page.setViewportSize({ width, height })
    const errors: string[] = []
    page.on('pageerror', (error) => errors.push(error.message))
    await page.goto('/login')
    await expect(page.getByTestId('login-submit')).toBeDisabled()
    await expect(page.locator('h2')).toHaveText('欢迎回来')
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true)
    await page.screenshot({
      path: resolve(evidence, `login-${width}x${height}.png`),
      fullPage: true,
    })
    await page.goto('/__prototype')
    await expect(
      page.getByText('演示数据 / 未连接后端', { exact: true }),
    ).toBeVisible()
    await page.getByTestId('alternative-0').click()
    await expect(
      page.getByText('已选择原型方案 1，仅展示影响，未预约。'),
    ).toBeVisible()
    await expect(page.getByTestId('change-status')).toHaveText(
      '等待工作人员确认',
    )
    expect(
      await page
        .locator('.alternative-card')
        .first()
        .evaluate((el) => Number.parseFloat(getComputedStyle(el).lineHeight)),
    ).toBeGreaterThan(14)
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true)
    await page.evaluate(() => window.scrollTo(0, 0))
    await page.screenshot({
      path: resolve(evidence, `prototype-${width}x${height}.png`),
      fullPage: true,
    })
    expect(errors).toEqual([])
  })
}
test('受保护页面无会话跳登录，登录错误不会伪造成功', async ({ page }) => {
  await page.goto('/activities/new')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await page.getByTestId('login-username').fill('not-a-real-user')
  await page.getByTestId('login-password').fill('not-a-real-password')
  await page.getByTestId('login-submit').click()
  await expect(page.getByTestId('api-error')).toBeVisible({ timeout: 20000 })
  await expect(page).toHaveURL(/\/login/)
})
