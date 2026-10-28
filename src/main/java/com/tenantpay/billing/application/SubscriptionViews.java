package com.tenantpay.billing.application;

import com.tenantpay.billing.application.port.ChargeRepository;
import com.tenantpay.billing.application.port.CustomerRepository;
import com.tenantpay.billing.application.port.PlanRepository;
import com.tenantpay.billing.domain.Charge;
import com.tenantpay.billing.domain.Customer;
import com.tenantpay.billing.domain.Plan;
import com.tenantpay.billing.domain.Subscription;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Builds {@link SubscriptionView}s with a constant number of queries, whatever the list size. */
class SubscriptionViews {
    static final Comparator<Charge> NEWEST_FIRST = Comparator.comparing(Charge::createdAt)
            .thenComparing(Charge::id).reversed();

    private final CustomerRepository customers;
    private final PlanRepository plans;
    private final ChargeRepository charges;

    SubscriptionViews(CustomerRepository customers, PlanRepository plans, ChargeRepository charges) {
        this.customers = customers;
        this.plans = plans;
        this.charges = charges;
    }

    List<SubscriptionView> assemble(UUID tenantId, List<Subscription> subscriptions) {
        if (subscriptions.isEmpty()) {
            return List.of();
        }
        Set<UUID> customerIds = subscriptions.stream().map(Subscription::customerId).collect(Collectors.toSet());
        Set<UUID> planIds = subscriptions.stream().map(Subscription::planId).collect(Collectors.toSet());
        Set<UUID> subscriptionIds = subscriptions.stream().map(Subscription::id).collect(Collectors.toSet());

        Map<UUID, String> customerNames = customers.findAllByIds(tenantId, customerIds).stream()
                .collect(Collectors.toMap(Customer::id, Customer::name));
        Map<UUID, String> planNames = plans.findAllByIds(tenantId, planIds).stream()
                .collect(Collectors.toMap(Plan::id, Plan::name));
        Map<UUID, Charge> latest = new HashMap<>();
        for (Charge charge : charges.findBySubscriptionIds(tenantId, subscriptionIds)) {
            latest.merge(charge.subscriptionId(), charge,
                    (current, candidate) -> NEWEST_FIRST.compare(current, candidate) <= 0 ? current : candidate);
        }
        return subscriptions.stream()
                .map(s -> new SubscriptionView(s, customerNames.get(s.customerId()), planNames.get(s.planId()),
                        latest.get(s.id())))
                .toList();
    }

    SubscriptionView assemble(UUID tenantId, Subscription subscription) {
        return assemble(tenantId, List.of(subscription)).get(0);
    }
}
