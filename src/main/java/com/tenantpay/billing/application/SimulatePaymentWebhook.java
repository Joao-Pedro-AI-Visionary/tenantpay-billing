package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.application.port.ChargeRepository;
import com.tenantpay.billing.application.port.CustomerRepository;
import com.tenantpay.billing.application.port.SubscriptionRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.domain.Charge;
import com.tenantpay.billing.domain.Customer;
import com.tenantpay.billing.domain.DomainValidationException;
import com.tenantpay.billing.domain.Money;
import com.tenantpay.billing.domain.Subscription;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Development helper: builds an Asaas-shaped notification for the latest charge of a subscription and pushes it
 * through the regular ingestion path, so the panel can demo the asynchronous flow without a real gateway.
 */
public class SimulatePaymentWebhook {
    private static final Pattern EVENT_TYPE = Pattern.compile("[A-Z][A-Z_]{2,63}");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private final SubscriptionRepository subscriptions;
    private final ChargeRepository charges;
    private final CustomerRepository customers;
    private final IngestWebhook ingest;
    private final UnitOfWork uow;
    private final Clock clock;

    public SimulatePaymentWebhook(SubscriptionRepository subscriptions, ChargeRepository charges,
                                  CustomerRepository customers, IngestWebhook ingest, UnitOfWork uow, Clock clock) {
        this.subscriptions = subscriptions;
        this.charges = charges;
        this.customers = customers;
        this.ingest = ingest;
        this.uow = uow;
        this.clock = clock;
    }

    public IngestWebhook.Result execute(UUID tenantId, UUID subscriptionId, String eventType) {
        if (eventType == null || !EVENT_TYPE.matcher(eventType).matches()) {
            throw new DomainValidationException("event must be an upper case event name such as PAYMENT_CONFIRMED");
        }
        Target target = uow.inTransaction(() -> {
            Subscription subscription = subscriptions.findById(tenantId, subscriptionId)
                    .orElseThrow(() -> NotFoundException.of("Subscription", subscriptionId));
            Charge latest = charges.findBySubscriptionIds(tenantId, List.of(subscriptionId)).stream()
                    .min(SubscriptionViews.NEWEST_FIRST)
                    .orElseThrow(() -> new NotFoundException("Subscription " + subscriptionId + " has no charge"));
            Customer customer = customers.findById(tenantId, subscription.customerId())
                    .orElseThrow(() -> NotFoundException.of("Customer", subscription.customerId()));
            return new Target(latest, customer);
        });
        String eventId = "evt_sim_" + UUID.randomUUID().toString().replace("-", "");
        String payload = payload(eventId, eventType, target, clock.instant());
        return ingest.execute(new IngestWebhook.Command(eventId, eventType, target.charge().gatewayPaymentId(),
                payload));
    }

    private static String payload(String eventId, String eventType, Target target, Instant now) {
        Charge charge = target.charge();
        String paymentStatus = switch (eventType) {
            case ProcessWebhookEvents.PAYMENT_CONFIRMED -> "CONFIRMED";
            case ProcessWebhookEvents.PAYMENT_RECEIVED -> "RECEIVED";
            case ProcessWebhookEvents.PAYMENT_OVERDUE -> "OVERDUE";
            default -> charge.status().name();
        };
        String value = Money.brl(charge.amountCents()).toDecimal().toPlainString();
        // Every interpolated value is an identifier, a number or a validated upper case token, so no escaping needed.
        return "{\"id\":\"" + eventId + "\","
                + "\"event\":\"" + eventType + "\","
                + "\"dateCreated\":\"" + DATE_TIME.format(now) + "\","
                + "\"payment\":{"
                + "\"object\":\"payment\","
                + "\"id\":\"" + charge.gatewayPaymentId() + "\","
                + "\"customer\":\"" + target.customer().gatewayCustomerId() + "\","
                + "\"value\":" + value + ","
                + "\"netValue\":" + value + ","
                + "\"billingType\":\"PIX\","
                + "\"status\":\"" + paymentStatus + "\","
                + "\"dueDate\":\"" + charge.dueDate() + "\","
                + "\"paymentDate\":\"" + DATE.format(now) + "\","
                + "\"externalReference\":\"" + charge.id() + "\"}}";
    }

    private record Target(Charge charge, Customer customer) {
    }
}
