package io.tokenpilot.core.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public final class TokenCountResult {
    private final OptionalLong tokens;
    private final OptionalLong safeUpperBoundTokens;
    private final Optional<TokenCountAccuracy> accuracy;
    private final Optional<TokenCountUnavailableReason> unavailableReason;
    private final TokenCountScope scope;
    private final TokenEstimatorDescriptor estimatorDescriptor;
    private final TokenizationBasis tokenizationBasis;

    private TokenCountResult(
            long tokens,
            long safeUpperBoundTokens,
            TokenCountAccuracy accuracy,
            TokenCountScope scope,
            TokenEstimatorDescriptor estimatorDescriptor,
            TokenizationBasis tokenizationBasis
    ) {
        this.tokens = OptionalLong.of(tokens);
        this.safeUpperBoundTokens = OptionalLong.of(safeUpperBoundTokens);
        this.accuracy = Optional.of(accuracy);
        this.unavailableReason = Optional.empty();
        this.scope = scope;
        this.estimatorDescriptor = estimatorDescriptor;
        this.tokenizationBasis = tokenizationBasis;
    }

    private TokenCountResult(
            TokenCountUnavailableReason unavailableReason,
            TokenCountScope scope,
            TokenEstimatorDescriptor estimatorDescriptor,
            TokenizationBasis tokenizationBasis
    ) {
        this.tokens = OptionalLong.empty();
        this.safeUpperBoundTokens = OptionalLong.empty();
        this.accuracy = Optional.empty();
        this.unavailableReason = Optional.of(unavailableReason);
        this.scope = scope;
        this.estimatorDescriptor = estimatorDescriptor;
        this.tokenizationBasis = tokenizationBasis;
    }

    public static TokenCountResult counted(
            long tokens,
            long safeUpperBoundTokens,
            TokenCountAccuracy accuracy,
            TokenCountScope scope,
            TokenEstimatorDescriptor estimatorDescriptor,
            TokenizationBasis tokenizationBasis
    ) {
        validateCounted(
                tokens,
                safeUpperBoundTokens,
                accuracy,
                scope,
                estimatorDescriptor,
                tokenizationBasis
        );

        return new TokenCountResult(
                tokens,
                safeUpperBoundTokens,
                accuracy,
                scope,
                estimatorDescriptor,
                tokenizationBasis
        );
    }

    private static void validateCounted(
            long tokens,
            long safeUpperBoundTokens,
            TokenCountAccuracy accuracy,
            TokenCountScope scope,
            TokenEstimatorDescriptor estimatorDescriptor,
            TokenizationBasis tokenizationBasis
    ) {
        Objects.requireNonNull(accuracy, "accuracy must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(estimatorDescriptor, "estimatorDescriptor must not be null");
        Objects.requireNonNull(tokenizationBasis, "tokenizationBasis must not be null");

        if (tokens < 0) {
            throw new IllegalArgumentException("tokens must be non-negative");
        }
        if (safeUpperBoundTokens < 0) {
            throw new IllegalArgumentException("safeUpperBoundTokens must be non-negative");
        }
        if (accuracy == TokenCountAccuracy.EXACT && tokens != safeUpperBoundTokens) {
            throw new IllegalArgumentException(
                    "EXACT requires tokens to equal safeUpperBoundTokens"
            );
        }
        if (accuracy == TokenCountAccuracy.HEURISTIC && safeUpperBoundTokens < tokens) {
            throw new IllegalArgumentException(
                    "HEURISTIC requires safeUpperBoundTokens to be greater than or equal to tokens"
            );
        }
    }

    public static TokenCountResult unavailable(
            TokenCountUnavailableReason reason,
            TokenCountScope scope,
            TokenEstimatorDescriptor estimatorDescriptor,
            TokenizationBasis tokenizationBasis
    ) {
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(estimatorDescriptor, "estimatorDescriptor must not be null");
        Objects.requireNonNull(tokenizationBasis, "tokenizationBasis must not be null");

        return new TokenCountResult(
                reason,
                scope,
                estimatorDescriptor,
                tokenizationBasis
        );
    }

}
