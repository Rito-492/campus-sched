import { test, expect } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { login } from '../integration/support'

// 验证实际dist里的按需组件、样式和中文配置；认证及查询使用真实后端。
for (const [width, height] of [
  [1366, 768],
  [1920, 1080],
  [390, 844],
]) {
  test(`生产组件、中文确认和主题在路由切换后完整 ${width}x${height}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height })
    const errors: string[] = [],
      scripts: string[] = [],
      mutations: string[] = []
    page.on('pageerror', (error) => errors.push(error.message))
    page.on('console', (message) => {
      if (
        message.type() === 'error' ||
        /Failed to resolve component/.test(message.text())
      )
        errors.push(`${message.text()} (${message.location().url})`)
    })
    page.on('request', (request) => {
      const path = new URL(request.url()).pathname
      if (request.resourceType() === 'script') scripts.push(path)
      if (
        path.startsWith('/api/') &&
        !path.startsWith('/api/v1/auth/') &&
        request.method() !== 'GET'
      )
        mutations.push(path)
    })
    await page.goto('/__prototype/workspace')
    await expect(
      page.getByRole('heading', { name: '页面不存在', exact: true }),
    ).toBeVisible()
    await expect(page.getByTestId('workspace-preview-banner')).toHaveCount(0)
    await login(page, 'resource_admin', '/resources/manage')
    await expect(
      page.getByTestId('eligibility-teacher').locator('.el-checkbox__inner'),
    ).toBeVisible()
    await expect(
      page.getByTestId('eligibility-teacher').locator('input'),
    ).toBeEnabled()
    await page.getByTestId('eligibility-save').click()
    const confirmation = page.locator('.el-popconfirm:visible')
    await expect(
      confirmation.getByRole('button', { name: '确定', exact: true }),
    ).toHaveCSS('background-color', 'rgb(23, 107, 104)')
    await expect(
      confirmation.getByRole('button', { name: '取消', exact: true }),
    ).toBeVisible()
    await confirmation
      .getByRole('button', { name: '取消', exact: true })
      .click()
    await expect(confirmation).toHaveCount(0)

    await login(page, 'account_admin', '/admin/users')
    await page.getByTestId('user-create-open').click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog.locator('.el-dialog')).toHaveCSS(
      'background-color',
      'rgb(255, 255, 255)',
    )
    await dialog.getByTestId('user-organization').click()
    await page.getByRole('option', { name: '软件学院', exact: true }).click()
    await expect(page.getByRole('listbox')).toBeHidden()
    await dialog.getByTestId('user-save').scrollIntoViewIfNeeded()
    await expect(dialog.getByTestId('user-save')).toBeVisible()
    await expect(dialog.getByTestId('user-save')).toHaveCSS(
      'background-color',
      'rgb(23, 107, 104)',
    )
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true)
    if (process.env.UI_EVIDENCE_DIR) {
      mkdirSync(process.env.UI_EVIDENCE_DIR, { recursive: true })
      await page.screenshot({
        animations: 'disabled',
        path: resolve(
          process.env.UI_EVIDENCE_DIR,
          `production-dialog-${width}x${height}.png`,
        ),
      })
    }
    await dialog.getByRole('button', { name: '取消', exact: true }).click()
    if (width < 1024)
      await page
        .getByRole('button', { name: '展开或收起导航', exact: true })
        .click()
    await page.getByRole('link', { name: '资源日历', exact: true }).click()
    await expect(page.getByTestId('calendar-nav')).toBeVisible()
    expect(
      await page.evaluate(() =>
        getComputedStyle(document.documentElement)
          .getPropertyValue('--el-color-primary')
          .trim(),
      ),
    ).toBe('#176b68')
    expect(scripts.some((path) => path.startsWith('/assets/'))).toBe(true)
    expect(
      scripts.filter(
        (path) => path.startsWith('/src/') || path.includes('@vite/client'),
      ),
    ).toEqual([])
    expect(errors).toEqual([])
    expect(mutations).toEqual([])
  })
}
