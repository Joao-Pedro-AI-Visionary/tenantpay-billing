package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.application.port.WebhookEventRepository;
import com.tenantpay.billing.domain.WebhookEvent;
import com.tenantpay.billing.domain.WebhookEventStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class WebhookEventRepositoryAdapter implements WebhookEventRepository {
    /**
     * ON CONFLICT DO NOTHING makes the insert race safe without raising a unique violation, which in PostgreSQL would
     * abort the surrounding transaction.
     */
    private static final String INSERT = """
            INSERT INTO webhook_events (id, tenant_id, event_id, event_type, payment_id, payload, status, attempts,
                                        next_attempt_at, last_error, received_at, processed_at)
            VALUES (:id, :tenantId, :eventId, :eventType, :paymentId, CAST(:payload AS jsonb), :status, :attempts,
                    NULL, NULL, :receivedAt, NULL)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private final WebhookEventJpaRepository events;
    private final NamedParameterJdbcTemplate jdbc;

    WebhookEventRepositoryAdapter(WebhookEventJpaRepository events, NamedParameterJdbcTemplate jdbc) {
        this.events = events;
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return events.existsByEventId(eventId);
    }

    @Override
    public boolean insertIfAbsent(WebhookEvent event) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", event.id(), Types.OTHER)
                .addValue("tenantId", event.tenantId(), Types.OTHER)
                .addValue("eventId", event.eventId())
                .addValue("eventType", event.eventType())
                .addValue("paymentId", event.paymentId(), Types.VARCHAR)
                .addValue("payload", event.payload())
                .addValue("status", event.status().name())
                .addValue("attempts", event.attempts())
                .addValue("receivedAt", toOffset(event.receivedAt()));
        return jdbc.update(INSERT, params) == 1;
    }

    @Override
    public void save(WebhookEvent event) {
        events.save(WebhookEventEntity.from(event));
    }

    @Override
    public Optional<WebhookEvent> findById(UUID id) {
        return events.findById(id).map(WebhookEventEntity::toDomain);
    }

    @Override
    public Optional<WebhookEvent> findById(UUID tenantId, UUID id) {
        return events.findByIdAndTenantId(id, tenantId).map(WebhookEventEntity::toDomain);
    }

    @Override
    public List<WebhookEvent> findAll(UUID tenantId, WebhookEventStatus status) {
        List<WebhookEventEntity> rows = status == null
                ? events.findByTenantIdOrderByReceivedAtDescIdDesc(tenantId)
                : events.findByTenantIdAndStatusOrderByReceivedAtDescIdDesc(tenantId, status);
        return rows.stream().map(WebhookEventEntity::toDomain).toList();
    }

    @Override
    public List<WebhookEvent> lockDue(Instant now, int limit) {
        return events.lockDue(now, limit).stream().map(WebhookEventEntity::toDomain).toList();
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
