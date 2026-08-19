package io.tokenpilot.budget;

import io.tokenpilot.core.domain.Cost;
import io.tokenpilot.core.domain.PricingSnapshot;

import java.util.Objects;
import java.util.Optional;

/**
 * 호출 전 안전 상한 비용을 예산 bucket에 예약하기 위한 immutable 요청입니다.
 *
 * <p>{@code limit}은 bucket 생성 시 고정되는 정책 snapshot이고,
 * {@code safeUpperBoundCost}는 예약할 실제 금액입니다. 모델과 가격 식별자는
 * 같은 idempotency key의 요청 payload가 바뀌었는지 검증하는 fingerprint로 사용됩니다.</p>
 */
public record BudgetReservationRequest(
        BudgetKey key,
        Cost limit,
        Cost safeUpperBoundCost,
        String requestId,
        IdempotencyKey idempotencyKey,
        String modelId,
        String pricingPolicyId,
        String catalogVersion,
        Optional<PricingSnapshot> pricingSnapshot
) {

    public BudgetReservationRequest {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(limit, "limit must not be null");
        Objects.requireNonNull(safeUpperBoundCost, "safeUpperBoundCost must not be null");
        requestId = requireText(requestId, "requestId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        if (limit.value().signum() <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
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
            modelId = snapshotText(modelId, snapshot.modelId(), "modelId");
            pricingPolicyId = snapshotText(
                    pricingPolicyId,
                    snapshot.pricingPolicyId(),
                    "pricingPolicyId"
            );
            catalogVersion = snapshotText(
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
     *             신규 호출은 두 값을 명시하는 canonical 생성자를 사용하세요.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public BudgetReservationRequest(
            BudgetKey key,
            Cost limit,
            Cost safeUpperBoundCost,
            IdempotencyKey idempotencyKey,
            String modelId,
            String pricingPolicyId,
            String catalogVersion
    ) {
        this(
                key,
                limit,
                safeUpperBoundCost,
                idempotencyKey.value(),
                idempotencyKey,
                modelId,
                pricingPolicyId,
                catalogVersion,
                Optional.empty()
        );
    }

    /**
     * @deprecated request ID와 idempotency key를 같은 값으로 사용하는 호환 생성자입니다.
     *             신규 호출은 두 값을 명시하는 canonical 생성자를 사용하세요.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public BudgetReservationRequest(
            BudgetKey key,
            Cost limit,
            Cost safeUpperBoundCost,
            IdempotencyKey idempotencyKey,
            String modelId,
            String pricingPolicyId,
            String catalogVersion,
            Optional<PricingSnapshot> pricingSnapshot
    ) {
        this(
                key,
                limit,
                safeUpperBoundCost,
                idempotencyKey.value(),
                idempotencyKey,
                modelId,
                pricingPolicyId,
                catalogVersion,
                pricingSnapshot
        );
    }

    /**
     * @deprecated request ID와 idempotency key를 같은 값으로 사용하는 호환 생성자입니다.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public BudgetReservationRequest(
            BudgetKey key,
            Cost limit,
            Cost safeUpperBoundCost,
            String idempotencyKey
    ) {
        this(
                key,
                limit,
                safeUpperBoundCost,
                idempotencyKey,
                new IdempotencyKey(idempotencyKey),
                null,
                null,
                null,
                Optional.empty()
        );
    }

    /**
     * @deprecated request ID와 idempotency key를 같은 값으로 사용하는 호환 생성자입니다.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public BudgetReservationRequest(
            BudgetKey key,
            Cost limit,
            Cost safeUpperBoundCost,
            String idempotencyKey,
            String modelId,
            String pricingPolicyId,
            String catalogVersion
    ) {
        this(
                key,
                limit,
                safeUpperBoundCost,
                idempotencyKey,
                new IdempotencyKey(idempotencyKey),
                modelId,
                pricingPolicyId,
                catalogVersion,
                Optional.empty()
        );
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

    private static String snapshotText(
            String value,
            String snapshotValue,
            String name
    ) {
        if (value != null && !value.equals(snapshotValue)) {
            throw new IllegalArgumentException(
                    name + " must match the pricing snapshot"
            );
        }
        return snapshotValue;
    }
}
