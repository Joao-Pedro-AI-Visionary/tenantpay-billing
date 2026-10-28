# TenantPay Billing: API contract (v1)

This is the single source of truth shared by the backend, the Vue panel and the e2e suite.
All JSON uses camelCase. Timestamps are ISO-8601 UTC (`2026-10-28T12:00:00Z`). Dates are `yyyy-MM-dd`.
Money is always an integer amount of cents (`amountCents`), currency `BRL`.

## Conventions

- Base path: `/api/v1` (webhooks live at `/webhooks/...`).
- Auth: `Authorization: Bearer <accessToken>` on every `/api/v1/**` route except `/api/v1/auth/**`.
- The JWT carries the claims `sub` (user id), `tenant_id`, `email`, `role` (`ADMIN`). The tenant is ALWAYS taken from the token, never from the request body or path.
- Errors are RFC 7807 `application/problem+json`:
  ```json
  { "type": "about:blank", "title": "Not Found", "status": 404, "detail": "Plan 3f... not found", "instance": "/api/v1/plans/3f..." }
  ```
  Validation errors (400) add `"errors": [{ "field": "email", "message": "must be a well-formed email address" }]`.
- Status codes: 400 validation, 401 missing/invalid token or bad credentials, 403 wrong role, 404 not found (also used for resources that belong to another tenant), 409 conflict (duplicate email, invalid state transition).

## Auth

### POST /api/v1/auth/signup
Creates a tenant and its first ADMIN user. Emails are globally unique.
Request: `{ "tenantName": "Acme Gym", "email": "owner@acme.test", "password": "s3cret-pass" }`
(`tenantName` 2..120 chars, `email` valid, `password` 8..72 chars)
Response 201:
```json
{ "tenantId": "uuid", "userId": "uuid", "accessToken": "jwt", "tokenType": "Bearer", "expiresIn": 3600 }
```
409 when the email is already registered.

### POST /api/v1/auth/login
Request: `{ "email": "owner@acme.test", "password": "s3cret-pass" }`
Response 200: same shape as signup. 401 on bad credentials.

### GET /api/v1/me
Response 200: `{ "userId": "uuid", "tenantId": "uuid", "tenantName": "Acme Gym", "email": "owner@acme.test", "role": "ADMIN" }`

## Plans

PlanResponse:
```json
{ "id": "uuid", "name": "Pro", "description": "Pro plan", "amountCents": 4990, "currency": "BRL", "interval": "MONTHLY", "active": true, "createdAt": "..." }
```
- `POST /api/v1/plans` body `{ "name": "Pro", "description": "optional", "amountCents": 4990, "interval": "MONTHLY" }` -> 201 PlanResponse. (`interval`: `MONTHLY` | `YEARLY`, `amountCents` >= 100)
- `GET /api/v1/plans` -> 200 `PlanResponse[]` (newest first)
- `GET /api/v1/plans/{id}` -> 200 PlanResponse | 404
- `PUT /api/v1/plans/{id}` body same as POST -> 200 PlanResponse
- `DELETE /api/v1/plans/{id}` -> 204 (soft delete: `active=false`; inactive plans cannot be subscribed, 409)

## Customers

CustomerResponse:
```json
{ "id": "uuid", "name": "Jane Doe", "email": "jane@example.test", "document": "24971563792", "gatewayCustomerId": "cus_fake_...", "createdAt": "..." }
```
- `POST /api/v1/customers` body `{ "name": "Jane Doe", "email": "jane@example.test", "document": "24971563792" }` (`document` = CPF or CNPJ digits, 11 or 14 digits) -> 201. The customer is also registered in the payment gateway.
- `GET /api/v1/customers` -> 200 `CustomerResponse[]`
- `GET /api/v1/customers/{id}` -> 200 | 404

## Subscriptions

Status: `PENDING` -> `ACTIVE` (payment confirmed/received) -> `OVERDUE` (payment overdue) -> `ACTIVE` again when paid. Any non canceled status -> `CANCELED`.

SubscriptionResponse:
```json
{
  "id": "uuid", "customerId": "uuid", "customerName": "Jane Doe", "planId": "uuid", "planName": "Pro",
  "amountCents": 4990, "status": "PENDING", "createdAt": "...", "activatedAt": null, "canceledAt": null,
  "latestCharge": { "id": "uuid", "gatewayPaymentId": "pay_fake_...", "status": "PENDING", "amountCents": 4990, "dueDate": "2026-10-31" }
}
```
- `POST /api/v1/subscriptions` body `{ "customerId": "uuid", "planId": "uuid" }` -> 201 SubscriptionResponse (status `PENDING`; the first charge is created in the gateway, due in 3 days).
- `GET /api/v1/subscriptions` (optional `?status=ACTIVE`) -> 200 `SubscriptionResponse[]` (newest first)
- `GET /api/v1/subscriptions/{id}` -> 200 | 404
- `POST /api/v1/subscriptions/{id}/cancel` -> 200 SubscriptionResponse with `CANCELED` (409 if already canceled)
- `GET /api/v1/subscriptions/{id}/charges` -> 200 `ChargeResponse[]`

ChargeResponse:
```json
{ "id": "uuid", "subscriptionId": "uuid", "customerId": "uuid", "gatewayPaymentId": "pay_fake_...", "amountCents": 4990, "status": "PENDING", "dueDate": "2026-10-31", "paidAt": null, "createdAt": "..." }
```
Charge status: `PENDING` | `CONFIRMED` | `RECEIVED` | `OVERDUE` | `CANCELED`.
- `GET /api/v1/charges` -> 200 `ChargeResponse[]`

