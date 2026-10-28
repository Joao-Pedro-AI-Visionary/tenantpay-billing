/*
 * In-browser mock of the TenantPay Billing API (docs/api-contract.md), used only by the demo build.
 *
 * - Every route the panel calls is implemented with the same request/response shapes and status codes.
 * - Data lives in localStorage under the `tenantpay.demo.` prefix and is seeded with sample data
 *   (tenant "Acme Gym") the first time the demo loads.
 * - Webhook processing is asynchronous like the real inbox: a simulated payment is stored as RECEIVED
 *   and applied on a later request once its processing time has passed.
 * - The demo account password is generated in the browser when the data is seeded, so no credential
 *   is written in the source. The login screen reads it through demoCredentials().
 * Nothing here talks to a server.
 */
import type {
  ChargeStatus,
  Interval,
  SubscriptionStatus,
  WebhookEventStatus,
} from '../types'

export const DEMO_STORAGE_PREFIX = 'tenantpay.demo.'
const DB_KEY = `${DEMO_STORAGE_PREFIX}db`
const DB_VERSION = 1
/** Simulated network latency, kept small so feedback stays under the Doherty threshold. */
const LATENCY_MS = 150
/** Time the simulated inbox takes to process a webhook. */
const PROCESSING_DELAY_MS = 1500

const DAY = 24 * 60 * 60 * 1000

/** Email of the seeded demo tenant admin. Its password is generated per browser (see seed). */
export const DEMO_EMAIL = 'demo@tenantpay.dev'

interface StoredTenant {
  id: string
  name: string
}
interface StoredUser {
  id: string
  tenantId: string
  email: string
  password: string
  role: 'ADMIN'
}
interface StoredPlan {
  id: string
  tenantId: string
  name: string
  description: string | null
  amountCents: number
  currency: 'BRL'
  interval: Interval
  active: boolean
  createdAt: string
}
interface StoredCustomer {
  id: string
  tenantId: string
  name: string
  email: string
  document: string
  gatewayCustomerId: string
  createdAt: string
}
interface StoredSubscription {
  id: string
  tenantId: string
  customerId: string
  planId: string
  amountCents: number
  status: SubscriptionStatus
  createdAt: string
  activatedAt: string | null
  canceledAt: string | null
}
interface StoredCharge {
  id: string
  tenantId: string
  subscriptionId: string
  customerId: string
  gatewayPaymentId: string
  amountCents: number
  status: ChargeStatus
  dueDate: string
  paidAt: string | null
  createdAt: string
}
interface StoredEvent {
  id: string
  tenantId: string
  eventId: string
  eventType: string
  status: WebhookEventStatus
  attempts: number
  lastError: string | null
  receivedAt: string
  processedAt: string | null
  nextAttemptAt: string | null
  chargeId: string | null
  /** Epoch ms after which a RECEIVED event is processed. */
  processAt: number | null
}
interface DemoDb {
  version: number
  tenants: StoredTenant[]
  users: StoredUser[]
  plans: StoredPlan[]
  customers: StoredCustomer[]
  subscriptions: StoredSubscription[]
  charges: StoredCharge[]
  events: StoredEvent[]
}

class HttpProblem extends Error {
  constructor(
    readonly status: number,
    readonly title: string,
    readonly detail: string,
    readonly errors: { field: string; message: string }[] = [],
  ) {
    super(detail)
  }
}

// ---------- helpers ----------

let counter = 0
function uuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  counter += 1
  const rand = Math.random().toString(16).slice(2, 14).padEnd(12, '0')
  return `00000000-0000-4000-8000-${(rand + counter.toString(16)).slice(-12)}`
}

