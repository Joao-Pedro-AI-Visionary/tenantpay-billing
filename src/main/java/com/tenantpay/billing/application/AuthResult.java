package com.tenantpay.billing.application;

import java.util.UUID;

public record AuthResult(UUID tenantId, UUID userId, String accessToken, long expiresInSeconds) {
}
