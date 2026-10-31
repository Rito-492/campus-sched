import { test, expect } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
const evidence = resolve(
  process.env.UI_EVIDENCE_DIR ||
    '../artifacts/screenshots/ui-completion-2026-10-31',
)
mkdirSync(evidence, { recursive: true })
// 仅DEV组件外观与交互检查，既不拦截API制造成功，也不计作业务E2E。
for (const [width, height] of [
  [1366, 768],
  [1920, 1080],
  [390, 844],
]) {
  test(`资源、日历、改期、活动与通知开发预览 ${width}x${height}`, async ({
    page,
  }) => {
    test.setTimeout(60000)
    await page.setViewportSize({ width, height })
    const errors: string[] = [],
      requests: string[] = []
    page.on('pageerror', (e) => errors.push(e.message))
    page.on('request', (r) => {
      if (new URL(r.url()).pathname.startsWith('/api/')) requests.push(r.url())
    })
    const open = async (path: string, persona = 'resource') => {
      await page.goto(
        `/__prototype/workspace?persona=${persona}&page=${encodeURIComponent(path)}`,
      )
      await expect(page.getByTestId('workspace-preview-banner')).toContainText(
        '未连接后端',
      )
    }
    const screenshot = async (name: string) => {
      await page.evaluate(() => window.scrollTo(0, 0))
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      ).toBe(true)
      await page.screenshot({
        path: resolve(evidence, `${name}-${width}x${height}.png`),
        fullPage: true,
      })
    }
    await open('/resources/manage')
    await expect(page.getByTestId('resource-management')).toBeVisible()
    await expect(page.getByTestId('eligibility-save')).toBeDisabled()
    await page.getByTestId('resource-new').click()
    await expect(page.getByTestId('resource-create')).toBeDisabled()
    await screenshot('resources')
    await open('/resources/calendar')
    await page.getByTestId('capacity-details').locator('summary').click()
    await expect(page.getByTestId('capacity-details')).toContainText('已用 3/4')
    await expect(page.getByTestId('capacity-details')).toContainText(
      '占用超过当前可用量',
    )
    if (width > 767)
      await expect(page.locator('.hour-row')).toContainText('08:30')
    await screenshot('calendar')
    await open('/activities/preview-3/change', 'applicant')
    await expect(page.getByTestId('change-create')).toBeDisabled()
    await expect(page.locator('.alternative-card')).toHaveCount(5)
    await page
      .locator('.alternative-card')
      .nth(0)
      .locator('label.el-checkbox')
      .click()
    await page
      .locator('.alternative-card')
      .nth(1)
      .locator('label.el-checkbox')
      .click()
    await expect(page.getByRole('table')).toBeVisible()
    await screenshot('alternatives-impact')
    await page.getByTestId('preview-candidates').selectOption('0')
    await expect(
      page.getByText('当前搜索范围内无可行方案，请调整时间或资源。'),
    ).toBeVisible()
    await open('/changes/preview-change')
    await expect(page.getByTestId('change-resource-approve')).toBeDisabled()
    await page.getByTestId('preview-change-status').selectOption('CONFLICTED')
    await expect(page.getByTestId('conflict-list')).toContainText(
      '此时段场地已被其他活动占用',
    )
    await expect(
      page.getByText('原安排未被本次改期修改，请继续关注原资源风险。'),
    ).toBeVisible()
    await screenshot('change-conflicted')
    await page.getByTestId('preview-change-status').selectOption('APPLIED')
    await expect(page.getByTestId('change-status')).toHaveText('改期已生效')
    await screenshot('change-applied-sample')
    await open('/activities/preview-3', 'student')
    await screenshot('activity-detail')
    await open('/notifications', 'student')
    await screenshot('notifications')
    expect(requests).toEqual([])
    expect(errors).toEqual([])
    expect(
      await page.evaluate(() => sessionStorage.getItem('campus-sched.session')),
    ).toBeNull()
  })
}
