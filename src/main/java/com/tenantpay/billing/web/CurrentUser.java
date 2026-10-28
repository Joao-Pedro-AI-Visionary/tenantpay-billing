package com.tenantpay.billing.web;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** Identity taken from the validated access token. The tenant is never read from the request body or path. */
public record CurrentUser(UUID userId, UUID tenantId) {

    public static CurrentUser of(Jwt jwt) {
        return new CurrentUser(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("tenant_id")));
    }
}
