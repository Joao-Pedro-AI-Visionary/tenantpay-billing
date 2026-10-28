package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.domain.WebhookEvent;
import com.tenantpay.billing.domain.WebhookEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Mutable processing state of an inbox row. The raw JSON payload column is written once by the native insert in
 * {@link WebhookEventRepositoryAdapter} and is intentionally not mapped here, so updates never rewrite it.
 */
@Entity
@Table(name = "webhook_events")
public class WebhookEventEntity {
    @Id
    private UUID id;
    @Column(name = "tenant_id")
    private UUID tenantId;
    @Column(name = "event_id", nullable = false, length = 255, updatable = false)
    private String eventId;
    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private String eventType;
    @Column(name = "payment_id", length = 100, updatable = false)
    private String paymentId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookEventStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
    @Column(name = "last_error", length = 2000)
    private String lastError;
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;
    @Column(name = "processed_at")
    private Instant processedAt;

    protected WebhookEventEntity() {
    }

    static WebhookEventEntity from(WebhookEvent event) {
        WebhookEventEntity entity = new WebhookEventEntity();
        entity.id = event.id();
        entity.tenantId = event.tenantId();
        entity.eventId = event.eventId();
        entity.eventType = event.eventType();
        entity.paymentId = event.paymentId();
        entity.status = event.status();
        entity.attempts = event.attempts();
        entity.nextAttemptAt = event.nextAttemptAt();
        entity.lastError = event.lastError();
        entity.receivedAt = event.receivedAt();
        entity.processedAt = event.processedAt();
        return entity;
    }

    WebhookEvent toDomain() {
        return WebhookEvent.restore(id, tenantId, eventId, eventType, paymentId, null, status, attempts,
                nextAttemptAt, lastError, receivedAt, processedAt);
    }
}
