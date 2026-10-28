package com.tenantpay.billing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.tenantpay.billing.domain.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanTest {

    @Test
    void enforcesMinimumAmount() {
        UUID tenant = UUID.randomUUID();
        assertThatThrownBy(() -> Plan.create(tenant, "Cheap", null, 99, PlanInterval.MONTHLY, NOW))
                .isInstanceOf(DomainValidationException.class);

        Plan plan = Plan.create(tenant, "  Pro  ", "  ", 100, PlanInterval.YEARLY, NOW);
        assertThat(plan.name()).isEqualTo("Pro");
        assertThat(plan.description()).isNull();
        assertThat(plan.price()).isEqualTo(Money.brl(100));
        assertThat(plan.active()).isTrue();
    }

    @Test
    void updateValidatesAndDeactivateIsSoft() {
        Plan plan = Fixtures.plan(UUID.randomUUID());
        plan.update("Pro Plus", "More", 9990, PlanInterval.YEARLY);

        assertThat(plan.price().amountCents()).isEqualTo(9990);
        assertThat(plan.interval()).isEqualTo(PlanInterval.YEARLY);
        assertThatThrownBy(() -> plan.update("", null, 9990, PlanInterval.YEARLY))
                .isInstanceOf(DomainValidationException.class);

        plan.deactivate();
        assertThat(plan.active()).isFalse();
        assertThatThrownBy(plan::ensureSubscribable).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void moneyIsBrlCentsWithDecimalView() {
        assertThat(Money.brl(4990).toDecimal()).isEqualByComparingTo(new BigDecimal("49.90"));
        assertThatThrownBy(() -> new Money(100, "USD")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> Money.brl(-1)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void customerDocumentMustBeCpfOrCnpjDigits() {
        UUID tenant = UUID.randomUUID();
        assertThatThrownBy(() -> Customer.create(UUID.randomUUID(), tenant, "A", "a@b.test", "123", "cus", NOW))
                .isInstanceOf(DomainValidationException.class);
        Customer company = Customer.create(UUID.randomUUID(), tenant, "Corp", "Billing@Corp.TEST",
                "11222333000181", "cus", NOW);
        assertThat(company.email()).isEqualTo("billing@corp.test");
    }
}
