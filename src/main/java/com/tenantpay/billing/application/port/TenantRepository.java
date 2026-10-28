package com.tenantpay.billing.application.port;

import com.tenantpay.billing.domain.Tenant;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {
    void save(Tenant tenant);

    Optional<Tenant> findById(UUID tenantId);
}
