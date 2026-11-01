import { defineConfig } from '@playwright/test'

// 仅运行真实服务；缺密码或未启动后端即失败，不回退到fixture。
if (!process.env.UI_DEMO_PASSWORD)
  throw new Error('请设置隔离测试环境的 UI_DEMO_PASSWORD')
export default defineConfig({
  testDir: './tests/integration',
  outputDir: './test-results/integration',
  workers: 1,
  retries: 0,
  timeout: 60000,
  expect: { timeout: 15000 },
  reporter: 'list',
  use: {
    baseURL: process.env.UI_BASE_URL || 'http://127.0.0.1:5173',
    channel: process.env.PLAYWRIGHT_CHANNEL || undefined,
    headless: true,
    viewport: { width: 1366, height: 768 },
    // 避免报告携带认证请求体或Bearer；证据仅包含页面截图和非敏感业务ID。
    trace: 'off',
    video: 'off',
    screenshot: 'off',
  },
})
