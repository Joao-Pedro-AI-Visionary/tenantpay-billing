package com.tenantpay.billing.application.port;

import com.tenantpay.billing.domain.Subscription;
import com.tenantpay.billing.domain.SubscriptionStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository {
    void save(Subscription subscription);

    Optional<Subscription> findById(UUID tenantId, UUID subscriptionId);

    /** Newest first. {@code status} is optional (null means all). */
    List<Subscription> findAll(UUID tenantId, SubscriptionStatus status);

    Map<SubscriptionStatus, Long> countByStatus(UUID tenantId);
}
