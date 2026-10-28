package com.tenantpay.billing.infrastructure.gateway;

import com.tenantpay.billing.application.port.GatewayException;
import com.tenantpay.billing.application.port.PaymentGateway;
import com.tenantpay.billing.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AsaasGatewayTest {
    private static final String BASE = "https://asaas.example.test/v3";

    private MockRestServiceServer server;
    private AsaasGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new AsaasGateway(builder, BASE, "test-api-key");
    }

    @Test
    void createsCustomerWithAccessTokenHeader() {
        server.expect(requestTo(BASE + "/customers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", "test-api-key"))
                .andExpect(content().json("""
                        {"name":"Jane Doe","email":"jane@example.test","cpfCnpj":"24971563792",
                         "externalReference":"c-1"}
                        """, true))
                .andRespond(withSuccess("{\"id\":\"cus_000005219613\",\"object\":\"customer\"}",
                        MediaType.APPLICATION_JSON));

        String id = gateway.createCustomer(new PaymentGateway.NewCustomer("Jane Doe", "jane@example.test",
                "24971563792", "c-1"));

        assertThat(id).isEqualTo("cus_000005219613");
        server.verify();
    }

    @Test
    void createsPaymentWithDecimalValueAndUndefinedBillingType() {
        server.expect(requestTo(BASE + "/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", "test-api-key"))
                .andExpect(content().json("""
                        {"customer":"cus_1","billingType":"UNDEFINED","value":49.90,"dueDate":"2026-10-31",
                         "description":"Subscription to plan Pro","externalReference":"charge-1"}
                        """, true))
                .andRespond(withSuccess("{\"id\":\"pay_080225913252\",\"status\":\"PENDING\"}",
                        MediaType.APPLICATION_JSON));

        String id = gateway.createCharge(new PaymentGateway.NewCharge("cus_1", Money.brl(4990),
                LocalDate.parse("2026-10-31"), "Subscription to plan Pro", "charge-1"));

        assertThat(id).isEqualTo("pay_080225913252");
        server.verify();
    }

    @Test
    void cancelsPayment() {
        server.expect(requestTo(BASE + "/payments/pay_1"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("access_token", "test-api-key"))
                .andRespond(withSuccess("{\"deleted\":true,\"id\":\"pay_1\"}", MediaType.APPLICATION_JSON));

        gateway.cancelCharge("pay_1");

        server.verify();
    }

    @Test
    void errorStatusBecomesGatewayExceptionWithoutLeakingTheKey() {
        server.expect(requestTo(BASE + "/payments"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"invalid_value\"}]}"));

        assertThatThrownBy(() -> gateway.createCharge(new PaymentGateway.NewCharge("cus_1", Money.brl(4990),
                LocalDate.parse("2026-10-31"), "x", "charge-1")))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageNotContaining("test-api-key");
    }

    @Test
    void cancelErrorBecomesGatewayException() {
        server.expect(requestTo(BASE + "/payments/pay_1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> gateway.cancelCharge("pay_1"))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("HTTP 401");
    }

    @Test
    void missingIdIsAnError() {
        server.expect(requestTo(BASE + "/customers"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.createCustomer(new PaymentGateway.NewCustomer("a", "a@b.test",
                "24971563792", "r")))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("no id");
    }
}
