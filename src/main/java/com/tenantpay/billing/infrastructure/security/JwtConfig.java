package com.tenantpay.billing.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.tenantpay.billing.application.port.PasswordHasher;
import com.tenantpay.billing.application.port.TokenIssuer;
import com.tenantpay.billing.infrastructure.config.TenantpayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/** HS256 tokens signed with {@code tenantpay.jwt.secret}, plus the BCrypt password hasher. */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {
    static final String ISSUER = "tenantpay-billing";
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecretKey jwtSigningKey(TenantpayProperties properties) {
        String secret = properties.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("tenantpay.jwt.secret must have at least " + MIN_SECRET_BYTES + " bytes");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    @Bean
    TokenIssuer tokenIssuer(JwtEncoder jwtEncoder, TenantpayProperties properties, Clock clock) {
        return new JwtTokenIssuer(jwtEncoder, properties.jwt().ttl(), clock);
    }

    @Bean
    PasswordHasher passwordHasher() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return new PasswordHasher() {
            @Override
            public String hash(String rawPassword) {
                return encoder.encode(rawPassword);
            }

            @Override
            public boolean matches(String rawPassword, String hash) {
                return encoder.matches(rawPassword, hash);
            }
        };
    }
}
