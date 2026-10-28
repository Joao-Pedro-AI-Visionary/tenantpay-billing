package com.tenantpay.billing.web.dto;

import com.tenantpay.billing.domain.Customer;
import com.tenantpay.billing.domain.Plan;
import com.tenantpay.billing.domain.PlanInterval;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class CatalogDtos {
    private CatalogDtos() {
    }

    public record PlanRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull @Min(100) Long amountCents,
            @NotNull PlanInterval interval) {
    }

    public record PlanResponse(UUID id, String name, String description, long amountCents, String currency,
                               PlanInterval interval, boolean active, Instant createdAt) {
        public static PlanResponse of(Plan plan) {
            return new PlanResponse(plan.id(), plan.name(), plan.description(), plan.price().amountCents(),
                    plan.price().currency(), plan.interval(), plan.active(), plan.createdAt());
        }
    }

    public record CustomerRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "\\d{11}|\\d{14}", message = "must have 11 (CPF) or 14 (CNPJ) digits")
            String document) {
    }

    public record CustomerResponse(UUID id, String name, String email, String document, String gatewayCustomerId,
                                   Instant createdAt) {
        public static CustomerResponse of(Customer customer) {
            return new CustomerResponse(customer.id(), customer.name(), customer.email(), customer.document(),
                    customer.gatewayCustomerId(), customer.createdAt());
        }
    }
}