## Dashboard

`GET /api/v1/dashboard` -> 200
```json
{
  "plans": 1, "customers": 1, "subscriptions": 1,
  "subscriptionsByStatus": { "PENDING": 0, "ACTIVE": 1, "OVERDUE": 0, "CANCELED": 0 },
  "receivedAmountCents": 4990
}
```
(`receivedAmountCents` = sum of charges in `CONFIRMED` or `RECEIVED`.)

## Webhooks (Asaas format)

### POST /webhooks/asaas
Header `asaas-access-token: <shared secret>` (property `tenantpay.asaas.webhook-token`, env `TENANTPAY_ASAAS_WEBHOOK_TOKEN`, default in dev/compose `dev-webhook-token`). Wrong or missing token -> 401 problem.
Body (subset of the Asaas v3 payload, unknown fields ignored):
```json
{
  "id": "evt_05b708f961d739ea7eba7e4db318f621&368604920",
  "event": "PAYMENT_CONFIRMED",
  "dateCreated": "2026-10-28 12:00:00",
  "payment": {
    "object": "payment", "id": "pay_fake_...", "customer": "cus_fake_...",
    "value": 49.90, "netValue": 48.90, "billingType": "PIX", "status": "CONFIRMED",
    "dueDate": "2026-10-31", "paymentDate": "2026-10-28", "externalReference": "<charge uuid>"
  }
}
```
Response 200 `{ "received": true, "duplicate": false, "eventId": "evt_..." }`. A repeated `id` returns 200 with `"duplicate": true` and is NOT processed again.
The event is stored in the `webhook_events` inbox (status `RECEIVED`) and processed asynchronously:
`PAYMENT_CONFIRMED` / `PAYMENT_RECEIVED` -> charge CONFIRMED/RECEIVED and subscription ACTIVE; `PAYMENT_OVERDUE` -> charge OVERDUE and subscription OVERDUE. Other events are stored and marked `IGNORED`.
Failures retry with exponential backoff (`FAILED`, attempts++), and after `max-attempts` the event becomes `DEAD`.

Inbox status: `RECEIVED` | `PROCESSING` | `PROCESSED` | `IGNORED` | `FAILED` | `DEAD`.

## Admin (role ADMIN, scoped to the caller tenant)

WebhookEventResponse:
```json
{ "id": "uuid", "eventId": "evt_...", "eventType": "PAYMENT_CONFIRMED", "status": "DEAD", "attempts": 5, "lastError": "...", "receivedAt": "...", "processedAt": null, "nextAttemptAt": null }
```
- `GET /api/v1/admin/webhook-events` (optional `?status=DEAD`) -> 200 `WebhookEventResponse[]`
- `POST /api/v1/admin/webhook-events/{id}/reprocess` -> 202 WebhookEventResponse (status reset to `RECEIVED`, attempts reset to 0). 409 if status is `PROCESSED` or `IGNORED`.

## Dev tools (only when `tenantpay.dev-tools.enabled=true`, env `TENANTPAY_DEV_TOOLS_ENABLED`; otherwise 404)

### POST /api/v1/dev/webhooks/simulate
Authenticated. Body `{ "subscriptionId": "uuid", "event": "PAYMENT_CONFIRMED" }`.
Builds an Asaas webhook payload for the latest charge of that subscription (tenant checked) and pushes it through the same ingestion path as `/webhooks/asaas`.
Response 200 `{ "received": true, "duplicate": false, "eventId": "evt_sim_..." }`.

### GET /api/v1/dev/config
Unauthenticated-safe info for the panel: `{ "devTools": true, "gateway": "fake" }` (also exposed when dev tools are disabled, with `devTools: false`). This route is public.

## Ops

- `GET /actuator/health` -> `{ "status": "UP" }` (public)
- OpenAPI: `GET /v3/api-docs`, Swagger UI: `/swagger-ui.html` (public)

## Runtime configuration (env vars)

| Env var | Default | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:55432/tenantpay` | database |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `tenantpay` / `tenantpay` | database credentials |
| `TENANTPAY_JWT_SECRET` | dev-only 32+ byte secret | HS256 signing key |
| `TENANTPAY_JWT_TTL` | `PT1H` | access token lifetime |
| `TENANTPAY_ASAAS_WEBHOOK_TOKEN` | `dev-webhook-token` | shared secret for `/webhooks/asaas` |
| `ASAAS_API_KEY` | empty | when set, the real `AsaasGateway` is used instead of the fake |
| `ASAAS_BASE_URL` | `https://api-sandbox.asaas.com/v3` | Asaas API base URL |
| `TENANTPAY_DEV_TOOLS_ENABLED` | `false` (`true` in compose) | enables `/api/v1/dev/**` |
| `TENANTPAY_CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:15173` | CORS |

Container: the API listens on 8080 (compose maps host 18080). The jar is `target/tenantpay-billing.jar`.
The frontend container is nginx on port 80 (compose maps host 15173) and proxies `/api/`, `/webhooks/`, `/actuator/` to `http://api:8080`. The Vite dev server proxies the same prefixes to `http://localhost:18080`, so the panel always uses relative URLs.
