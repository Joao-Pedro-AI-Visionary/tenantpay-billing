package com.tenantpay.billing.application.port;

import com.tenantpay.billing.domain.Plan;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository {
    void save(Plan plan);

    Optional<Plan> findById(UUID tenantId, UUID planId);

    /** Newest first. */
    List<Plan> findAll(UUID tenantId);

    List<Plan> findAllByIds(UUID tenantId, Collection<UUID> planIds);

    long countActive(UUID tenantId);
}
