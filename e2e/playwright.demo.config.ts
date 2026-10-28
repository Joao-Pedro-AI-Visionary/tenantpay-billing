/*
 * Demo mode smoke suite (no backend needed).
 *
 * Local run (from e2e/):  npm run test:demo
 *   Builds the panel with `npm run build:demo` (VITE_DEMO_MODE=true, in-browser mock API) and serves it
 *   with `vite preview` on http://localhost:4173.
 *
 * Against a deployed demo (for example the Vercel preview):
 *   DEMO_BASE_URL=https://your-demo.vercel.app npm run test:demo
 *   When DEMO_BASE_URL is set no local server is started.
 *
 * Saves docs/screenshots/09-demo-mode.png.
 */
import { defineConfig, devices } from '@playwright/test'

const externalUrl = process.env.DEMO_BASE_URL
const baseURL = externalUrl ?? 'http://localhost:4173'

export default defineConfig({
  testDir: './tests-demo',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  forbidOnly: !!process.env.CI,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report-demo' }]],
  use: {
    baseURL,
    viewport: { width: 1280, height: 800 },
    // Transitions finish instantly so screenshots never catch a half-animated state.
    reducedMotion: 'reduce',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    actionTimeout: 10_000,
    navigationTimeout: 30_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 }, reducedMotion: 'reduce' },
    },
  ],
  webServer: externalUrl
    ? undefined
    : {
        command: 'npm --prefix ../frontend run build:demo && npm --prefix ../frontend run preview -- --port 4173 --strictPort',
        url: 'http://localhost:4173',
        reuseExistingServer: !process.env.CI,
        timeout: 180_000,
        stdout: 'ignore',
        stderr: 'pipe',
      },
})
