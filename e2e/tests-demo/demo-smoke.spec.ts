import { expect, test } from '@playwright/test'
import * as fs from 'node:fs'
import * as path from 'node:path'

const SHOTS_DIR = path.resolve(__dirname, '../../docs/screenshots')
fs.mkdirSync(SHOTS_DIR, { recursive: true })

const stamp = Date.now()
const plan = { name: `Studio ${stamp}`, amount: '79,90' }
const customer = { name: `Demo Customer ${stamp}`, email: `demo.${stamp}@example.test`, document: '529.982.247-25' }

test('demo mode: enter demo, KPIs, create a plan, simulate a payment, subscription ACTIVE', async ({ page }) => {
  await test.step('banner and demo entry on the login screen', async () => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/login/)
    await expect(page.getByTestId('demo-banner')).toBeVisible()
    await expect(page.getByTestId('demo-banner')).toContainText('Demo mode: data is mocked in your browser')
    await expect(page.getByTestId('demo-email')).toHaveText('demo@tenantpay.dev')
    // The password is generated per browser when the mock seeds its data, never a literal in the source.
    await expect(page.getByTestId('demo-password')).toHaveText(/^[0-9a-f]{16}$/)
    await page.getByTestId('demo-enter').click()
    await expect(page).toHaveURL(/\/dashboard$/)
    await expect(page.getByTestId('current-tenant')).toHaveText('Acme Gym')
  })

  await test.step('dashboard KPIs render with seeded data', async () => {
    await expect(page.getByTestId('kpi-mrr')).toContainText('R$')
    await expect(page.getByTestId('dashboard-active')).not.toHaveText('0')
    await expect(page.getByTestId('kpi-overdue')).toHaveText('1')
    await expect(page.getByTestId('dashboard-received')).toContainText('R$')
    await expect(page.getByTestId('onboarding-checklist')).toBeVisible()
    await expect(page.getByTestId('demo-banner')).toBeVisible()
  })

  await test.step('create a plan', async () => {
    await page.getByTestId('nav-plans').click()
    await page.getByTestId('plan-name').fill(plan.name)
    await page.getByTestId('plan-amount').fill(plan.amount)
    await page.getByTestId('plan-interval').selectOption('MONTHLY')
    await page.getByTestId('plan-submit').click()
    const row = page.getByTestId('plan-row').filter({ hasText: plan.name })
    await expect(row).toHaveCount(1)
    await expect(row).toContainText('79,90')
  })

  await test.step('add a customer and subscribe it to the new plan', async () => {
    await page.getByTestId('nav-customers').click()
    await page.getByTestId('customer-name').fill(customer.name)
    await page.getByTestId('customer-email').fill(customer.email)
    await page.getByTestId('customer-document').fill(customer.document)
    await page.getByTestId('customer-submit').click()
    const row = page.getByTestId('customer-row').filter({ hasText: customer.name })
    await expect(row).toHaveCount(1)
    await row.getByTestId('subscribe-open').click()
    const select = row.getByTestId('subscribe-plan-select')
    const value = await select.locator('option', { hasText: plan.name }).getAttribute('value')
    await select.selectOption(value as string)
    await row.getByTestId('subscribe-submit').click()
    await expect(row.getByTestId('subscribe-message')).toContainText('Subscribed to')
  })

  await test.step('simulate a payment: the subscription becomes ACTIVE', async () => {
    await page.getByTestId('nav-subscriptions').click()
    const row = page.getByTestId('subscription-row').filter({ hasText: customer.name })
    await expect(row.getByTestId('subscription-status')).toHaveText('PENDING')
    await row.getByTestId('simulate-payment').click()
    await expect(row.getByTestId('subscription-status')).toHaveText('ACTIVE', { timeout: 15_000 })
    await expect(page.getByTestId('subscriptions-notice')).toContainText('ACTIVE')
    // Let transient toasts auto dismiss so they do not cover the screenshot.
    await expect(page.getByTestId('toast')).toHaveCount(0, { timeout: 10_000 })
    await page.screenshot({ path: path.join(SHOTS_DIR, '09-demo-mode.png'), fullPage: true })
  })

  await test.step('charges and webhooks pages load', async () => {
    await page.getByTestId('nav-charges').click()
    await expect(page.getByTestId('charge-row').first()).toBeVisible()
    await page.getByTestId('nav-webhooks').click()
    await expect(page.getByTestId('webhook-row').first()).toBeVisible()
  })
})
