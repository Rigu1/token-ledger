package io.tokenpilot.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCountResultTest {

    private static final TokenEstimatorDescriptor ESTIMATOR_DESCRIPTOR =
            new TokenEstimatorDescriptor("test-estimator", "1");
    private static final TokenizationBasis TOKENIZATION_BASIS =
            new TokenizationBasis("test-tokenizer");

    @Test
    @DisplayName("제한된 사유로 unavailable 결과를 생성할 수 있다")
    void createsUnavailableResultWithBoundedReason() {
        TokenCountResult result = TokenCountResult.unavailable(
                TokenCountUnavailableReason.ESTIMATOR_UNAVAILABLE,
                TokenCountScope.TEXT_ONLY,
                ESTIMATOR_DESCRIPTOR,
                TOKENIZATION_BASIS
        );

        assertThat(result.isCounted()).isFalse();
        assertThat(result.isUnavailable()).isTrue();
        assertThat(result.isExact()).isFalse();
        assertThat(result.tokens()).isEmpty();
        assertThat(result.safeUpperBoundTokens()).isEmpty();
        assertThat(result.accuracy()).isEmpty();
        assertThat(result.unavailableReason())
                .contains(TokenCountUnavailableReason.ESTIMATOR_UNAVAILABLE);
        assertThat(result.scope()).isEqualTo(TokenCountScope.TEXT_ONLY);
        assertThat(result.estimatorDescriptor()).isEqualTo(ESTIMATOR_DESCRIPTOR);
        assertThat(result.tokenizationBasis()).isEqualTo(TOKENIZATION_BASIS);
    }

    @Test
    @DisplayName("EXACT counted 결과는 계산값과 계산 기준을 제공한다")
    void exposesExactCountedResult() {
        TokenCountResult result = TokenCountResult.counted(
                10L,
                10L,
                TokenCountAccuracy.EXACT,
                TokenCountScope.REQUEST,
                ESTIMATOR_DESCRIPTOR,
                TOKENIZATION_BASIS
        );

        assertThat(result.isCounted()).isTrue();
        assertThat(result.isUnavailable()).isFalse();
        assertThat(result.isExact()).isTrue();
        assertThat(result.tokens()).hasValue(10L);
        assertThat(result.safeUpperBoundTokens()).hasValue(10L);
        assertThat(result.accuracy()).contains(TokenCountAccuracy.EXACT);
        assertThat(result.unavailableReason()).isEmpty();
        assertThat(result.scope()).isEqualTo(TokenCountScope.REQUEST);
        assertThat(result.estimatorDescriptor()).isEqualTo(ESTIMATOR_DESCRIPTOR);
        assertThat(result.tokenizationBasis()).isEqualTo(TOKENIZATION_BASIS);
    }

    @Test
    @DisplayName("HEURISTIC counted 결과는 계산값과 별도의 안전 상한을 제공한다")
    void exposesHeuristicCountedResult() {
        TokenCountResult result = TokenCountResult.counted(
                10L,
                12L,
                TokenCountAccuracy.HEURISTIC,
                TokenCountScope.TEXT_ONLY,
                ESTIMATOR_DESCRIPTOR,
                TOKENIZATION_BASIS
        );

        assertThat(result.isCounted()).isTrue();
        assertThat(result.isExact()).isFalse();
        assertThat(result.tokens()).hasValue(10L);
        assertThat(result.safeUpperBoundTokens()).hasValue(12L);
        assertThat(result.accuracy()).contains(TokenCountAccuracy.HEURISTIC);
        assertThat(result.scope()).isEqualTo(TokenCountScope.TEXT_ONLY);
    }

    @Test
    @DisplayName("정상적인 0-token 결과는 unavailable 결과가 아니다")
    void distinguishesZeroTokenResultFromUnavailableResult() {
        TokenCountResult result = TokenCountResult.counted(
                0L,
                0L,
                TokenCountAccuracy.EXACT,
                TokenCountScope.TEXT_ONLY,
                ESTIMATOR_DESCRIPTOR,
                TOKENIZATION_BASIS
        );

        assertThat(result.isCounted()).isTrue();
        assertThat(result.isUnavailable()).isFalse();
        assertThat(result.tokens()).hasValue(0L);
        assertThat(result.safeUpperBoundTokens()).hasValue(0L);
    }

    @Test
    @DisplayName("unavailable 결과의 reason은 null일 수 없다")
    void rejectsNullUnavailableReason() {
        assertThatThrownBy(() -> TokenCountResult.unavailable(
                null,
                TokenCountScope.TEXT_ONLY,
                ESTIMATOR_DESCRIPTOR,
                TOKENIZATION_BASIS
        ))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("EXACT counted 결과는 계산값과 안전 상한이 다르면 생성할 수 없다")
    void rejectsExactCountedResultWhenTokensDifferFromSafeUpperBound() {
        assertThatThrownBy(() -> TokenCountResult.counted(
                10L,
                11L,
                TokenCountAccuracy.EXACT,
                TokenCountScope.TEXT_ONLY,
                ESTIMATOR_DESCRIPTOR,
                TOKENIZATION_BASIS
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("HEURISTIC counted 결과는 안전 상한이 계산값보다 작으면 생성할 수 없다")
    void rejectsHeuristicCountedResultWhenSafeUpperBoundIsLowerThanTokens() {
        assertThatThrownBy(() -> TokenCountResult.counted(
                10L,
                9L,
                TokenCountAccuracy.HEURISTIC,
                TokenCountScope.TEXT_ONLY,
                ESTIMATOR_DESCRIPTOR,
                TOKENIZATION_BASIS
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("counted 결과의 계산값은 음수일 수 없다")
    void rejectsNegativeTokens() {
        assertThatThrownBy(() -> TokenCountResult.counted(
                -1L,
                0L,
                TokenCountAccuracy.HEURISTIC,
                TokenCountScope.TEXT_ONLY,
                ESTIMATOR_DESCRIPTOR,
                TOKENIZATION_BASIS
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("counted 결과의 안전 상한은 음수일 수 없다")
    void rejectsNegativeSafeUpperBoundTokens() {
        assertThatThrownBy(() -> TokenCountResult.counted(
                0L,
                -1L,
                TokenCountAccuracy.HEURISTIC,
                TokenCountScope.TEXT_ONLY,
                ESTIMATOR_DESCRIPTOR,
                TOKENIZATION_BASIS
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("counted 결과의 accuracy는 null일 수 없다")
    void rejectsNullAccuracy() {
        assertThatThrownBy(() -> TokenCountResult.counted(
                10L,
                10L,
                null,
                TokenCountScope.TEXT_ONLY,
                ESTIMATOR_DESCRIPTOR,
                TOKENIZATION_BASIS
        ))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("counted 결과의 scope는 null일 수 없다")
    void rejectsNullScope() {
        assertThatThrownBy(() -> TokenCountResult.counted(
                10L,
                10L,
                TokenCountAccuracy.EXACT,
                null,
                ESTIMATOR_DESCRIPTOR,
                TOKENIZATION_BASIS
        ))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("counted 결과의 estimator descriptor는 null일 수 없다")
    void rejectsNullEstimatorDescriptor() {
        assertThatThrownBy(() -> TokenCountResult.counted(
                10L,
                10L,
                TokenCountAccuracy.EXACT,
                TokenCountScope.TEXT_ONLY,
                null,
                TOKENIZATION_BASIS
        ))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("counted 결과의 tokenization basis는 null일 수 없다")
    void rejectsNullTokenizationBasis() {
        assertThatThrownBy(() -> TokenCountResult.counted(
                10L,
                10L,
                TokenCountAccuracy.EXACT,
                TokenCountScope.TEXT_ONLY,
                ESTIMATOR_DESCRIPTOR,
                null
        ))
                .isInstanceOf(NullPointerException.class);
    }
}
