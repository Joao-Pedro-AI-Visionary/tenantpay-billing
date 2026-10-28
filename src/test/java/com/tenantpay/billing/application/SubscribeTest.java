package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.domain.Charge;
import com.tenantpay.billing.domain.ChargeStatus;
import com.tenantpay.billing.domain.Customer;
import com.tenantpay.billing.domain.Fixtures;
import com.tenantpay.billing.domain.InvalidStateTransitionException;
import com.tenantpay.billing.domain.Plan;
import com.tenantpay.billing.domain.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscribeTest {
    private final UUID tenant = UUID.randomUUID();
    private final InMemoryPorts.Customers customers = new InMemoryPorts.Customers();
    private final InMemoryPorts.Plans plans = new InMemoryPorts.Plans();
    private final InMemoryPorts.Subscriptions subscriptions = new InMemoryPorts.Subscriptions();
    private final InMemoryPorts.Charges charges = new InMemoryPorts.Charges();
    private final InMemoryPorts.RecordingGateway gateway = new InMemoryPorts.RecordingGateway();
    private final InMemoryPorts.CountingUnitOfWork uow = new InMemoryPorts.CountingUnitOfWork();
    private final MutableClock clock = new MutableClock(Fixtures.NOW);
    private final Subscribe subscribe = new Subscribe(customers, plans, subscriptions, charges, gateway, uow, clock);
    private final CancelSubscription cancel = new CancelSubscription(subscriptions, charges, customers, plans,
            gateway, uow, clock);

    private Customer customer;
    private Plan plan;

    @BeforeEach
    void seed() {
        customer = Fixtures.customer(tenant);
        plan = Fixtures.plan(tenant);
        customers.save(customer);
        plans.save(plan);
    }

    @Test
    void createsPendingSubscriptionAndFirstChargeInTheGateway() {
        SubscriptionView view = subscribe.execute(new Subscribe.Command(tenant, customer.id(), plan.id()));

        assertThat(view.subscription().status()).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(view.customerName()).isEqualTo("Jane Doe");
        assertThat(view.planName()).isEqualTo("Pro");
        Charge charge = view.latestCharge();
        assertThat(charge.status()).isEqualTo(ChargeStatus.PENDING);
        assertThat(charge.dueDate()).isEqualTo(LocalDate.parse("2026-10-31"));
        assertThat(charge.gatewayPaymentId()).isEqualTo("pay_" + charge.id());

        assertThat(gateway.charges).singleElement().satisfies(sent -> {
            assertThat(sent.externalReference()).isEqualTo(charge.id().toString());
            assertThat(sent.gatewayCustomerId()).isEqualTo(customer.gatewayCustomerId());
            assertThat(sent.amount().amountCents()).isEqualTo(4990);
        });
        assertThat(subscriptions.rows).containsKey(view.subscription().id());
        assertThat(charges.rows).containsKey(charge.id());
    }

    @Test
    void customerOrPlanOfAnotherTenantIsNotFound() {
        UUID otherTenant = UUID.randomUUID();

        assertThatThrownBy(() -> subscribe.execute(new Subscribe.Command(otherTenant, customer.id(), plan.id())))
                .isInstanceOf(NotFoundException.class);
        Customer foreign = Fixtures.customer(otherTenant);
        customers.save(foreign);
        assertThatThrownBy(() -> subscribe.execute(new Subscribe.Command(tenant, foreign.id(), plan.id())))
                .isInstanceOf(NotFoundException.class);
        assertThat(gateway.charges).isEmpty();
    }

    @Test
    void inactivePlanIsRejectedBeforeCallingTheGateway() {
        plan.deactivate();

        assertThatThrownBy(() -> subscribe.execute(new Subscribe.Command(tenant, customer.id(), plan.id())))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(gateway.charges).isEmpty();
        assertThat(subscriptions.rows).isEmpty();
    }

    @Test
    void cancelCancelsOpenChargesInTheGatewayAndRefusesASecondTime() {
        SubscriptionView view = subscribe.execute(new Subscribe.Command(tenant, customer.id(), plan.id()));
        UUID id = view.subscription().id();

        SubscriptionView canceled = cancel.execute(tenant, id);

        assertThat(canceled.subscription().status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(canceled.latestCharge().status()).isEqualTo(ChargeStatus.CANCELED);
        assertThat(gateway.canceled).containsExactly(view.latestCharge().gatewayPaymentId());
        assertThat(subscriptions.locked).as("cancel locks the subscription row").containsExactly(id);
        assertThatThrownBy(() -> cancel.execute(tenant, id)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> cancel.execute(UUID.randomUUID(), id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void dashboardCountsOnlyTheCallerTenant() {
        subscribe.execute(new Subscribe.Command(tenant, customer.id(), plan.id()));
        Charge charge = charges.rows.values().iterator().next();
        charge.confirm(Fixtures.NOW);
        UUID other = UUID.randomUUID();
        plans.save(Fixtures.plan(other));

        GetDashboard.View view = new GetDashboard(plans, customers, subscriptions, charges, uow).execute(tenant);

        assertThat(view.plans()).isEqualTo(1);
        assertThat(view.customers()).isEqualTo(1);
        assertThat(view.subscriptions()).isEqualTo(1);
        assertThat(view.subscriptionsByStatus()).containsEntry(SubscriptionStatus.PENDING, 1L)
                .containsEntry(SubscriptionStatus.ACTIVE, 0L);
        assertThat(view.receivedAmountCents()).isEqualTo(4990);
    }
}
