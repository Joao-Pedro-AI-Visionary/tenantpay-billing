package com.tenantpay.billing.application;

import com.tenantpay.billing.application.port.PlanRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.domain.Plan;

import java.util.List;
import java.util.UUID;

public class ListPlans {
    private final PlanRepository plans;
    private final UnitOfWork uow;

    public ListPlans(PlanRepository plans, UnitOfWork uow) {
        this.plans = plans;
        this.uow = uow;
    }

    public List<Plan> execute(UUID tenantId) {
        return uow.inTransaction(() -> plans.findAll(tenantId));
    }
}
