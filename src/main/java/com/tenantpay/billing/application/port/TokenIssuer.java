package com.tenantpay.billing.application.port;

import com.tenantpay.billing.domain.User;

/** Issues access tokens carrying the user id, tenant id, email and role. */
public interface TokenIssuer {
    IssuedToken issue(User user);

    record IssuedToken(String accessToken, long expiresInSeconds) {
    }
}
