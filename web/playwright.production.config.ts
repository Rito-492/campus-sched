import { defineConfig } from '@playwright/test'
import integration from './playwright.integration.config'

export default defineConfig({
  ...integration,
  testDir: './tests/production',
  outputDir: './test-results/production',
  use: { ...integration.use, baseURL: 'http://127.0.0.1:4173' },
  webServer: {
    command:
      'node node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 4173 --strictPort',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: process.env.UI_REUSE_PRODUCTION === 'true',
  },
})
