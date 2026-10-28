package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.ConflictException;
import com.tenantpay.billing.application.port.PasswordHasher;
import com.tenantpay.billing.application.port.TenantRepository;
import com.tenantpay.billing.application.port.TokenIssuer;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.application.port.UserRepository;
import com.tenantpay.billing.domain.DomainValidationException;
import com.tenantpay.billing.domain.Tenant;
import com.tenantpay.billing.domain.User;

import java.time.Clock;
import java.time.Instant;

/** Creates a tenant and its first ADMIN user atomically, then issues a token for that user. */
public class SignUpTenant {
    public static final int MIN_PASSWORD = 8;
    public static final int MAX_PASSWORD = 72;

    private final TenantRepository tenants;
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenIssuer tokens;
    private final UnitOfWork uow;
    private final Clock clock;

    public SignUpTenant(TenantRepository tenants, UserRepository users, PasswordHasher hasher, TokenIssuer tokens,
                        UnitOfWork uow, Clock clock) {
        this.tenants = tenants;
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
        this.uow = uow;
        this.clock = clock;
    }

    public AuthResult execute(Command command) {
        String email = User.normalizeEmail(command.email());
        String password = command.password();
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw new DomainValidationException("password must have between 8 and 72 characters");
        }
        if (users.existsByEmail(email)) {
            throw duplicate();
        }
        String hash = hasher.hash(password);
        Instant now = clock.instant();
        return uow.inTransaction(() -> {
            if (users.existsByEmail(email)) {
                throw duplicate();
            }
            Tenant tenant = Tenant.create(command.tenantName(), now);
            User admin = User.createAdmin(tenant.id(), email, hash, now);
            tenants.save(tenant);
            users.save(admin);
            TokenIssuer.IssuedToken token = tokens.issue(admin);
            return new AuthResult(tenant.id(), admin.id(), token.accessToken(), token.expiresInSeconds());
        });
    }

    private static ConflictException duplicate() {
        return new ConflictException("Email is already registered");
    }

    public record Command(String tenantName, String email, String password) {
    }
}
