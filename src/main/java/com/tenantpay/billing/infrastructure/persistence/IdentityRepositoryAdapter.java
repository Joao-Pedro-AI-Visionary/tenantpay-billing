package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.application.port.TenantRepository;
import com.tenantpay.billing.application.port.UserRepository;
import com.tenantpay.billing.domain.Tenant;
import com.tenantpay.billing.domain.User;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Tenants and users. */
@Component
class IdentityRepositoryAdapter implements TenantRepository, UserRepository {
    private final TenantJpaRepository tenants;
    private final UserJpaRepository users;

    IdentityRepositoryAdapter(TenantJpaRepository tenants, UserJpaRepository users) {
        this.tenants = tenants;
        this.users = users;
    }

    @Override
    public void save(Tenant tenant) {
        tenants.save(TenantEntity.from(tenant));
    }

    @Override
    public Optional<Tenant> findById(UUID tenantId) {
        return tenants.findById(tenantId).map(TenantEntity::toDomain);
    }

    @Override
    public void save(User user) {
        // flush so that a unique email violation surfaces inside the use case transaction
        users.saveAndFlush(UserEntity.from(user));
    }

    @Override
    public boolean existsByEmail(String normalizedEmail) {
        return users.existsByEmail(normalizedEmail);
    }

    @Override
    public Optional<User> findByEmail(String normalizedEmail) {
        return users.findByEmail(normalizedEmail).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findById(UUID tenantId, UUID userId) {
        return users.findByIdAndTenantId(userId, tenantId).map(UserEntity::toDomain);
    }
}
