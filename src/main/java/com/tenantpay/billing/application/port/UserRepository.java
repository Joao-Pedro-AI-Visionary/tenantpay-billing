package com.tenantpay.billing.application.port;

import com.tenantpay.billing.domain.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    void save(User user);

    /** Emails are globally unique, so login is the one lookup that is not scoped by tenant. */
    boolean existsByEmail(String normalizedEmail);

    Optional<User> findByEmail(String normalizedEmail);

    Optional<User> findById(UUID tenantId, UUID userId);
}
