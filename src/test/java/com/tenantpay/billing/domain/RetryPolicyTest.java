package com.tenantpay.billing.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {
    private final RetryPolicy policy = new RetryPolicy(5, Duration.ofSeconds(2), Duration.ofMinutes(5));

    @Test
    void doublesTheDelayOnEveryAttempt() {
        assertThat(policy.backoffFor(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.backoffFor(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.backoffFor(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(policy.backoffFor(4)).isEqualTo(Duration.ofSeconds(16));
    }

    @Test
    void capsAtMaxBackoffWithoutOverflow() {
        assertThat(policy.backoffFor(8)).isEqualTo(Duration.ofSeconds(256));
        assertThat(policy.backoffFor(9)).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.backoffFor(40)).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.backoffFor(Integer.MAX_VALUE)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void exhaustedAtMaxAttempts() {
        assertThat(policy.isExhausted(4)).isFalse();
        assertThat(policy.isExhausted(5)).isTrue();
    }

    @Test
    void rejectsNonsenseConfiguration() {
        assertThatThrownBy(() -> new RetryPolicy(0, Duration.ofSeconds(1), Duration.ofSeconds(1)))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new RetryPolicy(3, Duration.ZERO, Duration.ofSeconds(1)))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new RetryPolicy(3, Duration.ofSeconds(2), Duration.ofSeconds(1)))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> policy.backoffFor(0)).isInstanceOf(DomainValidationException.class);
    }
}
