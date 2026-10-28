// Shapes mirror docs/api-contract.md (v1). Money is always integer cents.

export type Interval = 'MONTHLY' | 'YEARLY'
export type SubscriptionStatus = 'PENDING' | 'ACTIVE' | 'OVERDUE' | 'CANCELED'
export type ChargeStatus = 'PENDING' | 'CONFIRMED' | 'RECEIVED' | 'OVERDUE' | 'CANCELED'

export interface AuthResponse {
  tenantId: string
  userId: string
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface Me {
  userId: string
  tenantId: string
  tenantName: string
  email: string
  role: string
}

export interface Plan {
  id: string
  name: string
  description: string | null
  amountCents: number
  currency: string
  interval: Interval
  active: boolean
  createdAt: string
}

export interface Customer {
  id: string
  name: string
  email: string
  document: string
  gatewayCustomerId: string | null
  createdAt: string
}

export interface LatestCharge {
  id: string
  gatewayPaymentId: string
  status: ChargeStatus
  amountCents: number
  dueDate: string
}

export interface Subscription {
  id: string
  customerId: string
  customerName: string
  planId: string
  planName: string
  amountCents: number
  status: SubscriptionStatus
  createdAt: string
  activatedAt: string | null
  canceledAt: string | null
  latestCharge: LatestCharge | null
}

export interface Dashboard {
  plans: number
  customers: number
  subscriptions: number
  subscriptionsByStatus: Partial<Record<SubscriptionStatus, number>>
  receivedAmountCents: number
}

export interface DevConfig {
  devTools: boolean
  gateway: string
}

export interface WebhookAck {
  received: boolean
  duplicate: boolean
  eventId: string
}

export interface Charge {
  id: string
  subscriptionId: string
  customerId: string
  gatewayPaymentId: string
  amountCents: number
  status: ChargeStatus
  dueDate: string
  paidAt: string | null
  createdAt: string
}

export type WebhookEventStatus = 'RECEIVED' | 'PROCESSING' | 'PROCESSED' | 'IGNORED' | 'FAILED' | 'DEAD'

export interface WebhookEvent {
  id: string
  eventId: string
  eventType: string
  status: WebhookEventStatus
  attempts: number
  lastError: string | null
  receivedAt: string
  processedAt: string | null
  nextAttemptAt: string | null
}
