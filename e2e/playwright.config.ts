/*
 * TenantPay Billing end to end suite.
 *
 * How to run (from the repository root):
 *   1. Start the stack:            docker compose up -d --build
 *      (panel on http://localhost:15173, API on http://localhost:18080, dev tools enabled)
 *   2. Install deps and browser:   cd e2e && npm ci && npx playwright install chromium
 *   3. Run the suite:              npx playwright test
 *   4. Open the HTML report:       npx playwright show-report
 *
 * Environment variables (all optional):
 *   E2E_BASE_URL       panel URL              default http://localhost:15173
 *                      (use http://localhost:5173 when running `npm run dev` in frontend/)
 *   E2E_API_URL        API URL for direct calls default http://localhost:18080
 *   E2E_WEBHOOK_TOKEN  asaas-access-token      default dev-webhook-token
 *
 * Screenshots used as evidence are written to the repository docs/screenshots (01 to 08).
 */
import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:15173'

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  forbidOnly: !!process.env.CI,
  timeout: 90_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    viewport: { width: 1280, height: 800 },
    // Transitions finish instantly so screenshots never catch a half-animated state.
    reducedMotion: 'reduce',
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 }, reducedMotion: 'reduce' },
    },
  ],
})
