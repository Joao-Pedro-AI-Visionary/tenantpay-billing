package com.tenantpay.billing.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tenantpay.billing.application.IngestWebhook;
import com.tenantpay.billing.domain.WebhookEvent;
import com.tenantpay.billing.domain.WebhookEventStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

public final class WebhookDtos {
    private WebhookDtos() {
    }

    /** Subset of the Asaas v3 webhook body. Unknown fields are ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AsaasWebhookPayload(String id, String event, String dateCreated, AsaasPayment payment) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AsaasPayment(String id, String customer, String status, String externalReference) {
    }

    public record WebhookAckResponse(boolean received, boolean duplicate, String eventId) {
        public static WebhookAckResponse of(IngestWebhook.Result result) {
            return new WebhookAckResponse(true, result.duplicate(), result.eventId());
        }
    }

    public record WebhookEventResponse(UUID id, String eventId, String eventType, WebhookEventStatus status,
                                       int attempts, String lastError, Instant receivedAt, Instant processedAt,
                                       Instant nextAttemptAt) {
        public static WebhookEventResponse of(WebhookEvent event) {
            return new WebhookEventResponse(event.id(), event.eventId(), event.eventType(), event.status(),
                    event.attempts(), event.lastError(), event.receivedAt(), event.processedAt(),
                    event.nextAttemptAt());
        }
    }

    public record SimulateWebhookRequest(
            @NotNull UUID subscriptionId,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z_]{2,63}", message = "must be an event name such as PAYMENT_CONFIRMED")
            String event) {
    }

    public record DevConfigResponse(boolean devTools, String gateway) {
    }
}
