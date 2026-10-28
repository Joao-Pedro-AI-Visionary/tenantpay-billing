package com.tenantpay.billing.domain;

public enum WebhookEventStatus {
    RECEIVED,
    PROCESSING,
    PROCESSED,
    IGNORED,
    FAILED,
    DEAD
}
