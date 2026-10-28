package com.tenantpay.billing.application;

import com.tenantpay.billing.application.port.ChargeRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.application.port.WebhookEventRepository;
import com.tenantpay.billing.domain.Charge;
import com.tenantpay.billing.domain.WebhookEvent;

import java.time.Clock;
import java.util.UUID;

/**
 * Stores a gateway notification in the inbox exactly once. Processing happens later in
 * {@link ProcessWebhookEvents}, so the gateway gets a fast 200 and a repeated delivery is acknowledged without side
 * effects.
 */
public class IngestWebhook {
    private final WebhookEventRepository events;
    private final ChargeRepository charges;
    private final UnitOfWork uow;
    private final Clock clock;

    public IngestWebhook(WebhookEventRepository events, ChargeRepository charges, UnitOfWork uow, Clock clock) {
        this.events = events;
        this.charges = charges;
        this.uow = uow;
        this.clock = clock;
    }

    public Result execute(Command command) {
        if (events.existsByEventId(command.eventId())) {
            return new Result(true, command.eventId());
        }
        return uow.inTransaction(() -> {
            UUID tenantId = command.paymentId() == null ? null
                    : charges.findByGatewayPaymentId(command.paymentId()).map(Charge::tenantId).orElse(null);
            WebhookEvent event = WebhookEvent.receive(command.eventId(), command.eventType(), command.paymentId(),
                    command.rawPayload(), tenantId, clock.instant());
            boolean inserted = events.insertIfAbsent(event);
            return new Result(!inserted, command.eventId());
        });
    }

    public record Command(String eventId, String eventType, String paymentId, String rawPayload) {
    }

    public record Result(boolean duplicate, String eventId) {
    }
}
