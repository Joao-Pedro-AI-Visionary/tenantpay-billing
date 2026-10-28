package com.tenantpay.billing.domain;

import java.math.BigDecimal;

/** An amount of money stored as integer cents. Only BRL is supported. */
public record Money(long amountCents, String currency) {
    public static final String BRL = "BRL";

    public Money {
        if (amountCents < 0) {
            throw new DomainValidationException("amountCents must not be negative");
        }
        if (!BRL.equals(currency)) {
            throw new DomainValidationException("only BRL is supported");
        }
    }

    public static Money brl(long amountCents) {
        return new Money(amountCents, BRL);
    }

    /** Decimal value in reais, as expected by payment gateways. */
    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(amountCents, 2);
    }
}
