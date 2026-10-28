package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.application.port.PlanRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.domain.Plan;

import java.util.UUID;

public class GetPlan {
    private final PlanRepository plans;
    private final UnitOfWork uow;

    public GetPlan(PlanRepository plans, UnitOfWork uow) {
        this.plans = plans;
        this.uow = uow;
    }

    public Plan execute(UUID tenantId, UUID planId) {
        return uow.inTransaction(() -> plans.findById(tenantId, planId)
                .orElseThrow(() -> NotFoundException.of("Plan", planId)));
    }
}
