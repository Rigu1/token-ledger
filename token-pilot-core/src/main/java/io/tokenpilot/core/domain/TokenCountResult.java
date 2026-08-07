package io.tokenpilot.core.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * LLM 호출 전에 수행한 token 계산의 immutable snapshot입니다.
 * 계산된 결과와 계산할 수 없는 결과를 상호 배타적으로 표현합니다.
 */
public final class TokenCountResult {
    private final ResultState state;
    private final TokenCountScope scope;
    private final TokenEstimatorDescriptor estimatorDescriptor;
    private final TokenizationBasis tokenizationBasis;

    private TokenCountResult(
            ResultState state,
            TokenCountScope scope,
            TokenEstimatorDescriptor estimatorDescriptor,
            TokenizationBasis tokenizationBasis
    ) {
        this.state = state;
        this.scope = scope;
        this.estimatorDescriptor = estimatorDescriptor;
        this.tokenizationBasis = tokenizationBasis;
    }

    /**
     * token 계산값이 존재하는 counted 결과를 생성합니다.
     *
     * @param tokens               정보 제공용 token 계산값
     * @param safeUpperBoundTokens 보수적 admission 판단에 사용할 안전 상한
     * @param accuracy             계산 정확도
     * @param scope                계산에 포함된 입력 범위
     * @param estimatorDescriptor  계산에 사용된 estimator 식별 정보
     * @param tokenizationBasis    모델 encoding과 비교할 tokenization 기준
     * @return 검증된 counted 결과
     * @throws IllegalArgumentException token 값이나 accuracy별 상한 관계가 유효하지 않은 경우
     * @throws NullPointerException     accuracy, scope, estimatorDescriptor 또는 tokenizationBasis가 null인 경우
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
                new Counted(tokens, safeUpperBoundTokens, accuracy),
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
     * token 계산 불가 결과를 생성합니다.
     *
     * @param reason              계산 결과를 제공하지 못한 제한된 사유
     * @param scope               계산하려고 한 입력 범위
     * @param estimatorDescriptor 계산을 시도한 estimator 식별 정보
     * @param tokenizationBasis   estimator가 선언한 tokenization 기준
     * @return unavailable 결과
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
                new Unavailable(reason),
                scope,
                estimatorDescriptor,
                tokenizationBasis
        );
    }

    /**
     * token 계산값이 존재하는 counted 상태인지 확인합니다.
     *
     * @return counted 상태이면 true
     */
    public boolean isCounted() {
        return state instanceof Counted;
    }

    /**
     * counted 결과가 정확한 계산값인지 확인합니다.
     *
     * @return accuracy가 EXACT이면 true, HEURISTIC 또는 unavailable이면 false
     */
    public boolean isExact() {
        if (state instanceof Counted counted) {
            return counted.accuracy() == TokenCountAccuracy.EXACT;
        }
        return false;
    }

    /**
     * token 계산값을 제공하지 못한 unavailable 상태인지 확인합니다.
     *
     * @return unavailable 상태이면 true
     */
    public boolean isUnavailable() {
        return state instanceof Unavailable;
    }

    /**
     * 정보 제공용 token 계산값을 반환합니다.
     *
     * @return counted 상태의 계산값, unavailable 상태이면 empty
     */
    public OptionalLong tokens() {
        if (state instanceof Counted counted) {
            return OptionalLong.of(counted.tokens());
        }
        return OptionalLong.empty();
    }

    /**
     * 보수적 admission 판단에 사용할 안전 상한을 반환합니다.
     *
     * @return counted 상태의 안전 상한, unavailable 상태이면 empty
     */
    public OptionalLong safeUpperBoundTokens() {
        if (state instanceof Counted counted) {
            return OptionalLong.of(counted.safeUpperBoundTokens());
        }
        return OptionalLong.empty();
    }

    /**
     * counted 결과의 계산 정확도를 반환합니다.
     *
     * @return counted 상태의 정확도, unavailable 상태이면 empty
     */
    public Optional<TokenCountAccuracy> accuracy() {
        if (state instanceof Counted counted) {
            return Optional.of(counted.accuracy());
        }
        return Optional.empty();
    }

    /**
     * token 계산 결과를 제공하지 못한 사유를 반환합니다.
     *
     * @return unavailable 상태의 사유, counted 상태이면 empty
     */
    public Optional<TokenCountUnavailableReason> unavailableReason() {
        if (state instanceof Unavailable(TokenCountUnavailableReason reason)) {
            return Optional.of(reason);
        }
        return Optional.empty();
    }

    /**
     * token 계산에 포함된 입력 범위를 반환합니다.
     *
     * @return 생성 시점의 계산 범위
     */
    public TokenCountScope scope() {
        return scope;
    }

    /**
     * token 계산에 사용된 estimator 식별 정보를 반환합니다.
     *
     * @return 생성 시점의 estimator 식별 정보
     */
    public TokenEstimatorDescriptor estimatorDescriptor() {
        return estimatorDescriptor;
    }

    /**
     * 모델 encoding과 비교할 tokenization 기준을 반환합니다.
     *
     * @return 생성 시점의 tokenization 기준
     */
    public TokenizationBasis tokenizationBasis() {
        return tokenizationBasis;
    }

    private sealed interface ResultState permits Counted, Unavailable {
    }

    private record Counted(
            long tokens,
            long safeUpperBoundTokens,
            TokenCountAccuracy accuracy
    ) implements ResultState {
    }

    private record Unavailable(
            TokenCountUnavailableReason reason
    ) implements ResultState {
    }
}
