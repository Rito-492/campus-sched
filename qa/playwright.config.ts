import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright 配置（QA-01 骨架；用例由 QA-03 交付）。
 *
 * 约定（03 契约 §3）：
 * - E2E 使用独立 Compose 测试项目与测试数据库，BASE_URL 指向该环境（如 http://localhost:8089），
 *   后端与数据库必须真实，禁止接真实部署库；
 * - 默认 Chromium、1366×768，必要页面另测 1920×1080 与 390×844（项目 desktop-1920 / mobile-390）；
 * - 失败截图存 artifacts/screenshots/，由 reporter 配置落盘后归档。
 */
const baseURL = process.env.BASE_URL ?? 'http://localhost:8089';

export default defineConfig({
  testDir: './tests',
  outputDir: '../artifacts/test-results/e2e-raw',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  // 业务状态等待一律用 expect 轮询（QA-03 规则），不用长固定 sleep
  fullyParallel: false,
  retries: 0,
  reporter: [
    ['list'],
    ['json', { outputFile: '../artifacts/test-results/e2e-results.json' }],
  ],
  use: {
    baseURL,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    actionTimeout: 10_000,
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
  },
  projects: [
    {
      name: 'desktop',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1366, height: 768 } },
    },
    {
      name: 'desktop-1920',
      testMatch: /ui-layout\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1920, height: 1080 } },
    },
    {
      name: 'mobile-390',
      testMatch: /ui-layout\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 390, height: 844 } },
    },
  ],
});
