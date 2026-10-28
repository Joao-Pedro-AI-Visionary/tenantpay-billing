import { clearToken, getToken } from './auth'
import { DEMO_MODE } from './config'
import { router } from './router'
import type {
  AuthResponse,
  Charge,
  Customer,
  Dashboard,
  DevConfig,
  Interval,
  Me,
  Plan,
  Subscription,
  WebhookAck,
  WebhookEvent,
} from './types'

export interface FieldError {
  field: string
  message: string
}

/** Error raised for any non 2xx response, built from an RFC 7807 ProblemDetail when available. */
export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly fieldErrors: FieldError[]

  constructor(status: number, title: string, detail: string, fieldErrors: FieldError[] = []) {
    super(detail || title || `Request failed with status ${status}`)
    this.status = status
    this.title = title
    this.fieldErrors = fieldErrors
  }
}

/** Human readable message, including field errors, for any thrown value. */
export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.fieldErrors.length > 0) {
      return `${err.message}: ${err.fieldErrors.map((f) => `${f.field} ${f.message}`).join('; ')}`
    }
    return err.message
  }
  if (err instanceof Error) return err.message
  return 'Unexpected error'
}

async function toApiError(res: Response): Promise<ApiError> {
  let title = res.statusText
  let detail = ''
  let fieldErrors: FieldError[] = []
  try {
    const body: unknown = await res.json()
    if (body && typeof body === 'object') {
      const p = body as Record<string, unknown>
      if (typeof p.title === 'string') title = p.title
      if (typeof p.detail === 'string') detail = p.detail
      if (Array.isArray(p.errors)) {
        fieldErrors = p.errors
          .filter((e): e is FieldError => !!e && typeof e === 'object' && 'field' in e && 'message' in e)
          .map((e) => ({ field: String(e.field), message: String(e.message) }))
      }
    }
  } catch {
    // Body was not JSON; keep the status text.
  }
  return new ApiError(res.status, title, detail || title, fieldErrors)
}

interface RequestOptions {
  method?: string
  body?: unknown
  /** Auth endpoints must not trigger the global 401 redirect (bad credentials are a form error). */
  skipAuthRedirect?: boolean
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'

  const init: RequestInit = {
    method: opts.method ?? 'GET',
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  }

  let res: Response
  try {
    if (DEMO_MODE) {
      // Demo build: the same contract is served by an in-browser mock persisted in localStorage.
      const { demoFetch } = await import('#demo-api')
      res = await demoFetch(path, init)
    } else {
      res = await fetch(path, init)
    }
  } catch {
    throw new ApiError(0, 'Network error', 'Could not reach the API. Is the backend running?')
  }

  if (res.status === 401 && !opts.skipAuthRedirect) {
    clearToken()
    if (router.currentRoute.value.name !== 'login') {
      await router.push({ name: 'login', query: { expired: '1' } })
    }
  }
  if (!res.ok) throw await toApiError(res)
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  signup: (tenantName: string, email: string, password: string) =>
    request<AuthResponse>('/api/v1/auth/signup', {
      method: 'POST',
      body: { tenantName, email, password },
      skipAuthRedirect: true,
    }),
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: { email, password },
      skipAuthRedirect: true,
    }),
  me: () => request<Me>('/api/v1/me'),
  dashboard: () => request<Dashboard>('/api/v1/dashboard'),
  listPlans: () => request<Plan[]>('/api/v1/plans'),
  createPlan: (body: { name: string; description?: string; amountCents: number; interval: Interval }) =>
    request<Plan>('/api/v1/plans', { method: 'POST', body }),
  listCustomers: () => request<Customer[]>('/api/v1/customers'),
  createCustomer: (body: { name: string; email: string; document: string }) =>
    request<Customer>('/api/v1/customers', { method: 'POST', body }),
  listSubscriptions: () => request<Subscription[]>('/api/v1/subscriptions'),
  createSubscription: (customerId: string, planId: string) =>
    request<Subscription>('/api/v1/subscriptions', { method: 'POST', body: { customerId, planId } }),
  cancelSubscription: (id: string) =>
    request<Subscription>(`/api/v1/subscriptions/${encodeURIComponent(id)}/cancel`, { method: 'POST' }),
  listCharges: () => request<Charge[]>('/api/v1/charges'),
  listWebhookEvents: () => request<WebhookEvent[]>('/api/v1/admin/webhook-events'),
  reprocessWebhookEvent: (id: string) =>
    request<WebhookEvent>(`/api/v1/admin/webhook-events/${encodeURIComponent(id)}/reprocess`, { method: 'POST' }),
  devConfig: () => request<DevConfig>('/api/v1/dev/config', { skipAuthRedirect: true }),
  simulatePayment: (subscriptionId: string) =>
    request<WebhookAck>('/api/v1/dev/webhooks/simulate', {
      method: 'POST',
      body: { subscriptionId, event: 'PAYMENT_CONFIRMED' },
    }),
}
