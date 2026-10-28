import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import * as fs from 'node:fs'
import * as path from 'node:path'

const API_URL = process.env.E2E_API_URL ?? 'http://localhost:18080'
const WEBHOOK_TOKEN = process.env.E2E_WEBHOOK_TOKEN ?? 'dev-webhook-token'
const TOKEN_KEY = 'tenantpay.token'
// Repository level docs/screenshots (referenced by the README).
const SHOTS_DIR = path.resolve(__dirname, '../../docs/screenshots')

fs.mkdirSync(SHOTS_DIR, { recursive: true })

/** Unique data per run so the suite can be executed many times against the same database. */
const stamp = Date.now()
const tenant = {
  name: `Acme Gym ${stamp}`,
  email: `owner.${stamp}@acme.test`,
  password: 'Sup3r-secret-pass',
}
const plan = { name: `Pro ${stamp}`, description: 'Unlimited classes, billed monthly', amount: '49,90', cents: 4990 }
const customer = { name: `Jane Doe ${stamp}`, email: `jane.${stamp}@example.test`, document: validCpf(stamp) }
const customer2 = { name: `John Roe ${stamp}`, email: `john.${stamp}@example.test`, document: validCpf(stamp + 7) }

/** Set by the first test; the bonus test falls back to an API created tenant when run alone. */
let tenantReady = false

/** Waits for transient toasts to auto dismiss so they do not cover the screenshot. */
async function dismissToasts(page: Page): Promise<void> {
  await expect(page.getByTestId('toast')).toHaveCount(0, { timeout: 10_000 })
}

async function shot(page: Page, file: string): Promise<void> {
  await dismissToasts(page)
  await page.screenshot({ path: path.join(SHOTS_DIR, file), fullPage: true })
}

/** Builds a CPF with valid check digits from a numeric seed (sample data only). */
function validCpf(seed: number): string {
  const base = String(seed).slice(-9).padStart(9, '1').split('').map(Number)
  if (base.every((d) => d === base[0])) base[8] = (base[8]! + 1) % 10
  const digit = (nums: number[]): number => {
    const factor = nums.length + 1
    const sum = nums.reduce((acc, n, i) => acc + n * (factor - i), 0)
    const rest = sum % 11
    return rest < 2 ? 0 : 11 - rest
  }
  const d1 = digit(base)
  const d2 = digit([...base, d1])
  return [...base, d1, d2].join('')
}

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

async function readToken(page: Page): Promise<string> {
  const token = await page.evaluate((key) => window.localStorage.getItem(key), TOKEN_KEY)
  expect(token, 'JWT stored in localStorage').toBeTruthy()
  return token as string
}

async function apiContext(
  playwright: { request: { newContext: (o: object) => Promise<APIRequestContext> } },
  token?: string,
) {
  return playwright.request.newContext({
    baseURL: API_URL,
    extraHTTPHeaders: token ? { Authorization: `Bearer ${token}` } : {},
  })
}

async function loginThroughUi(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByTestId('login-email').fill(tenant.email)
  await page.getByTestId('login-password').fill(tenant.password)
  await page.getByTestId('login-submit').click()
  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByTestId('current-tenant')).toHaveText(tenant.name)
}

/** Creates the customer in the UI and subscribes it to the plan from the customer row. */
async function createCustomerAndSubscribe(page: Page, c: typeof customer): Promise<void> {
  await page.getByTestId('nav-customers').click()
  await expect(page).toHaveURL(/\/customers$/)
  await page.getByTestId('customer-name').fill(c.name)
  await page.getByTestId('customer-email').fill(c.email)
  await page.getByTestId('customer-document').fill(c.document)
  await page.getByTestId('customer-submit').click()

  const row = page.getByTestId('customer-row').filter({ hasText: c.name })
  await expect(row).toHaveCount(1)
  // The subscribe form is progressively disclosed inside the customer row.
  await row.getByTestId('subscribe-open').click()
  const select = row.getByTestId('subscribe-plan-select')
  const planValue = await select.locator('option', { hasText: plan.name }).getAttribute('value')
  expect(planValue, 'plan option present in the subscribe select').toBeTruthy()
  await select.selectOption(planValue as string)
  await row.getByTestId('subscribe-submit').click()
  await expect(row.getByTestId('subscribe-message')).toContainText('Subscribed to')
}

