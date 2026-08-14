package io.tokenpilot.core.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * context admission 결과와 판정에 사용한 immutable metadata입니다.
 *
 * <p>{@code remainingTokens}는 FITS 결과에서만 제공됩니다. INDETERMINATE와
 * EXCEEDS에서 이를 빈 값으로 유지해 가짜 여유량을 provider 경계에 전달하지 않습니다.</p>
 */
public record BudgetResult(
        AdmissionStatus status,
        AdmissionReason reason,
        Optional<String> canonicalModelId,
        OptionalLong inputEstimatedTokens,
        OptionalLong inputSafeUpperBoundTokens,
        long reservedOutputTokens,
        OptionalLong maxContextTokens,
        OptionalLong remainingTokens,
        TokenEstimatorDescriptor estimatorDescriptor,
        TokenizationBasis tokenizationBasis
) {

    public BudgetResult {
        status = Objects.requireNonNull(status, "status must not be null");
        reason = Objects.requireNonNull(reason, "reason must not be null");
        canonicalModelId = Objects.requireNonNull(
                canonicalModelId,
                "canonicalModelId must not be null"
        );
        inputEstimatedTokens = Objects.requireNonNull(
                inputEstimatedTokens,
                "inputEstimatedTokens must not be null"
        );
        inputSafeUpperBoundTokens = Objects.requireNonNull(
                inputSafeUpperBoundTokens,
                "inputSafeUpperBoundTokens must not be null"
        );
        maxContextTokens = Objects.requireNonNull(maxContextTokens, "maxContextTokens must not be null");
        remainingTokens = Objects.requireNonNull(remainingTokens, "remainingTokens must not be null");
        estimatorDescriptor = Objects.requireNonNull(
                estimatorDescriptor,
                "estimatorDescriptor must not be null"
        );
        tokenizationBasis = Objects.requireNonNull(
                tokenizationBasis,
                "tokenizationBasis must not be null"
        );

        if (reservedOutputTokens < 0) {
            throw new IllegalArgumentException("reservedOutputTokens must be non-negative");
        }
        if (canonicalModelId.isPresent() && canonicalModelId.orElseThrow().isBlank()) {
            throw new IllegalArgumentException("canonicalModelId must not be blank");
        }
        if (inputEstimatedTokens.isPresent() != inputSafeUpperBoundTokens.isPresent()) {
            throw new IllegalArgumentException("input token values must be present together");
        }
        if (inputEstimatedTokens.isPresent()
                && inputSafeUpperBoundTokens.getAsLong() < inputEstimatedTokens.getAsLong()) {
            throw new IllegalArgumentException(
                    "inputSafeUpperBoundTokens must be greater than or equal to inputEstimatedTokens"
            );
        }
        if (maxContextTokens.isPresent() && maxContextTokens.getAsLong() <= 0) {
            throw new IllegalArgumentException("maxContextTokens must be positive");
        }
        if (status == AdmissionStatus.FITS) {
            if (reason != AdmissionReason.WITHIN_CONTEXT
                    || canonicalModelId.isEmpty()
                    || inputSafeUpperBoundTokens.isEmpty()
                    || maxContextTokens.isEmpty()
                    || remainingTokens.isEmpty()) {
                throw new IllegalArgumentException("FITS result is incomplete");
            }
            long expectedRemaining = maxContextTokens.getAsLong()
                    - inputSafeUpperBoundTokens.getAsLong()
                    - reservedOutputTokens;
            if (expectedRemaining != remainingTokens.getAsLong()) {
                throw new IllegalArgumentException("remainingTokens does not match FITS calculation");
            }
        } else if (remainingTokens.isPresent()) {
            throw new IllegalArgumentException("remainingTokens is only available for FITS");
        }
    }

    /**
     * FITS 상태인지 반환합니다.
     *
     * @return status가 FITS이면 true
     */
    public boolean fits() {
        return status == AdmissionStatus.FITS;
    }
}
