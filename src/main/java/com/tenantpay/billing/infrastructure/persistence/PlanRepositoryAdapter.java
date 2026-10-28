package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.application.port.PlanRepository;
import com.tenantpay.billing.domain.Plan;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class PlanRepositoryAdapter implements PlanRepository {
    private final PlanJpaRepository plans;

    PlanRepositoryAdapter(PlanJpaRepository plans) {
        this.plans = plans;
    }

    @Override
    public void save(Plan plan) {
        plans.save(PlanEntity.from(plan));
    }

    @Override
    public Optional<Plan> findById(UUID tenantId, UUID planId) {
        return plans.findByIdAndTenantId(planId, tenantId).map(PlanEntity::toDomain);
    }

    @Override
    public List<Plan> findAll(UUID tenantId) {
        return plans.findByTenantIdOrderByCreatedAtDescIdDesc(tenantId).stream().map(PlanEntity::toDomain).toList();
    }

    @Override
    public List<Plan> findAllByIds(UUID tenantId, Collection<UUID> planIds) {
        return plans.findByTenantIdAndIdIn(tenantId, planIds).stream().map(PlanEntity::toDomain).toList();
    }

    @Override
    public long countActive(UUID tenantId) {
        return plans.countByTenantIdAndActiveTrue(tenantId);
    }
}
