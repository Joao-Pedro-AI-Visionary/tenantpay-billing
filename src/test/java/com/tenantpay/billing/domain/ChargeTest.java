package com.tenantpay.billing.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.tenantpay.billing.domain.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargeTest {
    private final Subscription subscription = Fixtures.subscription(UUID.randomUUID());

    private Charge newCharge() {
        return Fixtures.charge(subscription, "pay_1");
    }

    @Test
    void copiesTenantCustomerAndAmountFromSubscription() {
        Charge c = newCharge();

        assertThat(c.status()).isEqualTo(ChargeStatus.PENDING);
        assertThat(c.tenantId()).isEqualTo(subscription.tenantId());
        assertThat(c.customerId()).isEqualTo(subscription.customerId());
        assertThat(c.amountCents()).isEqualTo(subscription.amountCents());
        assertThat(c.isOpen()).isTrue();
    }

    @Test
    void confirmThenReceive() {
        Charge c = newCharge();
        c.confirm(NOW);
        c.confirm(NOW.plusSeconds(5));

        assertThat(c.status()).isEqualTo(ChargeStatus.CONFIRMED);
        assertThat(c.paidAt()).isEqualTo(NOW);

        c.receive(NOW.plusSeconds(60));
        assertThat(c.status()).isEqualTo(ChargeStatus.RECEIVED);
        assertThat(c.paidAt()).isEqualTo(NOW);
        assertThat(c.status().isPaid()).isTrue();
    }

    @Test
    void confirmAfterReceiveDoesNotDowngrade() {
        Charge c = newCharge();
        c.receive(NOW);
        c.confirm(NOW.plusSeconds(1));

        assertThat(c.status()).isEqualTo(ChargeStatus.RECEIVED);
    }

    @Test
    void overdueOnlyFromPending() {
        Charge c = newCharge();
        c.markOverdue();
        c.markOverdue();
        assertThat(c.status()).isEqualTo(ChargeStatus.OVERDUE);

        Charge paid = newCharge();
        paid.confirm(NOW);
        assertThatThrownBy(paid::markOverdue).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void overdueChargeCanStillBePaid() {
        Charge c = newCharge();
        c.markOverdue();
        c.confirm(NOW);

        assertThat(c.status()).isEqualTo(ChargeStatus.CONFIRMED);
    }

    @Test
    void canceledChargeCannotBePaidAndPaidChargeCannotBeCanceled() {
        Charge c = newCharge();
        c.cancel();
        c.cancel();
        assertThat(c.status()).isEqualTo(ChargeStatus.CANCELED);
        assertThatThrownBy(() -> c.confirm(NOW)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> c.receive(NOW)).isInstanceOf(InvalidStateTransitionException.class);

        Charge paid = newCharge();
        paid.receive(NOW);
        assertThatThrownBy(paid::cancel).isInstanceOf(InvalidStateTransitionException.class);
    }
}
