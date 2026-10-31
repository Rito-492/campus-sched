import { test, expect } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
const evidence = resolve(
  process.env.UI_EVIDENCE_DIR ||
    '../artifacts/screenshots/ui-workspace-preview-2026-10-31',
)
mkdirSync(evidence, { recursive: true })

for (const [width, height] of [
  [1366, 768],
  [1920, 1080],
  [390, 844],
]) {
  test(`登录后外观预览与真实会话隔离 ${width}x${height}`, async ({ page }) => {
    await page.setViewportSize({ width, height })
    const apiCalls: string[] = [],
      errors: string[] = []
    page.on('request', (request) => {
      if (new URL(request.url()).pathname.startsWith('/api/'))
        apiCalls.push(request.url())
    })
    page.on('pageerror', (error) => errors.push(error.message))
    await page.goto('/__prototype/workspace')
    await expect(page.getByTestId('workspace-preview-banner')).toContainText(
      '演示数据 / 未连接后端',
    )
    await expect(
      page.getByTestId('activity-list').locator('article'),
    ).toHaveCount(4)
    await expect(
      page.getByRole('navigation').getByText('审批与待办'),
    ).toHaveCount(0)
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true)
    await page.screenshot({
      path: resolve(evidence, `workspace-${width}x${height}.png`),
      fullPage: true,
    })
    await page
      .getByRole('link', { name: '校园技术分享会', exact: true })
      .click()
    await expect(
      page.getByRole('button', { name: '预览模式不可报名' }),
    ).toBeDisabled()
    await page.getByTestId('preview-role').selectOption('accounts')
    await expect(
      page.getByRole('navigation').getByText('账号与授权'),
    ).toHaveCount(1)
    await expect(
      page.getByRole('navigation').getByText('资源管理'),
    ).toHaveCount(0)
    expect(apiCalls).toEqual([])
    expect(
      await page.evaluate(() => sessionStorage.getItem('campus-sched.session')),
    ).toBeNull()
    expect(errors).toEqual([])
    await page.goto('/activities')
    await expect(page).toHaveURL(/\/login\?redirect=/)
  })
}
