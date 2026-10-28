package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.domain.Role;
import com.tenantpay.billing.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserEntity() {
    }

    static UserEntity from(User user) {
        UserEntity entity = new UserEntity();
        entity.id = user.id();
        entity.tenantId = user.tenantId();
        entity.email = user.email();
        entity.passwordHash = user.passwordHash();
        entity.role = user.role();
        entity.createdAt = user.createdAt();
        return entity;
    }

    User toDomain() {
        return User.restore(id, tenantId, email, passwordHash, role, createdAt);
    }
}
