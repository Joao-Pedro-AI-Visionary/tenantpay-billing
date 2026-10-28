-- TenantPay Billing schema. Every business table carries tenant_id; child rows reference their parent through
-- (tenant_id, id) composite keys so that the database itself rejects cross tenant links.

CREATE TABLE tenants (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    tenant_id     UUID         NOT NULL REFERENCES tenants (id),
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN'))
);
CREATE INDEX idx_users_tenant ON users (tenant_id);

CREATE TABLE plans (
    id               UUID         PRIMARY KEY,
    tenant_id        UUID         NOT NULL REFERENCES tenants (id),
    name             VARCHAR(120) NOT NULL,
    description      VARCHAR(500),
    amount_cents     BIGINT       NOT NULL,
    currency         VARCHAR(3)   NOT NULL,
    billing_interval VARCHAR(10)  NOT NULL,
    active           BOOLEAN      NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_plans_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_plans_amount CHECK (amount_cents >= 100),
    CONSTRAINT ck_plans_currency CHECK (currency = 'BRL'),
    CONSTRAINT ck_plans_interval CHECK (billing_interval IN ('MONTHLY', 'YEARLY'))
);
CREATE INDEX idx_plans_tenant_created ON plans (tenant_id, created_at DESC);

CREATE TABLE customers (
    id                  UUID         PRIMARY KEY,
    tenant_id           UUID         NOT NULL REFERENCES tenants (id),
    name                VARCHAR(120) NOT NULL,
    email               VARCHAR(254) NOT NULL,
    document            VARCHAR(14)  NOT NULL,
    gateway_customer_id VARCHAR(100) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_customers_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_customers_document CHECK (document ~ '^([0-9]{11}|[0-9]{14})$')
);
CREATE INDEX idx_customers_tenant_created ON customers (tenant_id, created_at DESC);

CREATE TABLE subscriptions (
    id           UUID        PRIMARY KEY,
    tenant_id    UUID        NOT NULL REFERENCES tenants (id),
    customer_id  UUID        NOT NULL,
    plan_id      UUID        NOT NULL,
    amount_cents BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    canceled_at  TIMESTAMPTZ,
    CONSTRAINT uk_subscriptions_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_subscriptions_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id),
    CONSTRAINT fk_subscriptions_plan FOREIGN KEY (tenant_id, plan_id) REFERENCES plans (tenant_id, id),
    CONSTRAINT ck_subscriptions_amount CHECK (amount_cents >= 100),
    CONSTRAINT ck_subscriptions_status CHECK (status IN ('PENDING', 'ACTIVE', 'OVERDUE', 'CANCELED'))
);
CREATE INDEX idx_subscriptions_tenant_created ON subscriptions (tenant_id, created_at DESC);
CREATE INDEX idx_subscriptions_tenant_status ON subscriptions (tenant_id, status);

CREATE TABLE charges (
    id                 UUID         PRIMARY KEY,
    tenant_id          UUID         NOT NULL REFERENCES tenants (id),
    subscription_id    UUID         NOT NULL,
    customer_id        UUID         NOT NULL,
    gateway_payment_id VARCHAR(100) NOT NULL,
    amount_cents       BIGINT       NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    due_date           DATE         NOT NULL,
    paid_at            TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_charges_gateway_payment_id UNIQUE (gateway_payment_id),
    CONSTRAINT fk_charges_subscription FOREIGN KEY (tenant_id, subscription_id)
        REFERENCES subscriptions (tenant_id, id),
    CONSTRAINT fk_charges_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id),
    CONSTRAINT ck_charges_amount CHECK (amount_cents > 0),
    CONSTRAINT ck_charges_status CHECK (status IN ('PENDING', 'CONFIRMED', 'RECEIVED', 'OVERDUE', 'CANCELED'))
);
CREATE INDEX idx_charges_tenant_created ON charges (tenant_id, created_at DESC);
CREATE INDEX idx_charges_tenant_subscription ON charges (tenant_id, subscription_id);

CREATE TABLE webhook_events (
    id              UUID          PRIMARY KEY,
    tenant_id       UUID          REFERENCES tenants (id),
    event_id        VARCHAR(255)  NOT NULL,
    event_type      VARCHAR(64)   NOT NULL,
    payment_id      VARCHAR(100),
    payload         JSONB         NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    attempts        INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error      VARCHAR(2000),
    received_at     TIMESTAMPTZ   NOT NULL,
    processed_at    TIMESTAMPTZ,
    CONSTRAINT uk_webhook_events_event_id UNIQUE (event_id),
    CONSTRAINT ck_webhook_events_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_webhook_events_status
        CHECK (status IN ('RECEIVED', 'PROCESSING', 'PROCESSED', 'IGNORED', 'FAILED', 'DEAD'))
);
CREATE INDEX idx_webhook_events_tenant_received ON webhook_events (tenant_id, received_at DESC);
-- Partial index that keeps the poller query cheap: finished events are never scanned.
CREATE INDEX idx_webhook_events_due ON webhook_events (next_attempt_at, received_at)
    WHERE status IN ('RECEIVED', 'FAILED', 'PROCESSING');
