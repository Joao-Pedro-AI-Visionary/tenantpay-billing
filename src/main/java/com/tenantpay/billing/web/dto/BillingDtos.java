package com.tenantpay.billing.web.dto;

import com.tenantpay.billing.application.GetDashboard;
import com.tenantpay.billing.application.SubscriptionView;
import com.tenantpay.billing.domain.Charge;
import com.tenantpay.billing.domain.ChargeStatus;
import com.tenantpay.billing.domain.Subscription;
import com.tenantpay.billing.domain.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class BillingDtos {
    private BillingDtos() {
    }

    public record SubscribeRequest(@NotNull UUID customerId, @NotNull UUID planId) {
    }

    public record LatestChargeResponse(UUID id, String gatewayPaymentId, ChargeStatus status, long amountCents,
                                       LocalDate dueDate) {
        static LatestChargeResponse of(Charge charge) {
            return charge == null ? null : new LatestChargeResponse(charge.id(), charge.gatewayPaymentId(),
                    charge.status(), charge.amountCents(), charge.dueDate());
        }
    }

    public record SubscriptionResponse(UUID id, UUID customerId, String customerName, UUID planId, String planName,
                                       long amountCents, SubscriptionStatus status, Instant createdAt,
                                       Instant activatedAt, Instant canceledAt,
                                       LatestChargeResponse latestCharge) {
        public static SubscriptionResponse of(SubscriptionView view) {
            Subscription s = view.subscription();
            return new SubscriptionResponse(s.id(), s.customerId(), view.customerName(), s.planId(),
                    view.planName(), s.amountCents(), s.status(), s.createdAt(), s.activatedAt(), s.canceledAt(),
                    LatestChargeResponse.of(view.latestCharge()));
        }
    }

    public record ChargeResponse(UUID id, UUID subscriptionId, UUID customerId, String gatewayPaymentId,
                                 long amountCents, ChargeStatus status, LocalDate dueDate, Instant paidAt,
                                 Instant createdAt) {
        public static ChargeResponse of(Charge charge) {
            return new ChargeResponse(charge.id(), charge.subscriptionId(), charge.customerId(),
                    charge.gatewayPaymentId(), charge.amountCents(), charge.status(), charge.dueDate(),
                    charge.paidAt(), charge.createdAt());
        }
    }

    public record DashboardResponse(long plans, long customers, long subscriptions,
                                    Map<String, Long> subscriptionsByStatus, long receivedAmountCents) {
        public static DashboardResponse of(GetDashboard.View view) {
            Map<String, Long> byStatus = new LinkedHashMap<>();
            for (SubscriptionStatus status : SubscriptionStatus.values()) {
                byStatus.put(status.name(), view.subscriptionsByStatus().getOrDefault(status, 0L));
            }
            return new DashboardResponse(view.plans(), view.customers(), view.subscriptions(), byStatus,
                    view.receivedAmountCents());
        }
    }
}
