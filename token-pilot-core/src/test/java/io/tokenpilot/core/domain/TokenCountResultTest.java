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

        assertThat(result).isNotNull();
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
