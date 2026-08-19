package io.tokenpilot.budget;

import io.tokenpilot.core.domain.Cost;
import io.tokenpilot.core.domain.PricingSnapshot;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 예산 bucket에 생성된 immutable 예약 snapshot입니다.
 */
public record BudgetReservation(
        ReservationId id,
        BudgetKey key,
        Cost limit,
        Cost amount,
        String requestId,
        IdempotencyKey idempotencyKey,
        String modelId,
        String pricingPolicyId,
        String catalogVersion,
        Optional<PricingSnapshot> pricingSnapshot,
        ReservationState state,
        Instant createdAt
) {

    public BudgetReservation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(limit, "limit must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        requestId = requireText(requestId, "requestId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (limit.value().signum() <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        if (!limit.currency().equals(amount.currency())) {
            throw new IllegalArgumentException("reservation costs must use the budget currency");
        }
        modelId = optionalText(modelId, "modelId");
        pricingPolicyId = optionalText(pricingPolicyId, "pricingPolicyId");
        catalogVersion = optionalText(catalogVersion, "catalogVersion");
        pricingSnapshot = Objects.requireNonNull(
                pricingSnapshot,
                "pricingSnapshot must not be null"
        );
        if (pricingSnapshot.isPresent()) {
            PricingSnapshot snapshot = pricingSnapshot.orElseThrow();
            requireSnapshotText(modelId, snapshot.modelId(), "modelId");
            requireSnapshotText(
                    pricingPolicyId,
                    snapshot.pricingPolicyId(),
                    "pricingPolicyId"
            );
            requireSnapshotText(
                    catalogVersion,
                    snapshot.catalogVersion(),
                    "catalogVersion"
            );
            if (!limit.currency().equals(snapshot.currency())) {
                throw new IllegalArgumentException(
                        "pricing snapshot must use the budget currency"
                );
            }
        }
    }

    /**
     * @deprecated request ID와 idempotency key를 같은 값으로 사용하는 호환 생성자입니다.
     *             신규 예약은 {@link #reserved(ReservationId, BudgetReservationRequest, Instant)}로 생성하세요.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public BudgetReservation(
            ReservationId id,
            BudgetKey key,
            Cost limit,
            Cost amount,
            IdempotencyKey idempotencyKey,
            String modelId,
            String pricingPolicyId,
            String catalogVersion,
            ReservationState state,
            Instant createdAt
    ) {
        this(
                id,
                key,
                limit,
                amount,
                idempotencyKey.value(),
                idempotencyKey,
                modelId,
                pricingPolicyId,
                catalogVersion,
                Optional.empty(),
                state,
                createdAt
        );
    }

    public static BudgetReservation reserved(
            ReservationId id,
            BudgetReservationRequest request,
            Instant createdAt
    ) {
        Objects.requireNonNull(request, "request must not be null");
        return new BudgetReservation(
                id,
                request.key(),
                request.limit(),
                request.safeUpperBoundCost(),
                request.requestId(),
                request.idempotencyKey(),
                request.modelId(),
                request.pricingPolicyId(),
                request.catalogVersion(),
                request.pricingSnapshot(),
                ReservationState.RESERVED,
                createdAt
        );
    }

    public boolean matches(BudgetReservationRequest request) {
        return key.equals(request.key())
                && limit.equals(request.limit())
                && amount.equals(request.safeUpperBoundCost())
                && requestId.equals(request.requestId())
                && idempotencyKey.equals(request.idempotencyKey())
                && Objects.equals(modelId, request.modelId())
                && Objects.equals(pricingPolicyId, request.pricingPolicyId())
                && Objects.equals(catalogVersion, request.catalogVersion())
                && pricingSnapshot.equals(request.pricingSnapshot());
    }

    /** 이 예약이 지정한 provider 요청에 속하는지 확인합니다. */
    public boolean belongsTo(String candidateRequestId) {
        return requestId.equals(candidateRequestId);
    }

    private static String optionalText(String value, String name) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static void requireSnapshotText(
            String value,
            String snapshotValue,
            String name
    ) {
        if (!Objects.equals(value, snapshotValue)) {
            throw new IllegalArgumentException(
                    name + " must match the pricing snapshot"
            );
        }
    }
}
