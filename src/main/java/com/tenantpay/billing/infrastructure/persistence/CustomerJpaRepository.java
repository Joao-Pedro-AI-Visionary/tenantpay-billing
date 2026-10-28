package com.tenantpay.billing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID> {
    Optional<CustomerEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<CustomerEntity> findByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId);

    List<CustomerEntity> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    long countByTenantId(UUID tenantId);
}
