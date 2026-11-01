import { test, expect } from '@playwright/test'
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { randomUUID } from 'node:crypto'
import { login, authenticatedGet, readState } from '../../tests/integration/support'

test('API授权的普通工作人员在改期结束后应仍可从活动页打开改期历史', async ({ page }) => {
  const state = readState()
  expect(state.reviewActivityId).toBeTruthy()
  const path = `/activities/${state.reviewActivityId}`
  await login(page, 'staff_a', path)
  await expect(page.getByTestId('current-version')).toBeVisible()
  const currentResponse = await authenticatedGet(page, path)
  const historyResponse = await authenticatedGet(page, `${path}/changes`)
  expect(currentResponse.status()).toBe(200)
  expect(historyResponse.status()).toBe(200)
  const current = (await currentResponse.json()).data
  const changes = (await historyResponse.json()).data
  expect(changes.some((c: { id: string }) => c.id === state.reviewChangeId)).toBe(true)
  expect(current.activeChangeId).toBeNull()
  expect(current.input.staffUserIds).toEqual([])
  const evidence = resolve('../artifacts/test-results/ui-professional-review-2026-11-01')
  mkdirSync(evidence, { recursive: true })
  writeFileSync(resolve(evidence, 'staff-history-observed.json'), JSON.stringify({
    activityId: current.id,
    activityStatus: current.status,
    staffUserIdsReturned: current.input.staffUserIds,
    activeChangeId: current.activeChangeId,
    historyHttpStatus: historyResponse.status(),
    historyCount: changes.length,
    requestedChangeFound: true,
    historyTabCount: await page.getByRole('tab', { name: '改期记录', exact: true }).count(),
  }, null, 2))
  const shot = resolve('../artifacts/screenshots/ui-professional-review-2026-11-01')
  mkdirSync(shot, { recursive: true })
  await page.screenshot({ path: resolve(shot, 'staff-history-missing.png'), fullPage: true })
  await expect(page.getByRole('tab', { name: '改期记录', exact: true })).toBeVisible()
})

test('真实资源刷新应将当前选中资源的资格开关更新为服务器最新值', async ({ page }) => {
  await login(page, 'resource_admin', '/resources/manage')
  const token = await page.evaluate(() => sessionStorage.getItem('campus-sched.session'))
  const create = await page.request.post('/api/v1/resources', {
    headers: { Authorization: `Bearer ${token}`, 'Idempotency-Key': randomUUID() },
    data: { kind: 'VENUE', name: `UI复审资格 ${randomUUID().slice(0, 8)}`, category: 'VENUE',
      capacity: 40, quantity: 1, openFrom: '08:00', openTo: '22:00', allowTeacher: true, allowStudent: true },
  })
  expect(create.ok()).toBe(true)
  const resource = (await create.json()).data
  const refresh = page.getByRole('button', { name: '刷新资源与权限', exact: true })
  await expect(refresh).toBeEnabled()
  await refresh.click()
  await page.getByTestId(`resource-select-${resource.id}`).click()
  const teacher = page.getByTestId('eligibility-teacher').locator('input')
  const student = page.getByTestId('eligibility-student').locator('input')
  await expect(teacher).toBeChecked()
  await expect(student).toBeChecked()
  const changed = await page.request.patch(`/api/v1/resources/${resource.id}/eligibility`, {
    headers: { Authorization: `Bearer ${token}`, 'Idempotency-Key': randomUUID() },
    data: { allowTeacher: false, allowStudent: false },
  })
  expect(changed.status()).toBe(200)
  const responsePromise = page.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/resources' && r.request().method() === 'GET')
  await refresh.click()
  const response = await responsePromise
  expect(response.status()).toBe(200)
  const latest = (await response.json()).data.find((r: { id: string }) => r.id === resource.id)
  expect(latest.allowTeacher).toBe(false)
  expect(latest.allowStudent).toBe(false)
  await expect(refresh).toBeEnabled()
  const evidence = resolve('../artifacts/test-results/ui-professional-review-2026-11-01')
  writeFileSync(resolve(evidence, 'eligibility-observed.json'), JSON.stringify({ resourceId: resource.id,
    serverTeacher: latest.allowTeacher, serverStudent: latest.allowStudent,
    uiTeacher: await teacher.isChecked(), uiStudent: await student.isChecked() }, null, 2))
  const shot = resolve('../artifacts/screenshots/ui-professional-review-2026-11-01')
  mkdirSync(shot, { recursive: true })
  await page.screenshot({ path: resolve(shot, 'eligibility-refresh-stale.png'), fullPage: true })
  await expect.soft(teacher).not.toBeChecked()
  await expect.soft(student).not.toBeChecked()
})