test.describe.serial('TenantPay Billing full flow', () => {
  test('signup, login, plan, customer, subscription, idempotent webhook, activation, dashboard', async ({
    page,
    playwright,
  }) => {
    await test.step('1. sign up a tenant through the UI', async () => {
      await page.goto('/signup')
      await page.getByTestId('signup-tenant-name').fill(tenant.name)
      await page.getByTestId('signup-email').fill(tenant.email)
      await page.getByTestId('signup-password').fill(tenant.password)
      await shot(page, '01-signup.png')
      await page.getByTestId('signup-submit').click()
      await expect(page).toHaveURL(/\/dashboard$/)
      await expect(page.getByTestId('current-tenant')).toHaveText(tenant.name)
      tenantReady = true
    })

    await test.step('2. log out and log in again (JWT)', async () => {
      // Log out lives in the user menu at the top right.
      await page.getByTestId('user-menu').click()
      await page.getByTestId('logout').click()
      await expect(page).toHaveURL(/\/login/)
      expect(await page.evaluate((key) => window.localStorage.getItem(key), TOKEN_KEY)).toBeNull()

      await page.getByTestId('login-email').fill(tenant.email)
      await page.getByTestId('login-password').fill(tenant.password)
      await shot(page, '02-login.png')
      await page.getByTestId('login-submit').click()
      await expect(page).toHaveURL(/\/dashboard$/)
      await expect(page.getByTestId('current-tenant')).toHaveText(tenant.name)
      const token = await readToken(page)
      expect(token.split('.')).toHaveLength(3)
    })

    await test.step('3. create a plan', async () => {
      await page.getByTestId('nav-plans').click()
      await expect(page).toHaveURL(/\/plans$/)
      await page.getByTestId('plan-name').fill(plan.name)
      await page.getByTestId('plan-description').fill(plan.description)
      await page.getByTestId('plan-amount').fill(plan.amount)
      await page.getByTestId('plan-interval').selectOption('MONTHLY')
      await page.getByTestId('plan-submit').click()
      const row = page.getByTestId('plan-row').filter({ hasText: plan.name })
      await expect(row).toHaveCount(1)
      await expect(row).toContainText('49,90')
      await shot(page, '03-plan-created.png')
    })

    await test.step('4. create a customer and subscribe it (PENDING)', async () => {
      await createCustomerAndSubscribe(page, customer)
      await page.getByTestId('nav-subscriptions').click()
      await expect(page).toHaveURL(/\/subscriptions$/)
      const row = page.getByTestId('subscription-row').filter({ hasText: customer.name })
      await expect(row).toHaveCount(1)
      await expect(row.getByTestId('subscription-status')).toHaveText('PENDING')
      await shot(page, '04-subscription-pending.png')
    })

    await test.step('5. Asaas webhook delivered twice is processed once (idempotency)', async () => {
      const token = await readToken(page)
      const authed = await apiContext(playwright, token)
      const subsRes = await authed.get('/api/v1/subscriptions')
      expect(subsRes.status()).toBe(200)
      const subs = (await subsRes.json()) as Array<{
        customerName: string
        customerId: string
        latestCharge: { id: string; gatewayPaymentId: string; dueDate: string } | null
      }>
      const sub = subs.find((s) => s.customerName === customer.name)
      expect(sub?.latestCharge?.gatewayPaymentId, 'latest charge has a gateway payment id').toBeTruthy()
      const charge = sub!.latestCharge!

      const custRes = await authed.get(`/api/v1/customers/${sub!.customerId}`)
      expect(custRes.status()).toBe(200)
      const gatewayCustomerId = ((await custRes.json()) as { gatewayCustomerId: string | null }).gatewayCustomerId

      const now = new Date()
      const today = now.toISOString().slice(0, 10)
      const payload = {
        id: `evt_e2e_${stamp}`,
        event: 'PAYMENT_CONFIRMED',
        dateCreated: now.toISOString().replace('T', ' ').slice(0, 19),
        payment: {
          object: 'payment',
          id: charge.gatewayPaymentId,
          customer: gatewayCustomerId,
          value: plan.cents / 100,
          netValue: plan.cents / 100 - 1,
          billingType: 'PIX',
          status: 'CONFIRMED',
          dueDate: charge.dueDate,
          paymentDate: today,
          externalReference: charge.id,
        },
      }

      const webhook = await apiContext(playwright)
      const send = () =>
        webhook.post('/webhooks/asaas', {
          headers: { 'asaas-access-token': WEBHOOK_TOKEN, 'Content-Type': 'application/json' },
          data: payload,
        })
      const first = await send()
      const firstBody = (await first.json()) as { received: boolean; duplicate: boolean; eventId: string }
      const second = await send()
      const secondBody = (await second.json()) as { received: boolean; duplicate: boolean; eventId: string }

      expect(first.status()).toBe(200)
      expect(firstBody).toMatchObject({ received: true, duplicate: false, eventId: payload.id })
      expect(second.status()).toBe(200)
      expect(secondBody).toMatchObject({ received: true, duplicate: true, eventId: payload.id })

      const evidence = await page.context().newPage()
      const block = (title: string, label: string, body: unknown) => `
        <section>
          <h2>${escapeHtml(title)} <span class="status">${escapeHtml(label)}</span></h2>
          <pre>${escapeHtml(JSON.stringify(body, null, 2))}</pre>
        </section>`
      await evidence.setContent(`<!doctype html>
        <html lang="en"><head><meta charset="utf-8"><title>Webhook idempotency evidence</title>
        <style>
          body { font-family: 'Segoe UI', system-ui, sans-serif; margin: 0; padding: 32px; background: #f1f5f9; color: #0f172a; }
          h1 { font-size: 22px; margin: 0 0 4px; }
          p.lead { margin: 0 0 24px; color: #475569; }
          .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
          section { background: #fff; border: 1px solid #cbd5e1; border-radius: 10px; padding: 16px 20px; }
          h2 { font-size: 15px; margin: 0 0 8px; display: flex; justify-content: space-between; }
          .status { background: #dcfce7; color: #166534; border-radius: 999px; padding: 0 10px; font-size: 13px; }
          pre { margin: 0; font-family: Consolas, ui-monospace, monospace; font-size: 12.5px; white-space: pre-wrap; word-break: break-all; }
          .verdict { margin-top: 16px; padding: 12px 16px; border-radius: 8px; background: #f0fdf4; color: #166534; border: 1px solid #bbf7d0; font-weight: 600; }
        </style></head><body>
        <h1>POST /webhooks/asaas delivered twice with the same event id</h1>
        <p class="lead">Header asaas-access-token set. Event ${escapeHtml(payload.id)} for payment ${escapeHtml(charge.gatewayPaymentId)}.</p>
        ${block('Request body (sent twice)', 'POST x2', payload)}
        <div class="grid" style="margin-top:16px">
          ${block('First response', `HTTP ${first.status()}`, firstBody)}
          ${block('Second response', `HTTP ${second.status()}`, secondBody)}
        </div>
        <div class="verdict">First delivery: duplicate=${firstBody.duplicate}. Second delivery: duplicate=${secondBody.duplicate}. The event was processed once.</div>
        </body></html>`)
      await evidence.setViewportSize({ width: 1280, height: 800 })
      await shot(evidence, '05-webhook-idempotent.png')
      await evidence.close()
      await authed.dispose()
      await webhook.dispose()
    })

    await test.step('6. the subscription becomes ACTIVE in the panel', async () => {
      await page.bringToFront()
      const row = page.getByTestId('subscription-row').filter({ hasText: customer.name })
      const badge = row.getByTestId('subscription-status')
      await expect
        .poll(
          async () => {
            await page.getByTestId('refresh-subscriptions').click()
            return (await badge.textContent())?.trim()
          },
          { timeout: 20_000, intervals: [1_000] },
        )
        .toBe('ACTIVE')
      await expect(badge).toHaveText('ACTIVE')
      await shot(page, '06-subscription-active.png')
    })

    await test.step('7. dashboard shows 1 active subscription and the received amount', async () => {
      await page.getByTestId('nav-dashboard').click()
      await expect(page).toHaveURL(/\/dashboard$/)
      await expect(page.getByTestId('dashboard-plans')).toHaveText('1')
      await expect(page.getByTestId('dashboard-customers')).toHaveText('1')
      await expect(page.getByTestId('dashboard-subscriptions')).toHaveText('1')
      await expect(page.getByTestId('dashboard-active')).toHaveText('1')
      await expect(page.getByTestId('dashboard-received')).toContainText('49,90')
      await shot(page, '07-dashboard.png')
    })
  })

  test('bonus: the dev "Simulate payment confirmed" button activates a second subscription', async ({
    page,
    playwright,
  }) => {
    const anon = await apiContext(playwright)
    const cfgRes = await anon.get('/api/v1/dev/config')
    const devTools = cfgRes.ok() && ((await cfgRes.json()) as { devTools?: boolean }).devTools === true
    test.skip(!devTools, 'Dev tools are disabled on the API (TENANTPAY_DEV_TOOLS_ENABLED=false)')

    if (!tenantReady) {
      // Running this test alone: create the tenant and plan through the API.
      const signup = await anon.post('/api/v1/auth/signup', {
        data: { tenantName: tenant.name, email: tenant.email, password: tenant.password },
      })
      expect(signup.status()).toBe(201)
      const { accessToken } = (await signup.json()) as { accessToken: string }
      const authed = await apiContext(playwright, accessToken)
      const created = await authed.post('/api/v1/plans', {
        data: { name: plan.name, description: plan.description, amountCents: plan.cents, interval: 'MONTHLY' },
      })
      expect(created.status()).toBe(201)
      await authed.dispose()
      tenantReady = true
    }
    await anon.dispose()

    await loginThroughUi(page)
    await createCustomerAndSubscribe(page, customer2)

    await page.getByTestId('nav-subscriptions').click()
    const row = page.getByTestId('subscription-row').filter({ hasText: customer2.name })
    await expect(row.getByTestId('subscription-status')).toHaveText('PENDING')
    await row.getByTestId('simulate-payment').click()

    // No manual refresh here: the panel polls the list after the simulated webhook.
    await expect(row.getByTestId('subscription-status')).toHaveText('ACTIVE', { timeout: 20_000 })
    await expect(page.getByTestId('subscriptions-notice')).toContainText('ACTIVE')
    await shot(page, '08-simulate-button.png')
  })
})
