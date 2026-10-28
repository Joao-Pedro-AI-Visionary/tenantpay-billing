package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.application.port.SubscriptionRepository;
import com.tenantpay.billing.domain.Subscription;
import com.tenantpay.billing.domain.SubscriptionStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
class SubscriptionRepositoryAdapter implements SubscriptionRepository {
    private final SubscriptionJpaRepository subscriptions;

    SubscriptionRepositoryAdapter(SubscriptionJpaRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Override
    public void save(Subscription subscription) {
        subscriptions.save(SubscriptionEntity.from(subscription));
    }

    @Override
    public Optional<Subscription> findById(UUID tenantId, UUID subscriptionId) {
        return subscriptions.findByIdAndTenantId(subscriptionId, tenantId).map(SubscriptionEntity::toDomain);
    }

    @Override
    public Optional<Subscription> findByIdForUpdate(UUID tenantId, UUID subscriptionId) {
        return subscriptions.lockByIdAndTenantId(subscriptionId, tenantId).map(SubscriptionEntity::toDomain);
    }

    @Override
    public List<Subscription> findAll(UUID tenantId, SubscriptionStatus status) {
        List<SubscriptionEntity> rows = status == null
                ? subscriptions.findByTenantIdOrderByCreatedAtDescIdDesc(tenantId)
                : subscriptions.findByTenantIdAndStatusOrderByCreatedAtDescIdDesc(tenantId, status);
        return rows.stream().map(SubscriptionEntity::toDomain).toList();
    }

    @Override
    public Map<SubscriptionStatus, Long> countByStatus(UUID tenantId) {
        Map<SubscriptionStatus, Long> counts = new EnumMap<>(SubscriptionStatus.class);
        for (SubscriptionJpaRepository.StatusCount row : subscriptions.countByStatus(tenantId)) {
            counts.put(row.getStatus(), row.getTotal());
        }
        return counts;
    }
}
