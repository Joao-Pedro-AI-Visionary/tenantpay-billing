package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.application.port.PlanRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.domain.Plan;
import com.tenantpay.billing.domain.PlanInterval;

import java.util.UUID;

/** Changes name, description, price and interval. Existing subscriptions keep the price they started with. */
public class UpdatePlan {
    private final PlanRepository plans;
    private final UnitOfWork uow;

    public UpdatePlan(PlanRepository plans, UnitOfWork uow) {
        this.plans = plans;
        this.uow = uow;
    }

    public Plan execute(Command command) {
        return uow.inTransaction(() -> {
            Plan plan = plans.findById(command.tenantId(), command.planId())
                    .orElseThrow(() -> NotFoundException.of("Plan", command.planId()));
            plan.update(command.name(), command.description(), command.amountCents(), command.interval());
            plans.save(plan);
            return plan;
        });
    }

    public record Command(UUID tenantId, UUID planId, String name, String description, long amountCents,
                          PlanInterval interval) {
    }
}
