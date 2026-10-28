package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.application.port.ChargeRepository;
import com.tenantpay.billing.application.port.SubscriptionRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.domain.Charge;

import java.util.List;
import java.util.UUID;

public class ListCharges {
    private final ChargeRepository charges;
    private final SubscriptionRepository subscriptions;
    private final UnitOfWork uow;

    public ListCharges(ChargeRepository charges, SubscriptionRepository subscriptions, UnitOfWork uow) {
        this.charges = charges;
        this.subscriptions = subscriptions;
        this.uow = uow;
    }

    public List<Charge> all(UUID tenantId) {
        return uow.inTransaction(() -> charges.findAll(tenantId));
    }

    /** Charges of one subscription; 404 when the subscription is not visible to the tenant. */
    public List<Charge> ofSubscription(UUID tenantId, UUID subscriptionId) {
        return uow.inTransaction(() -> {
            subscriptions.findById(tenantId, subscriptionId)
                    .orElseThrow(() -> NotFoundException.of("Subscription", subscriptionId));
            return charges.findBySubscriptionIds(tenantId, List.of(subscriptionId)).stream()
                    .sorted(SubscriptionViews.NEWEST_FIRST)
                    .toList();
        });
    }
}
