package com.tenantpay.billing.application;

import com.tenantpay.billing.application.port.PlanRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.domain.Plan;
import com.tenantpay.billing.domain.PlanInterval;

import java.time.Clock;
import java.util.UUID;

public class CreatePlan {
    private final PlanRepository plans;
    private final UnitOfWork uow;
    private final Clock clock;

    public CreatePlan(PlanRepository plans, UnitOfWork uow, Clock clock) {
        this.plans = plans;
        this.uow = uow;
        this.clock = clock;
    }

    public Plan execute(Command command) {
        Plan plan = Plan.create(command.tenantId(), command.name(), command.description(), command.amountCents(),
                command.interval(), clock.instant());
        uow.run(() -> plans.save(plan));
        return plan;
    }

    public record Command(UUID tenantId, String name, String description, long amountCents, PlanInterval interval) {
    }
}
