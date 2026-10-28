package com.tenantpay.billing.domain;

/** Small argument checks shared by the aggregates. */
final class Guard {
    private Guard() {
    }

    static <T> T notNull(T value, String field) {
        if (value == null) {
            throw new DomainValidationException(field + " is required");
        }
        return value;
    }

    static String notBlank(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new DomainValidationException(field + " must have at most " + max + " characters");
        }
        return trimmed;
    }
}