/** Random demo password (16 hex chars). getRandomValues also works outside secure contexts. */
function randomPassword(): string {
  const bytes = new Uint8Array(8)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

function shortId(): string {
  return uuid().replace(/-/g, '').slice(0, 16)
}

function iso(ms: number): string {
  return new Date(ms).toISOString().replace(/\.\d{3}Z$/, 'Z')
}

function dateOnly(ms: number): string {
  return new Date(ms).toISOString().slice(0, 10)
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** Builds a CPF with valid check digits from 9 base digits (sample data only). */
function cpfFrom(base: string): string {
  const nums = base.split('').map(Number)
  const digit = (arr: number[]): number => {
    const factor = arr.length + 1
    const sum = arr.reduce((acc, n, i) => acc + n * (factor - i), 0)
    const rest = sum % 11
    return rest < 2 ? 0 : 11 - rest
  }
  const d1 = digit(nums)
  const d2 = digit([...nums, d1])
  return `${base}${d1}${d2}`
}

function base64Url(value: string): string {
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function fromBase64Url(value: string): string {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/')
  return atob(padded + '='.repeat((4 - (padded.length % 4)) % 4))
}

/** A JWT shaped token (three dot separated parts). The signature part is a fixed marker, not a secret. */
function issueToken(user: StoredUser): string {
  const header = base64Url(JSON.stringify({ alg: 'none', typ: 'JWT' }))
  const payload = base64Url(
    JSON.stringify({ sub: user.id, tenant_id: user.tenantId, email: user.email, role: user.role, demo: true }),
  )
  return `${header}.${payload}.demo`
}

function authResponse(user: StoredUser) {
  return {
    tenantId: user.tenantId,
    userId: user.id,
    accessToken: issueToken(user),
    tokenType: 'Bearer',
    expiresIn: 3600,
  }
}

// ---------- persistence and seed ----------

function seed(): DemoDb {
  const now = Date.now()
  const tenantId = '7d0c2b1e-4a5f-4c3e-9b1a-000000000001'
  const db: DemoDb = {
    version: DB_VERSION,
    tenants: [{ id: tenantId, name: 'Acme Gym' }],
    users: [
      {
        id: '7d0c2b1e-4a5f-4c3e-9b1a-000000000002',
        tenantId,
        email: DEMO_EMAIL,
        password: randomPassword(),
        role: 'ADMIN',
      },
    ],
    plans: [],
    customers: [],
    subscriptions: [],
    charges: [],
    events: [],
  }

  const plan = (key: string, name: string, description: string, amountCents: number, interval: Interval, ageDays: number) => {
    const p: StoredPlan = {
      id: `a1000000-0000-4000-8000-00000000000${key}`,
      tenantId,
      name,
      description,
      amountCents,
      currency: 'BRL',
      interval,
      active: true,
      createdAt: iso(now - ageDays * DAY),
    }
    db.plans.unshift(p)
    return p
  }
  const basic = plan('1', 'Basic', 'Gym floor access during off-peak hours', 8990, 'MONTHLY', 90)
  const pro = plan('2', 'Pro', 'Unlimited classes and full gym floor access', 14990, 'MONTHLY', 88)
  const annual = plan('3', 'Pro Annual', 'Twelve months of Pro, two months free', 149900, 'YEARLY', 85)
  const personal = plan('4', 'Personal Training', 'Four one on one sessions per month', 39900, 'MONTHLY', 80)

  const customer = (key: string, name: string, email: string, document: string, ageDays: number) => {
    const c: StoredCustomer = {
      id: `b2000000-0000-4000-8000-00000000000${key}`,
      tenantId,
      name,
      email,
      document,
      gatewayCustomerId: `cus_demo_${key.padStart(6, '0')}`,
      createdAt: iso(now - ageDays * DAY),
    }
    db.customers.unshift(c)
    return c
  }
  const ana = customer('1', 'Ana Souza', 'ana.souza@example.test', cpfFrom('529982247'), 60)
  const bruno = customer('2', 'Bruno Lima', 'bruno.lima@example.test', cpfFrom('111444777'), 52)
  const carla = customer('3', 'Carla Mendes', 'carla.mendes@example.test', cpfFrom('390533447'), 45)
  const diego = customer('4', 'Diego Rocha', 'diego.rocha@example.test', cpfFrom('123456789'), 38)
  const elisa = customer('5', 'Elisa Martins', 'elisa.martins@example.test', cpfFrom('987654321'), 3)
  const felipe = customer('6', 'Felipe Costa', 'felipe.costa@example.test', cpfFrom('246813579'), 30)
  customer('7', 'Blue Harbor Fitness Ltda', 'finance@blueharbor.example', '11222333000181', 1)

  let chargeSeq = 0
  const charge = (
    sub: StoredSubscription,
    status: ChargeStatus,
    dueOffsetDays: number,
    createdOffsetDays: number,
  ): StoredCharge => {
    chargeSeq += 1
    const paid = status === 'CONFIRMED' || status === 'RECEIVED'
    const c: StoredCharge = {
      id: `c3000000-0000-4000-8000-${String(chargeSeq).padStart(12, '0')}`,
      tenantId,
      subscriptionId: sub.id,
      customerId: sub.customerId,
      gatewayPaymentId: `pay_demo_${String(chargeSeq).padStart(6, '0')}`,
      amountCents: sub.amountCents,
      status,
      dueDate: dateOnly(now + dueOffsetDays * DAY),
      paidAt: paid ? iso(now + (dueOffsetDays - 1) * DAY) : null,
      createdAt: iso(now + createdOffsetDays * DAY),
    }
    db.charges.unshift(c)
    return c
  }

  let subSeq = 0
  const subscription = (
    c: StoredCustomer,
    p: StoredPlan,
    status: SubscriptionStatus,
    ageDays: number,
  ): StoredSubscription => {
    subSeq += 1
    const s: StoredSubscription = {
      id: `d4000000-0000-4000-8000-${String(subSeq).padStart(12, '0')}`,
      tenantId,
      customerId: c.id,
      planId: p.id,
      amountCents: p.amountCents,
      status,
      createdAt: iso(now - ageDays * DAY),
      activatedAt: status === 'PENDING' ? null : iso(now - (ageDays - 2) * DAY),
      canceledAt: status === 'CANCELED' ? iso(now - 4 * DAY) : null,
    }
    db.subscriptions.unshift(s)
    return s
  }

  const sAna = subscription(ana, pro, 'ACTIVE', 58)
  charge(sAna, 'RECEIVED', -55, -58)
  charge(sAna, 'RECEIVED', -25, -28)
  charge(sAna, 'PENDING', 5, -2)

  const sBruno = subscription(bruno, basic, 'ACTIVE', 50)
  charge(sBruno, 'RECEIVED', -47, -50)
  charge(sBruno, 'CONFIRMED', -17, -20)
  charge(sBruno, 'PENDING', 13, -1)

  const sCarla = subscription(carla, annual, 'ACTIVE', 44)
  charge(sCarla, 'RECEIVED', -41, -44)

  const sFelipe = subscription(felipe, personal, 'CANCELED', 29)
  charge(sFelipe, 'RECEIVED', -26, -29)
  charge(sFelipe, 'CANCELED', 4, -3)

  const sDiego = subscription(diego, pro, 'OVERDUE', 36)
  charge(sDiego, 'RECEIVED', -33, -36)
  const overdue = charge(sDiego, 'OVERDUE', -3, -6)

  const sElisa = subscription(elisa, basic, 'PENDING', 1)
  charge(sElisa, 'PENDING', 2, -1)

  const event = (
    type: string,
    status: WebhookEventStatus,
    ageHours: number,
    chargeId: string | null,
    extra: Partial<StoredEvent> = {},
  ) => {
    db.events.unshift({
      id: uuid(),
      tenantId,
      eventId: `evt_demo_${shortId()}`,
      eventType: type,
      status,
      attempts: status === 'PROCESSED' || status === 'IGNORED' ? 1 : 0,
      lastError: null,
      receivedAt: iso(now - ageHours * 60 * 60 * 1000),
      processedAt: status === 'PROCESSED' || status === 'IGNORED' ? iso(now - ageHours * 60 * 60 * 1000 + 2000) : null,
      nextAttemptAt: null,
      chargeId,
      processAt: null,
      ...extra,
    })
  }
  event('PAYMENT_RECEIVED', 'PROCESSED', 24 * 25, null)
  event('PAYMENT_CREATED', 'IGNORED', 24 * 6, null)
  event('PAYMENT_OVERDUE', 'PROCESSED', 24 * 3, overdue.id)
  event('PAYMENT_CONFIRMED', 'DEAD', 20, overdue.id, {
    attempts: 5,
    lastError: 'Gateway lookup timed out after 5 attempts (simulated failure)',
  })
  event('PAYMENT_CREATED', 'IGNORED', 2, null)

  return db
}

function loadDb(): DemoDb {
  const raw = localStorage.getItem(DB_KEY)
  if (raw) {
    try {
      const parsed = JSON.parse(raw) as DemoDb
      if (parsed && parsed.version === DB_VERSION) return parsed
    } catch {
      // Corrupted data: fall through and reseed.
    }
  }
  const fresh = seed()
  saveDb(fresh)
  return fresh
}

function saveDb(db: DemoDb): void {
  localStorage.setItem(DB_KEY, JSON.stringify(db))
}

/** Credentials of the seeded demo admin, shown on the login screen. Seeds the data on first use. */
export function demoCredentials(): { email: string; password: string } | null {
  const user = loadDb().users.find((u) => u.email === DEMO_EMAIL)
  return user ? { email: user.email, password: user.password } : null
}

/** Removes every demo key so the next request reseeds the sample data. */
export function resetDemoData(): void {
  const keys: string[] = []
  for (let i = 0; i < localStorage.length; i += 1) {
    const key = localStorage.key(i)
    if (key && key.startsWith(DEMO_STORAGE_PREFIX)) keys.push(key)
  }
  keys.forEach((key) => localStorage.removeItem(key))
}

// ---------- asynchronous inbox ----------

function processDueEvents(db: DemoDb): void {
  const now = Date.now()
  for (const ev of db.events) {
    if (ev.status !== 'RECEIVED' || ev.processAt === null || ev.processAt > now) continue
    ev.attempts += 1
    const charge = ev.chargeId ? db.charges.find((c) => c.id === ev.chargeId) : undefined
    if (charge && (ev.eventType === 'PAYMENT_CONFIRMED' || ev.eventType === 'PAYMENT_RECEIVED')) {
      if (charge.status !== 'CONFIRMED' && charge.status !== 'RECEIVED') {
        charge.status = ev.eventType === 'PAYMENT_RECEIVED' ? 'RECEIVED' : 'CONFIRMED'
        charge.paidAt = iso(now)
      }
      const sub = db.subscriptions.find((s) => s.id === charge.subscriptionId)
      if (sub && sub.status !== 'CANCELED') {
        sub.status = 'ACTIVE'
        sub.activatedAt = sub.activatedAt ?? iso(now)
      }
    }
    ev.status = 'PROCESSED'
    ev.processedAt = iso(now)
    ev.lastError = null
    ev.nextAttemptAt = null
    ev.processAt = null
  }
}

// ---------- views ----------

function latestCharge(db: DemoDb, subscriptionId: string): StoredCharge | undefined {
  return db.charges
    .filter((c) => c.subscriptionId === subscriptionId)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0]
}

function planView(p: StoredPlan) {
  const { tenantId: _t, ...rest } = p
  return rest
}

function customerView(c: StoredCustomer) {
  const { tenantId: _t, ...rest } = c
  return rest
}

function chargeView(c: StoredCharge) {
  const { tenantId: _t, ...rest } = c
  return rest
}

function eventView(e: StoredEvent) {
  const { tenantId: _t, chargeId: _c, processAt: _p, ...rest } = e
  return rest
}

function subscriptionView(db: DemoDb, s: StoredSubscription) {
  const customer = db.customers.find((c) => c.id === s.customerId)
  const plan = db.plans.find((p) => p.id === s.planId)
  const lc = latestCharge(db, s.id)
  return {
    id: s.id,
    customerId: s.customerId,
    customerName: customer?.name ?? 'Unknown customer',
    planId: s.planId,
    planName: plan?.name ?? 'Unknown plan',
    amountCents: s.amountCents,
    status: s.status,
    createdAt: s.createdAt,
    activatedAt: s.activatedAt,
    canceledAt: s.canceledAt,
    latestCharge: lc
      ? {
          id: lc.id,
          gatewayPaymentId: lc.gatewayPaymentId,
          status: lc.status,
          amountCents: lc.amountCents,
          dueDate: lc.dueDate,
        }
      : null,
  }
}

const newestFirst = <T extends { createdAt: string }>(a: T, b: T) => b.createdAt.localeCompare(a.createdAt)

// ---------- request handling ----------

interface Ctx {
  db: DemoDb
  method: string
  segments: string[]
  query: URLSearchParams
  body: Record<string, unknown>
  user: StoredUser | null
}

function requireUser(ctx: Ctx): StoredUser {
  if (!ctx.user) throw new HttpProblem(401, 'Unauthorized', 'Missing or invalid access token')
  return ctx.user
}

function str(body: Record<string, unknown>, key: string): string {
  const v = body[key]
  return typeof v === 'string' ? v.trim() : ''
}

function validate(errors: { field: string; message: string }[]): void {
  if (errors.length > 0) throw new HttpProblem(400, 'Bad Request', 'Validation failed', errors)
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function handleAuth(ctx: Ctx, action: string): { status: number; body: unknown } {
  const { db, body } = ctx
  const email = str(body, 'email').toLowerCase()
  const password = typeof body.password === 'string' ? body.password : ''
  if (action === 'login' && ctx.method === 'POST') {
    const user = db.users.find((u) => u.email === email && u.password === password)
    if (!user) throw new HttpProblem(401, 'Unauthorized', 'Invalid email or password')
    return { status: 200, body: authResponse(user) }
  }
  if (action === 'signup' && ctx.method === 'POST') {
    const tenantName = str(body, 'tenantName')
    const errors = []
    if (tenantName.length < 2 || tenantName.length > 120) errors.push({ field: 'tenantName', message: 'size must be between 2 and 120' })
    if (!EMAIL_RE.test(email)) errors.push({ field: 'email', message: 'must be a well-formed email address' })
    if (password.length < 8 || password.length > 72) errors.push({ field: 'password', message: 'size must be between 8 and 72' })
    validate(errors)
    if (db.users.some((u) => u.email === email)) throw new HttpProblem(409, 'Conflict', `Email ${email} is already registered`)
    const tenant: StoredTenant = { id: uuid(), name: tenantName }
    const user: StoredUser = { id: uuid(), tenantId: tenant.id, email, password, role: 'ADMIN' }
    db.tenants.push(tenant)
    db.users.push(user)
    return { status: 201, body: authResponse(user) }
  }
  throw new HttpProblem(404, 'Not Found', 'Route not found')
}

function planInput(body: Record<string, unknown>) {
  const name = str(body, 'name')
  const description = str(body, 'description') || null
  const amountCents = typeof body.amountCents === 'number' ? body.amountCents : NaN
  const interval = body.interval
  const errors = []
  if (!name || name.length > 120) errors.push({ field: 'name', message: 'must not be blank' })
  if (!Number.isInteger(amountCents) || amountCents < 100) errors.push({ field: 'amountCents', message: 'must be greater than or equal to 100' })
  if (interval !== 'MONTHLY' && interval !== 'YEARLY') errors.push({ field: 'interval', message: 'must be MONTHLY or YEARLY' })
  validate(errors)
  return { name, description, amountCents, interval: interval as Interval }
}

function route(ctx: Ctx): { status: number; body?: unknown } {
  const { db, method, segments: s } = ctx
  // s = ['api', 'v1', ...rest]
  if (s[0] !== 'api' || s[1] !== 'v1') throw new HttpProblem(404, 'Not Found', 'Route not found in demo mode')
  const [resource, id, sub] = s.slice(2)

  if (resource === 'auth') return handleAuth(ctx, id ?? '')
  if (resource === 'dev' && id === 'config' && method === 'GET') return { status: 200, body: { devTools: true, gateway: 'demo' } }

  const user = requireUser(ctx)
  const t = user.tenantId
  const notFound = (what: string, ref: string) => new HttpProblem(404, 'Not Found', `${what} ${ref} not found`)

  if (resource === 'me' && method === 'GET') {
    const tenant = db.tenants.find((x) => x.id === t)
    return {
      status: 200,
      body: { userId: user.id, tenantId: t, tenantName: tenant?.name ?? '', email: user.email, role: user.role },
    }
  }

  if (resource === 'plans') {
    const plans = db.plans.filter((p) => p.tenantId === t)
    if (!id) {
      if (method === 'GET') return { status: 200, body: plans.sort(newestFirst).map(planView) }
      if (method === 'POST') {
        const input = planInput(ctx.body)
        const plan: StoredPlan = { id: uuid(), tenantId: t, currency: 'BRL', active: true, createdAt: iso(Date.now()), ...input }
        db.plans.push(plan)
        return { status: 201, body: planView(plan) }
      }
    } else {
      const plan = plans.find((p) => p.id === id)
      if (!plan) throw notFound('Plan', id)
      if (method === 'GET') return { status: 200, body: planView(plan) }
      if (method === 'PUT') {
        Object.assign(plan, planInput(ctx.body))
        return { status: 200, body: planView(plan) }
      }
      if (method === 'DELETE') {
        plan.active = false
        return { status: 204 }
      }
    }
  }

  if (resource === 'customers') {
    const customers = db.customers.filter((c) => c.tenantId === t)
    if (!id) {
      if (method === 'GET') return { status: 200, body: customers.sort(newestFirst).map(customerView) }
      if (method === 'POST') {
        const name = str(ctx.body, 'name')
        const email = str(ctx.body, 'email')
        const document = str(ctx.body, 'document')
        const errors = []
        if (!name) errors.push({ field: 'name', message: 'must not be blank' })
        if (!EMAIL_RE.test(email)) errors.push({ field: 'email', message: 'must be a well-formed email address' })
        if (!/^(\d{11}|\d{14})$/.test(document)) errors.push({ field: 'document', message: 'must have 11 (CPF) or 14 (CNPJ) digits' })
        validate(errors)
        const customer: StoredCustomer = {
          id: uuid(),
          tenantId: t,
          name,
          email,
          document,
          gatewayCustomerId: `cus_demo_${shortId()}`,
          createdAt: iso(Date.now()),
        }
        db.customers.push(customer)
        return { status: 201, body: customerView(customer) }
      }
    } else if (method === 'GET') {
      const customer = customers.find((c) => c.id === id)
      if (!customer) throw notFound('Customer', id)
      return { status: 200, body: customerView(customer) }
    }
  }

  if (resource === 'subscriptions') {
    const subs = db.subscriptions.filter((x) => x.tenantId === t)
    if (!id) {
      if (method === 'GET') {
        const status = ctx.query.get('status')
        const list = subs.filter((x) => !status || x.status === status).sort(newestFirst)
        return { status: 200, body: list.map((x) => subscriptionView(db, x)) }
      }
      if (method === 'POST') {
        const customerId = str(ctx.body, 'customerId')
        const planId = str(ctx.body, 'planId')
        const errors = []
        if (!customerId) errors.push({ field: 'customerId', message: 'must not be null' })
        if (!planId) errors.push({ field: 'planId', message: 'must not be null' })
        validate(errors)
        const customer = db.customers.find((c) => c.id === customerId && c.tenantId === t)
        if (!customer) throw notFound('Customer', customerId)
        const plan = db.plans.find((p) => p.id === planId && p.tenantId === t)
        if (!plan) throw notFound('Plan', planId)
        if (!plan.active) throw new HttpProblem(409, 'Conflict', `Plan ${plan.name} is inactive`)
        const now = Date.now()
        const created: StoredSubscription = {
          id: uuid(),
          tenantId: t,
          customerId,
          planId,
          amountCents: plan.amountCents,
          status: 'PENDING',
          createdAt: iso(now),
          activatedAt: null,
          canceledAt: null,
        }
        db.subscriptions.push(created)
        db.charges.push({
          id: uuid(),
          tenantId: t,
          subscriptionId: created.id,
          customerId,
          gatewayPaymentId: `pay_demo_${shortId()}`,
          amountCents: plan.amountCents,
          status: 'PENDING',
          dueDate: dateOnly(now + 3 * DAY),
          paidAt: null,
          createdAt: iso(now),
        })
        return { status: 201, body: subscriptionView(db, created) }
      }
    } else {
      const found = subs.find((x) => x.id === id)
      if (!found) throw notFound('Subscription', id)
      if (!sub && method === 'GET') return { status: 200, body: subscriptionView(db, found) }
      if (sub === 'cancel' && method === 'POST') {
        if (found.status === 'CANCELED') throw new HttpProblem(409, 'Conflict', 'Subscription is already canceled')
        found.status = 'CANCELED'
        found.canceledAt = iso(Date.now())
        return { status: 200, body: subscriptionView(db, found) }
      }
      if (sub === 'charges' && method === 'GET') {
        const charges = db.charges.filter((c) => c.subscriptionId === found.id).sort(newestFirst)
        return { status: 200, body: charges.map(chargeView) }
      }
    }
  }

  if (resource === 'charges' && !id && method === 'GET') {
    const charges = db.charges.filter((c) => c.tenantId === t).sort(newestFirst)
    return { status: 200, body: charges.map(chargeView) }
  }

  if (resource === 'dashboard' && method === 'GET') {
    const subs = db.subscriptions.filter((x) => x.tenantId === t)
    const byStatus: Record<SubscriptionStatus, number> = { PENDING: 0, ACTIVE: 0, OVERDUE: 0, CANCELED: 0 }
    subs.forEach((x) => (byStatus[x.status] += 1))
    const received = db.charges
      .filter((c) => c.tenantId === t && (c.status === 'CONFIRMED' || c.status === 'RECEIVED'))
      .reduce((acc, c) => acc + c.amountCents, 0)
    return {
      status: 200,
      body: {
        plans: db.plans.filter((p) => p.tenantId === t).length,
        customers: db.customers.filter((c) => c.tenantId === t).length,
        subscriptions: subs.length,
        subscriptionsByStatus: byStatus,
        receivedAmountCents: received,
      },
    }
  }

  if (resource === 'dev' && id === 'webhooks' && sub === 'simulate' && method === 'POST') {
    const subscriptionId = str(ctx.body, 'subscriptionId')
    const eventType = str(ctx.body, 'event') || 'PAYMENT_CONFIRMED'
    const found = db.subscriptions.find((x) => x.id === subscriptionId && x.tenantId === t)
    if (!found) throw notFound('Subscription', subscriptionId)
    const lc = latestCharge(db, found.id)
    if (!lc) throw new HttpProblem(409, 'Conflict', 'Subscription has no charge to pay')
    const now = Date.now()
    const eventId = `evt_sim_${shortId()}`
    db.events.push({
      id: uuid(),
      tenantId: t,
      eventId,
      eventType,
      status: 'RECEIVED',
      attempts: 0,
      lastError: null,
      receivedAt: iso(now),
      processedAt: null,
      nextAttemptAt: null,
      chargeId: lc.id,
      processAt: now + PROCESSING_DELAY_MS,
    })
    return { status: 200, body: { received: true, duplicate: false, eventId } }
  }

  if (resource === 'admin' && id === 'webhook-events') {
    if (user.role !== 'ADMIN') throw new HttpProblem(403, 'Forbidden', 'Admin role required')
    const events = db.events.filter((e) => e.tenantId === t)
    // Path: /api/v1/admin/webhook-events/{eventRef}/{action}
    const eventRef = s[4]
    const action = s[5]
    if (!eventRef && method === 'GET') {
      const status = ctx.query.get('status')
      const list = events
        .filter((e) => !status || e.status === status)
        .sort((a, b) => b.receivedAt.localeCompare(a.receivedAt))
      return { status: 200, body: list.map(eventView) }
    }
    if (eventRef && action === 'reprocess' && method === 'POST') {
      const ev = events.find((e) => e.id === eventRef)
      if (!ev) throw notFound('Webhook event', eventRef)
      if (ev.status === 'PROCESSED' || ev.status === 'IGNORED') {
        throw new HttpProblem(409, 'Conflict', `Event in status ${ev.status} cannot be reprocessed`)
      }
      ev.status = 'RECEIVED'
      ev.attempts = 0
      ev.lastError = null
      ev.processAt = Date.now() + PROCESSING_DELAY_MS
      ev.nextAttemptAt = iso(ev.processAt)
      return { status: 202, body: eventView(ev) }
    }
  }

  throw new HttpProblem(404, 'Not Found', `No demo route for ${method} /${s.join('/')}`)
}

function userFromAuth(db: DemoDb, header: string | null): StoredUser | null {
  if (!header || !header.startsWith('Bearer ')) return null
  const parts = header.slice(7).split('.')
  if (parts.length !== 3 || parts[2] !== 'demo') return null
  try {
    const claims = JSON.parse(fromBase64Url(parts[1] ?? '')) as { sub?: string; tenant_id?: string }
    return db.users.find((u) => u.id === claims.sub && u.tenantId === claims.tenant_id) ?? null
  } catch {
    return null
  }
}

function json(status: number, body: unknown, contentType = 'application/json'): Response {
  if (status === 204 || body === undefined) return new Response(null, { status })
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } })
}

