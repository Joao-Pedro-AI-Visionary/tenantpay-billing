package com.tenantpay.billing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanJpaRepository extends JpaRepository<PlanEntity, UUID> {
    Optional<PlanEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<PlanEntity> findByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId);

    List<PlanEntity> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    long countByTenantIdAndActiveTrue(UUID tenantId);
}
