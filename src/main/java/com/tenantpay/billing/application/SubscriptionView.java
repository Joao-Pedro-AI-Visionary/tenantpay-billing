package com.tenantpay.billing.application;

import com.tenantpay.billing.domain.Charge;
import com.tenantpay.billing.domain.Subscription;

/** A subscription with the names and latest charge the panel shows next to it. {@code latestCharge} may be null. */
public record SubscriptionView(Subscription subscription, String customerName, String planName, Charge latestCharge) {
}
