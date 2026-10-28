package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.domain.ChargeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargeJpaRepository extends JpaRepository<ChargeEntity, UUID> {
    List<ChargeEntity> findByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId);

    List<ChargeEntity> findByTenantIdAndSubscriptionIdInOrderByCreatedAtDescIdDesc(UUID tenantId,
                                                                                    Collection<UUID> ids);

    Optional<ChargeEntity> findByGatewayPaymentId(String gatewayPaymentId);

    /** Scalar projection: nothing enters the persistence context, so a later entity read is fresh. */
    @Query("select c.tenantId as tenantId, c.subscriptionId as subscriptionId from ChargeEntity c "
            + "where c.gatewayPaymentId = :gatewayPaymentId")
    Optional<OwnerRow> findOwnerByGatewayPaymentId(@Param("gatewayPaymentId") String gatewayPaymentId);

    interface OwnerRow {
        UUID getTenantId();

        UUID getSubscriptionId();
    }

    @Query("select coalesce(sum(c.amountCents), 0) from ChargeEntity c "
            + "where c.tenantId = :tenantId and c.status in :statuses")
    long sumAmount(@Param("tenantId") UUID tenantId, @Param("statuses") Collection<ChargeStatus> statuses);
}
