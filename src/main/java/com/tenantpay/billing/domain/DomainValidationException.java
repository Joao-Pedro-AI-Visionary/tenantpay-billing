package com.tenantpay.billing.domain;

/** Raised when an aggregate would be created or changed with invalid data. */
public class DomainValidationException extends DomainException {
    public DomainValidationException(String message) {
        super(message);
    }
}
