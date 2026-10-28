package com.tenantpay.billing.web.dto;

import com.tenantpay.billing.application.AuthResult;
import com.tenantpay.billing.application.GetMe;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record SignUpRequest(
            @NotBlank @Size(min = 2, max = 120) String tenantName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record AuthResponse(UUID tenantId, UUID userId, String accessToken, String tokenType, long expiresIn) {
        public static AuthResponse of(AuthResult result) {
            return new AuthResponse(result.tenantId(), result.userId(), result.accessToken(), "Bearer",
                    result.expiresInSeconds());
        }
    }

    public record MeResponse(UUID userId, UUID tenantId, String tenantName, String email, String role) {
        public static MeResponse of(GetMe.View view) {
            return new MeResponse(view.userId(), view.tenantId(), view.tenantName(), view.email(),
                    view.role().name());
        }
    }
}
