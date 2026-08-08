package io.tokenpilot.core.internal;

import io.tokenpilot.core.TokenEstimator;
import io.tokenpilot.core.domain.TokenCountAccuracy;
import io.tokenpilot.core.domain.TokenCountResult;
import io.tokenpilot.core.domain.TokenCountScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeuristicTokenEstimatorTest {

    private final TokenEstimator estimator = new HeuristicTokenEstimator();

    @Test
    @DisplayName("빈 문자열은 estimate와 upper bound가 0인 결과로 계산한다")
    void estimatesEmptyTextWithZeroEstimateAndUpperBound() {
        TokenCountResult result = estimator.estimate("");

        assertThat(result).isNotNull();
        assertThat(result.isCounted()).isTrue();
        assertThat(result.isUnavailable()).isFalse();
        assertThat(result.tokens()).hasValue(0L);
        assertThat(result.safeUpperBoundTokens()).hasValue(0L);
        assertThat(result.isExact()).isFalse();
        assertThat(result.accuracy()).contains(TokenCountAccuracy.HEURISTIC);
        assertThat(result.scope()).isEqualTo(TokenCountScope.TEXT_ONLY);
    }

    @Test
    @DisplayName("4로 나누어떨어지는 ASCII byte 길이로 estimate를 계산한다")
    void estimatesAsciiTextWhenUtf8ByteLengthIsDivisibleByFour() {
        TokenCountResult result = estimator.estimate("four");

        assertThat(result.tokens()).hasValue(1L);
        assertThat(result.safeUpperBoundTokens()).hasValue(4L);
    }

    @Test
    @DisplayName("4보다 짧은 ASCII byte 길이의 estimate를 올림한다")
    void roundsUpAsciiTextShorterThanFourBytes() {
        TokenCountResult result = estimator.estimate("abc");

        assertThat(result.tokens()).hasValue(1L);
        assertThat(result.safeUpperBoundTokens()).hasValue(3L);
    }

    @Test
    @DisplayName("나머지가 있는 ASCII byte 길이의 estimate를 올림한다")
    void roundsUpAsciiTextWhenUtf8ByteLengthHasRemainder() {
        TokenCountResult result = estimator.estimate("hello");

        assertThat(result.tokens()).hasValue(2L);
        assertThat(result.safeUpperBoundTokens()).hasValue(5L);
    }

    @Test
    @DisplayName("모든 결과에 고정된 estimator와 tokenization 기준을 포함한다")
    void includesFixedEstimatorAndTokenizationMetadata() {
        TokenCountResult result = estimator.estimate("");

        assertThat(result.estimatorDescriptor().estimatorId())
                .isEqualTo("tokenpilot-utf8-byte-heuristic");
        assertThat(result.estimatorDescriptor().estimatorVersion()).isEqualTo("1");
        assertThat(result.tokenizationBasis().id()).isEqualTo("BYTE_LEVEL_BPE_UTF8");
    }

    @Test
    @DisplayName("null 입력을 거부한다")
    void rejectsNullText() {
        assertThatThrownBy(() -> estimator.estimate(null))
                .isInstanceOf(NullPointerException.class);
    }
}
