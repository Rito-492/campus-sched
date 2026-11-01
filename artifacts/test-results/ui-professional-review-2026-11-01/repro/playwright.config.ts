import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: '.',
  testMatch: 'staff-history.spec.ts',
  outputDir: '../professional-review-output',
  workers: 1,
  retries: 0,
  timeout: 30000,
  expect: { timeout: 5000 },
  use: {
    baseURL: 'http://127.0.0.1:5173', channel: 'msedge', headless: true,
    viewport: { width: 1366, height: 768 },
    trace: 'off', video: 'off', screenshot: 'off',
  },
})
