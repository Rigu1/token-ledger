package io.tokenpilot.budget;

import io.tokenpilot.core.domain.Cost;
import io.tokenpilot.core.domain.PricingSnapshot;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * 한 예약의 estimate와 actual 비용을 연결한 회계 정산 결과입니다.
 */
public record ReservationReconciliation(
        String requestId,
        String attemptId,
        ReservationId reservationId,
        BudgetKey budgetKey,
        String responseModelId,
        PricingSnapshot pricingSnapshot,
        Cost estimate,
        Cost actual,
        ReservationTransition transition
) {

    public ReservationReconciliation {
        requestId = requireText(requestId, "requestId");
        attemptId = requireText(attemptId, "attemptId");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(budgetKey, "budgetKey must not be null");
        responseModelId = requireText(responseModelId, "responseModelId");
        Objects.requireNonNull(pricingSnapshot, "pricingSnapshot must not be null");
        Objects.requireNonNull(estimate, "estimate must not be null");
        Objects.requireNonNull(actual, "actual must not be null");
        Objects.requireNonNull(transition, "transition must not be null");
        if (!estimate.currency().equals(actual.currency())) {
            throw new IllegalArgumentException(
                    "estimate and actual must use the same currency"
            );
        }
        if (!estimate.currency().equals(pricingSnapshot.currency())) {
            throw new IllegalArgumentException(
                    "reconciliation costs must use the pricing snapshot currency"
            );
        }
    }

    /** 예약 시점 pricing snapshot의 request model입니다. */
    public String requestModelId() {
        return pricingSnapshot.modelId();
    }

    /** 예약 시점 pricing policy 식별자입니다. */
    public String pricingPolicyId() {
        return pricingSnapshot.pricingPolicyId();
    }

    /** 예약 시점 model catalog version입니다. */
    public String catalogVersion() {
        return pricingSnapshot.catalogVersion();
    }

    /** actual에서 estimate를 뺀 signed 비용 차이입니다. */
    public BigDecimal delta() {
        return actual.value().subtract(estimate.value());
    }

    /** estimate와 actual이 사용하는 통화입니다. */
    public Currency currency() {
        return actual.currency();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
