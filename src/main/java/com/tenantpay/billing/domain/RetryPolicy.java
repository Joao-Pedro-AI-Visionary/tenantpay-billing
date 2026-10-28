package com.tenantpay.billing.domain;

import java.time.Duration;

/**
 * Exponential backoff: the delay after the n-th failed attempt is {@code base * 2^(n-1)}, capped at {@code maxBackoff}.
 * After {@code maxAttempts} failed attempts the work is abandoned.
 */
public record RetryPolicy(int maxAttempts, Duration baseBackoff, Duration maxBackoff) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new DomainValidationException("maxAttempts must be at least 1");
        }
        if (baseBackoff == null || baseBackoff.isNegative() || baseBackoff.isZero()) {
            throw new DomainValidationException("baseBackoff must be positive");
        }
        if (maxBackoff == null || maxBackoff.compareTo(baseBackoff) < 0) {
            throw new DomainValidationException("maxBackoff must be greater than or equal to baseBackoff");
        }
    }

    public Duration backoffFor(int attempts) {
        if (attempts < 1) {
            throw new DomainValidationException("attempts must be at least 1");
        }
        int exponent = attempts - 1;
        if (exponent >= 62) {
            return maxBackoff;
        }
        long factor = 1L << exponent;
        long baseMillis = baseBackoff.toMillis();
        if (baseMillis > maxBackoff.toMillis() / factor) {
            return maxBackoff;
        }
        Duration delay = baseBackoff.multipliedBy(factor);
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }

    public boolean isExhausted(int attempts) {
        return attempts >= maxAttempts;
    }
}
