package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.application.port.PlanRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.domain.Plan;

import java.util.UUID;

/** Soft delete: the plan stays for history but cannot be subscribed anymore. */
public class DeactivatePlan {
    private final PlanRepository plans;
    private final UnitOfWork uow;

    public DeactivatePlan(PlanRepository plans, UnitOfWork uow) {
        this.plans = plans;
        this.uow = uow;
    }

    public void execute(UUID tenantId, UUID planId) {
        uow.run(() -> {
            Plan plan = plans.findById(tenantId, planId).orElseThrow(() -> NotFoundException.of("Plan", planId));
            plan.deactivate();
            plans.save(plan);
        });
    }
}
