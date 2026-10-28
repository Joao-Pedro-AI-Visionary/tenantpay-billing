package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.application.port.ChargeRepository;
import com.tenantpay.billing.application.port.CustomerRepository;
import com.tenantpay.billing.application.port.PaymentGateway;
import com.tenantpay.billing.application.port.PlanRepository;
import com.tenantpay.billing.application.port.SubscriptionRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.domain.Charge;
import com.tenantpay.billing.domain.Customer;
import com.tenantpay.billing.domain.Plan;
import com.tenantpay.billing.domain.Subscription;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Subscribes a customer to a plan: the subscription starts PENDING and its first charge is created in the gateway,
 * due in {@value #DUE_IN_DAYS} days, with the local charge id as external reference.
 *
 * <p>The gateway call happens between two short transactions so that no database connection is held during network
 * I/O. If the final insert fails, the gateway charge is left orphaned; it carries the charge id as reference and is
 * never paid against a local record, which is the accepted trade-off.
 */
public class Subscribe {
    public static final int DUE_IN_DAYS = 3;

    private final CustomerRepository customers;
    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final ChargeRepository charges;
    private final PaymentGateway gateway;
    private final UnitOfWork uow;
    private final Clock clock;

    public Subscribe(CustomerRepository customers, PlanRepository plans, SubscriptionRepository subscriptions,
                     ChargeRepository charges, PaymentGateway gateway, UnitOfWork uow, Clock clock) {
        this.customers = customers;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.charges = charges;
        this.gateway = gateway;
        this.uow = uow;
        this.clock = clock;
    }

    public SubscriptionView execute(Command command) {
        Instant now = clock.instant();
        Prepared prepared = uow.inTransaction(() -> {
            Customer customer = customers.findById(command.tenantId(), command.customerId())
                    .orElseThrow(() -> NotFoundException.of("Customer", command.customerId()));
            Plan plan = plans.findById(command.tenantId(), command.planId())
                    .orElseThrow(() -> NotFoundException.of("Plan", command.planId()));
            return new Prepared(customer, plan, Subscription.start(command.tenantId(), customer, plan, now));
        });

        UUID chargeId = UUID.randomUUID();
        LocalDate dueDate = LocalDate.ofInstant(now, clock.getZone()).plusDays(DUE_IN_DAYS);
        String paymentId = gateway.createCharge(new PaymentGateway.NewCharge(
                prepared.customer().gatewayCustomerId(), prepared.plan().price(), dueDate,
                "Subscription to plan " + prepared.plan().name(), chargeId.toString()));
        Charge charge = Charge.create(chargeId, prepared.subscription(), paymentId, dueDate, now);

        uow.run(() -> {
            subscriptions.save(prepared.subscription());
            charges.save(charge);
        });
        return new SubscriptionView(prepared.subscription(), prepared.customer().name(), prepared.plan().name(),
                charge);
    }

    private record Prepared(Customer customer, Plan plan, Subscription subscription) {
    }

    public record Command(UUID tenantId, UUID customerId, UUID planId) {
    }
}
