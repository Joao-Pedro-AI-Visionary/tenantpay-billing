package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.application.port.ChargeRepository;
import com.tenantpay.billing.application.port.CustomerRepository;
import com.tenantpay.billing.application.port.PaymentGateway;
import com.tenantpay.billing.application.port.PlanRepository;
import com.tenantpay.billing.application.port.SubscriptionRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.domain.Charge;
import com.tenantpay.billing.domain.Subscription;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Cancels a subscription and its open charges. The gateway cancellation runs inside the transaction on purpose: if
 * the gateway refuses, nothing changes locally and the caller can retry.
 */
public class CancelSubscription {
    private final SubscriptionRepository subscriptions;
    private final ChargeRepository charges;
    private final PaymentGateway gateway;
    private final SubscriptionViews views;
    private final UnitOfWork uow;
    private final Clock clock;

    public CancelSubscription(SubscriptionRepository subscriptions, ChargeRepository charges,
                              CustomerRepository customers, PlanRepository plans, PaymentGateway gateway,
                              UnitOfWork uow, Clock clock) {
        this.subscriptions = subscriptions;
        this.charges = charges;
        this.gateway = gateway;
        this.views = new SubscriptionViews(customers, plans, charges);
        this.uow = uow;
        this.clock = clock;
    }

    public SubscriptionView execute(UUID tenantId, UUID subscriptionId) {
        return uow.inTransaction(() -> {
            Subscription subscription = subscriptions.findByIdForUpdate(tenantId, subscriptionId)
                    .orElseThrow(() -> NotFoundException.of("Subscription", subscriptionId));
            subscription.cancel(clock.instant());
            List<Charge> open = charges.findBySubscriptionIds(tenantId, List.of(subscriptionId)).stream()
                    .filter(Charge::isOpen)
                    .toList();
            for (Charge charge : open) {
                gateway.cancelCharge(charge.gatewayPaymentId());
                charge.cancel();
                charges.save(charge);
            }
            subscriptions.save(subscription);
            return views.assemble(tenantId, subscription);
        });
    }
}