/** fetch compatible entry point used by api.ts when the panel runs in demo mode. */
export async function demoFetch(path: string, init: RequestInit = {}): Promise<Response> {
  await sleep(LATENCY_MS)
  const url = new URL(path, 'http://demo.local')
  const headers = new Headers(init.headers)
  const db = loadDb()
  processDueEvents(db)

  let body: Record<string, unknown> = {}
  if (typeof init.body === 'string' && init.body) {
    try {
      const parsed: unknown = JSON.parse(init.body)
      if (parsed && typeof parsed === 'object') body = parsed as Record<string, unknown>
    } catch {
      return json(400, { type: 'about:blank', title: 'Bad Request', status: 400, detail: 'Malformed JSON body', instance: url.pathname }, 'application/problem+json')
    }
  }

  const ctx: Ctx = {
    db,
    method: (init.method ?? 'GET').toUpperCase(),
    segments: url.pathname.split('/').filter(Boolean),
    query: url.searchParams,
    body,
    user: userFromAuth(db, headers.get('Authorization')),
  }

  try {
    const result = route(ctx)
    saveDb(db)
    return json(result.status, result.body)
  } catch (err) {
    saveDb(db)
    if (err instanceof HttpProblem) {
      const problem: Record<string, unknown> = {
        type: 'about:blank',
        title: err.title,
        status: err.status,
        detail: err.detail,
        instance: url.pathname,
      }
      if (err.errors.length > 0) problem.errors = err.errors
      return json(err.status, problem, 'application/problem+json')
    }
    throw err
  }
}
