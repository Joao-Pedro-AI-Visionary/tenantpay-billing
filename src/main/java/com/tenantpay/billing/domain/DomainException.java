package com.tenantpay.billing.domain;

/** Base type for every rule violation raised by the domain model. */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
