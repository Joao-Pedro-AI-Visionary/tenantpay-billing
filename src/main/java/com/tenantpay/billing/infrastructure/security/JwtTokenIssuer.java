package com.tenantpay.billing.infrastructure.security;

import com.tenantpay.billing.application.port.TokenIssuer;
import com.tenantpay.billing.domain.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Claims: sub (user id), tenant_id, email, role. */
public class JwtTokenIssuer implements TokenIssuer {
    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final Clock clock;

    public JwtTokenIssuer(JwtEncoder encoder, Duration ttl, Clock clock) {
        this.encoder = encoder;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(CLAIM_TENANT_ID, user.tenantId().toString())
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_ROLE, user.role().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, ttl.toSeconds());
    }
}
