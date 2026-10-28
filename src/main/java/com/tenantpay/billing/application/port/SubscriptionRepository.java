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

    /**
     * Loads the subscription and locks its row until the surrounding transaction ends. Every write path that can race
     * (cancel versus a payment webhook) goes through this method, so the later writer always sees the earlier commit.
     */
    Optional<Subscription> findByIdForUpdate(UUID tenantId, UUID subscriptionId);

    /** Newest first. {@code status} is optional (null means all). */
    List<Subscription> findAll(UUID tenantId, SubscriptionStatus status);

    Map<SubscriptionStatus, Long> countByStatus(UUID tenantId);
}
