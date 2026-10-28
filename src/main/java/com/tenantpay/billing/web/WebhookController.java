package com.tenantpay.billing.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenantpay.billing.application.IngestWebhook;
import com.tenantpay.billing.application.exception.UnauthorizedException;
import com.tenantpay.billing.web.dto.WebhookDtos.AsaasWebhookPayload;
import com.tenantpay.billing.web.dto.WebhookDtos.WebhookAckResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Receives Asaas notifications. Authenticated by the shared {@code asaas-access-token} header (constant time
 * comparison). The body is stored verbatim in the inbox and processed asynchronously.
 */
@RestController
@Tag(name = "Webhooks")
@SecurityRequirements
public class WebhookController {
    static final String TOKEN_HEADER = "asaas-access-token";

    private final IngestWebhook ingest;
    private final ObjectMapper objectMapper;
    private final byte[] expectedToken;

    public WebhookController(IngestWebhook ingest, ObjectMapper objectMapper,
                             @Value("${tenantpay.asaas.webhook-token:}") String webhookToken) {
        this.ingest = ingest;
        this.objectMapper = objectMapper;
        this.expectedToken = webhookToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/webhooks/asaas")
    public WebhookAckResponse receive(@RequestHeader(name = TOKEN_HEADER, required = false) String token,
                                      @RequestBody(required = false) String body) {
        verifyToken(token);
        if (body == null || body.isBlank()) {
            throw new InvalidRequestException("Webhook body is required");
        }
        AsaasWebhookPayload payload;
        try {
            payload = objectMapper.readValue(body, AsaasWebhookPayload.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Webhook body is not valid JSON");
        }
        if (payload == null || isBlank(payload.id()) || isBlank(payload.event())) {
            throw new InvalidRequestException("Webhook body must contain id and event");
        }
        String paymentId = payload.payment() == null ? null : payload.payment().id();
        return WebhookAckResponse.of(ingest.execute(
                new IngestWebhook.Command(payload.id(), payload.event(), paymentId, body)));
    }

    private void verifyToken(String token) {
        byte[] provided = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (expectedToken.length == 0 || !MessageDigest.isEqual(expectedToken, provided)) {
            throw new UnauthorizedException("Invalid webhook token");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
