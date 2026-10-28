package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.application.port.ChargeRepository;
import com.tenantpay.billing.domain.Charge;
import com.tenantpay.billing.domain.ChargeStatus;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class ChargeRepositoryAdapter implements ChargeRepository {
    private static final List<ChargeStatus> PAID = List.of(ChargeStatus.CONFIRMED, ChargeStatus.RECEIVED);

    private final ChargeJpaRepository charges;

    ChargeRepositoryAdapter(ChargeJpaRepository charges) {
        this.charges = charges;
    }

    @Override
    public void save(Charge charge) {
        charges.save(ChargeEntity.from(charge));
    }

    @Override
    public List<Charge> findAll(UUID tenantId) {
        return charges.findByTenantIdOrderByCreatedAtDescIdDesc(tenantId).stream().map(ChargeEntity::toDomain)
                .toList();
    }

    @Override
    public List<Charge> findBySubscriptionIds(UUID tenantId, Collection<UUID> subscriptionIds) {
        if (subscriptionIds.isEmpty()) {
            return List.of();
        }
        return charges.findByTenantIdAndSubscriptionIdInOrderByCreatedAtDescIdDesc(tenantId, subscriptionIds)
                .stream().map(ChargeEntity::toDomain).toList();
    }

    @Override
    public Optional<Charge> findByGatewayPaymentId(String gatewayPaymentId) {
        return charges.findByGatewayPaymentId(gatewayPaymentId).map(ChargeEntity::toDomain);
    }

    @Override
    public long sumPaidAmountCents(UUID tenantId) {
        return charges.sumAmount(tenantId, PAID);
    }
}
