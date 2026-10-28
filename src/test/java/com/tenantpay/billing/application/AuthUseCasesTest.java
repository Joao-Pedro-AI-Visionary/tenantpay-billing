package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.ConflictException;
import com.tenantpay.billing.application.exception.UnauthorizedException;
import com.tenantpay.billing.domain.DomainValidationException;
import com.tenantpay.billing.domain.Fixtures;
import com.tenantpay.billing.domain.Role;
import com.tenantpay.billing.domain.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthUseCasesTest {
    /** Generated per run: tests never carry a literal password. */
    private static final String PASSWORD = UUID.randomUUID().toString();

    private final InMemoryPorts.Tenants tenants = new InMemoryPorts.Tenants();
    private final InMemoryPorts.Users users = new InMemoryPorts.Users();
    private final InMemoryPorts.CountingUnitOfWork uow = new InMemoryPorts.CountingUnitOfWork();
    private final MutableClock clock = new MutableClock(Fixtures.NOW);
    private final SignUpTenant signUp = new SignUpTenant(tenants, users, new InMemoryPorts.PlainHasher(),
            new InMemoryPorts.StubTokens(), uow, clock);
    private final Login login = new Login(users, new InMemoryPorts.PlainHasher(), new InMemoryPorts.StubTokens());
    private final GetMe getMe = new GetMe(users, tenants, uow);

    @Test
    void signUpCreatesTenantAndAdminInOneTransaction() {
        AuthResult result = signUp.execute(new SignUpTenant.Command("Acme Gym", "Owner@Acme.test", PASSWORD));

        assertThat(uow.transactions).isEqualTo(1);
        assertThat(tenants.rows).containsKey(result.tenantId());
        User admin = users.rows.get(result.userId());
        assertThat(admin.email()).isEqualTo("owner@acme.test");
        assertThat(admin.role()).isEqualTo(Role.ADMIN);
        assertThat(admin.passwordHash()).isNotEqualTo(PASSWORD);
        assertThat(result.accessToken()).isEqualTo("token-for-" + admin.id());
        assertThat(result.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void duplicateEmailIsAConflictRegardlessOfCase() {
        signUp.execute(new SignUpTenant.Command("Acme Gym", "owner@acme.test", PASSWORD));

        assertThatThrownBy(() -> signUp.execute(
                new SignUpTenant.Command("Other", "OWNER@acme.test", "another-pass")))
                .isInstanceOf(ConflictException.class);
        assertThat(tenants.rows).hasSize(1);
    }

    @Test
    void shortPasswordIsRejected() {
        assertThatThrownBy(() -> signUp.execute(new SignUpTenant.Command("Acme", "a@acme.test", "short")))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void loginChecksPasswordAndHidesWhichPartWasWrong() {
        AuthResult created = signUp.execute(new SignUpTenant.Command("Acme", "owner@acme.test", PASSWORD));

        AuthResult logged = login.execute(new Login.Command("owner@acme.test", PASSWORD));
        assertThat(logged.userId()).isEqualTo(created.userId());
        assertThat(logged.tenantId()).isEqualTo(created.tenantId());

        assertThatThrownBy(() -> login.execute(new Login.Command("owner@acme.test", PASSWORD + "-wrong")))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid email or password");
        assertThatThrownBy(() -> login.execute(new Login.Command("nobody@acme.test", PASSWORD)))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid email or password");
    }

    @Test
    void meReturnsTenantName() {
        AuthResult created = signUp.execute(new SignUpTenant.Command("Acme Gym", "owner@acme.test", PASSWORD));

        GetMe.View me = getMe.execute(created.tenantId(), created.userId());

        assertThat(me.tenantName()).isEqualTo("Acme Gym");
        assertThat(me.email()).isEqualTo("owner@acme.test");
    }
}
