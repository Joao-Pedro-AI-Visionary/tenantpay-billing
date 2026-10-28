package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.UnauthorizedException;
import com.tenantpay.billing.application.port.PasswordHasher;
import com.tenantpay.billing.application.port.TokenIssuer;
import com.tenantpay.billing.application.port.UserRepository;
import com.tenantpay.billing.domain.User;

import java.util.Optional;

/** Exchanges email and password for an access token. */
public class Login {
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenIssuer tokens;
    private volatile String dummyHash;

    public Login(UserRepository users, PasswordHasher hasher, TokenIssuer tokens) {
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    public AuthResult execute(Command command) {
        String email = command.email() == null ? "" : command.email().trim();
        String password = command.password() == null ? "" : command.password();
        Optional<User> user = email.isEmpty() ? Optional.empty() : users.findByEmail(User.normalizeEmail(email));
        if (user.isEmpty()) {
            // Spend the same hashing time as a real check so that response time does not reveal registered emails.
            hasher.matches(password, dummyHash());
            throw invalid();
        }
        if (!hasher.matches(password, user.get().passwordHash())) {
            throw invalid();
        }
        TokenIssuer.IssuedToken token = tokens.issue(user.get());
        return new AuthResult(user.get().tenantId(), user.get().id(), token.accessToken(), token.expiresInSeconds());
    }

    private String dummyHash() {
        String value = dummyHash;
        if (value == null) {
            value = hasher.hash("not-a-real-password");
            dummyHash = value;
        }
        return value;
    }

    private static UnauthorizedException invalid() {
        return new UnauthorizedException("Invalid email or password");
    }

    public record Command(String email, String password) {
    }
}
