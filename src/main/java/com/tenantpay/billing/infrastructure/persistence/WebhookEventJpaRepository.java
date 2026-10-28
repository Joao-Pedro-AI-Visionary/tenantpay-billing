package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.domain.WebhookEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEventJpaRepository extends JpaRepository<WebhookEventEntity, UUID> {
    boolean existsByEventId(String eventId);

    Optional<WebhookEventEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<WebhookEventEntity> findByTenantIdOrderByReceivedAtDescIdDesc(UUID tenantId);

    List<WebhookEventEntity> findByTenantIdAndStatusOrderByReceivedAtDescIdDesc(UUID tenantId,
                                                                                WebhookEventStatus status);

    /** Row level claim: concurrent pollers skip rows another transaction already locked. */
    @Query(value = """
            SELECT id, tenant_id, event_id, event_type, payment_id, status, attempts, next_attempt_at, last_error,
                   received_at, processed_at
              FROM webhook_events
             WHERE status = 'RECEIVED'
                OR (status IN ('FAILED', 'PROCESSING') AND next_attempt_at <= :now)
             ORDER BY received_at
             LIMIT :limit
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookEventEntity> lockDue(@Param("now") Instant now, @Param("limit") int limit);
}
