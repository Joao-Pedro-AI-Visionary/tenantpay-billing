package com.tenantpay.billing.domain;

public enum ChargeStatus {
    PENDING,
    CONFIRMED,
    RECEIVED,
    OVERDUE,
    CANCELED;

    public boolean isPaid() {
        return this == CONFIRMED || this == RECEIVED;
    }
}
