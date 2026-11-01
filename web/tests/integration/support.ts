import { expect, type Page, type Response } from '@playwright/test'
import { mkdirSync, readFileSync, existsSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
export const password = process.env.UI_DEMO_PASSWORD!
export const evidence = resolve(
  process.env.UI_EVIDENCE_DIR ||
    '../artifacts/screenshots/ui-integration-2026-10-31',
)
const statePath = resolve('.integration-runtime/smoke-state.json')
mkdirSync(evidence, { recursive: true })
mkdirSync(resolve('.integration-runtime'), { recursive: true })
export function saveState(patch: Record<string, unknown>) {
  const state = existsSync(statePath)
    ? JSON.parse(readFileSync(statePath, 'utf8').replace(/^\uFEFF/, ''))
    : {}
  writeFileSync(statePath, JSON.stringify({ ...state, ...patch }, null, 2))
}
export function readState() {
  return JSON.parse(readFileSync(statePath, 'utf8').replace(/^\uFEFF/, ''))
}
export async function login(page: Page, username: string, target: string) {
  await page.goto(`/login?redirect=${encodeURIComponent(target)}`)
  await page.getByTestId('login-username').fill(username)
  await page.getByTestId('login-password').fill(password)
  await page.getByTestId('login-submit').click()
  await expect(page).toHaveURL(
    new RegExp(`${target.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`),
  )
  await expect(page.locator('.account')).toBeVisible()
}
export async function authenticatedGet(page: Page, path: string) {
  const token = await page.evaluate(() =>
    sessionStorage.getItem('campus-sched.session'),
  )
  return page.request.get(`/api/v1${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
}
export async function successful(response: Response) {
  const body = await response.json()
  expect(
    response.ok(),
    JSON.stringify({
      path: new URL(response.url()).pathname,
      status: response.status(),
      error: body.error,
      requestId: body.requestId,
    }),
  ).toBe(true)
  return body.data
}
export async function screenshots(page: Page, name: string) {
  await page.waitForLoadState('networkidle')
  for (const [width, height] of [
    [1366, 768],
    [1920, 1080],
    [390, 844],
  ]) {
    await page.setViewportSize({ width, height })
    await expect
      .poll(() =>
        page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
      )
      .toBe(true)
    await page.screenshot({
      path: resolve(evidence, `${name}-${width}x${height}.png`),
      fullPage: true,
    })
  }
  await page.setViewportSize({ width: 1366, height: 768 })
}
