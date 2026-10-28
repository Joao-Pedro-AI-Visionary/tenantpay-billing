package com.tenantpay.billing.infrastructure.gateway;

import com.tenantpay.billing.application.port.GatewayException;
import com.tenantpay.billing.application.port.PaymentGateway;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Asaas v3 REST adapter (sandbox by default). Authenticates with the {@code access_token} header. Any non 2xx answer
 * or I/O problem becomes a {@link GatewayException}; the API key never appears in messages.
 */
public class AsaasGateway implements PaymentGateway {
    private final RestClient client;

    public AsaasGateway(RestClient.Builder builder, String baseUrl, String apiKey) {
        this.client = builder
                .baseUrl(baseUrl)
                .defaultHeader("access_token", apiKey)
                .defaultHeader("User-Agent", "tenantpay-billing")
                .build();
    }

    @Override
    public String name() {
        return "asaas";
    }

    @Override
    public String createCustomer(NewCustomer customer) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", customer.name());
        body.put("email", customer.email());
        body.put("cpfCnpj", customer.document());
        body.put("externalReference", customer.externalReference());
        return post("/customers", body, "create customer");
    }

    @Override
    public String createCharge(NewCharge charge) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customer", charge.gatewayCustomerId());
        body.put("billingType", "UNDEFINED");
        body.put("value", charge.amount().toDecimal());
        body.put("dueDate", charge.dueDate().toString());
        body.put("description", charge.description());
        body.put("externalReference", charge.externalReference());
        return post("/payments", body, "create payment");
    }

    @Override
    public void cancelCharge(String gatewayPaymentId) {
        try {
            client.delete()
                    .uri("/payments/{id}", gatewayPaymentId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw failure("cancel payment", response.getStatusCode());
                    })
                    .toBodilessEntity();
        } catch (GatewayException e) {
            throw e;
        } catch (RestClientException e) {
            throw new GatewayException("Asaas cancel payment failed: " + e.getClass().getSimpleName(), e);
        }
    }

    private String post(String path, Map<String, Object> body, String operation) {
        IdResponse response;
        try {
            response = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        throw failure(operation, res.getStatusCode());
                    })
                    .body(IdResponse.class);
        } catch (GatewayException e) {
            throw e;
        } catch (RestClientException e) {
            throw new GatewayException("Asaas " + operation + " failed: " + e.getClass().getSimpleName(), e);
        }
        if (response == null || response.id() == null || response.id().isBlank()) {
            throw new GatewayException("Asaas " + operation + " returned no id");
        }
        return response.id();
    }

    private static GatewayException failure(String operation, HttpStatusCode status) {
        return new GatewayException("Asaas " + operation + " failed with HTTP " + status.value());
    }

    record IdResponse(String id) {
    }
}
