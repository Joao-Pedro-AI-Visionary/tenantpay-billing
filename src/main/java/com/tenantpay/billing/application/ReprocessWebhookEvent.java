package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.application.port.WebhookEventRepository;
import com.tenantpay.billing.domain.WebhookEvent;

import java.util.UUID;

/** Operator action: puts a FAILED or DEAD event of the caller tenant back in the queue with fresh attempts. */
public class ReprocessWebhookEvent {
    private final WebhookEventRepository events;
    private final UnitOfWork uow;

    public ReprocessWebhookEvent(WebhookEventRepository events, UnitOfWork uow) {
        this.events = events;
        this.uow = uow;
    }

    public WebhookEvent execute(UUID tenantId, UUID eventId) {
        return uow.inTransaction(() -> {
            WebhookEvent event = events.findById(tenantId, eventId)
                    .orElseThrow(() -> NotFoundException.of("Webhook event", eventId));
            event.resetForReprocess();
            events.save(event);
            return event;
        });
    }
}
