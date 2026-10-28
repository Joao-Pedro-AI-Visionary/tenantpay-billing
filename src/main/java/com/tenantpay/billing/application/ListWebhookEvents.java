package com.tenantpay.billing.application;

import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.application.port.WebhookEventRepository;
import com.tenantpay.billing.domain.WebhookEvent;
import com.tenantpay.billing.domain.WebhookEventStatus;

import java.util.List;
import java.util.UUID;

/** Inbox view of one tenant. Events whose charge is unknown have no tenant and are not listed. */
public class ListWebhookEvents {
    private final WebhookEventRepository events;
    private final UnitOfWork uow;

    public ListWebhookEvents(WebhookEventRepository events, UnitOfWork uow) {
        this.events = events;
        this.uow = uow;
    }

    /** {@code status} is optional. */
    public List<WebhookEvent> execute(UUID tenantId, WebhookEventStatus status) {
        return uow.inTransaction(() -> events.findAll(tenantId, status));
    }
}
