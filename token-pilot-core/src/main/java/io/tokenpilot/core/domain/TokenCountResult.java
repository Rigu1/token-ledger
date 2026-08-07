package io.tokenpilot.core.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * LLM 호출 전에 수행한 token 계산의 immutable snapshot입니다.
 * counted 결과와 unavailable 결과는 factory를 통해 상호 배타적으로 생성됩니다.
 */
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

    /**
     * token 계산값이 존재하는 counted 결과를 생성합니다.
     *
     * @param tokens 정보 제공용 token 계산값
     * @param safeUpperBoundTokens 보수적 admission 판단에 사용할 안전 상한
     * @param accuracy 계산 정확도
     * @param scope 계산에 포함된 입력 범위
     * @param estimatorDescriptor 계산에 사용된 estimator 식별 정보
     * @param tokenizationBasis 모델 encoding과 비교할 tokenization 기준
     * @return 검증된 counted 결과
     * @throws IllegalArgumentException token 값이나 accuracy별 상한 관계가 유효하지 않은 경우
     * @throws NullPointerException accuracy, scope, estimatorDescriptor 또는 tokenizationBasis가 null인 경우
     */
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

    /**
     * token 숫자 sentinel 없이 계산 불가 결과를 생성합니다.
     *
     * @param reason 계산 결과를 제공하지 못한 제한된 사유
     * @param scope 계산하려고 한 입력 범위
     * @param estimatorDescriptor 계산을 시도한 estimator 식별 정보
     * @param tokenizationBasis estimator가 선언한 tokenization 기준
     * @return 숫자 계산값을 갖지 않는 unavailable 결과
     * @throws NullPointerException 인자가 null인 경우
     */
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
