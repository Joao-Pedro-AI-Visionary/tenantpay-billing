package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, UUID> {
    Optional<SubscriptionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SubscriptionEntity> findByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId);

    List<SubscriptionEntity> findByTenantIdAndStatusOrderByCreatedAtDescIdDesc(UUID tenantId,
                                                                               SubscriptionStatus status);

    @Query("select s.status as status, count(s) as total from SubscriptionEntity s "
            + "where s.tenantId = :tenantId group by s.status")
    List<StatusCount> countByStatus(@Param("tenantId") UUID tenantId);

    interface StatusCount {
        SubscriptionStatus getStatus();

        long getTotal();
    }
}
