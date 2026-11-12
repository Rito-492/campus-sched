import { defineConfig } from '@playwright/test'
import { resolve } from 'node:path'
import base from './playwright.config'

// 本次 PM 本机复验；用例保持 QA 原样，认证轨迹不落盘。
export default defineConfig({
  ...base,
  testDir: resolve('tests'),
  outputDir: resolve('../../qa-e2e-output'),
  workers: 1,
  reporter: [
    ['list'],
    ['json', { outputFile: resolve('../../qa-e2e-results.json') }],
  ],
  use: { ...base.use, channel: 'msedge', trace: 'off', video: 'off', screenshot: 'off' },
})
