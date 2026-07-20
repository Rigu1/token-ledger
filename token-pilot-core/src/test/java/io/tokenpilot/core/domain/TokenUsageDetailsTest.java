package io.tokenpilot.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenUsageDetailsTest {

    @Test
    @DisplayName("cache read input 토큰은 음수일 수 없다")
    void shouldRejectNegativeCacheReadInputTokens() {
        assertThatThrownBy(() ->
                new TokenUsageDetails(-1L, 0L, 0L)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("cache creation input 토큰은 음수일 수 없다")
    void shouldRejectNegativeCacheCreationInputTokens() {
        assertThatThrownBy(() ->
                new TokenUsageDetails(0L, -1L, 0L)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("reasoning output 토큰은 음수일 수 없다")
    void shouldRejectNegativeReasoningOutputTokens() {
        assertThatThrownBy(() ->
                new TokenUsageDetails(0L, 0L, -1L)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("미보고 세부량과 보고된 0을 구분한다")
    void shouldDistinguishUnreportedDetailsFromReportedZero() {
        TokenUsageDetails unreported = TokenUsageDetails.unreported();
        TokenUsageDetails reportedZero = new TokenUsageDetails(0L, 0L, 0L);

        assertThat(unreported.cacheReadInputTokens()).isNull();
        assertThat(unreported.cacheCreationInputTokens()).isNull();
        assertThat(unreported.reasoningOutputTokens()).isNull();
        assertThat(reportedZero.cacheReadInputTokens()).isZero();
        assertThat(reportedZero.cacheCreationInputTokens()).isZero();
        assertThat(reportedZero.reasoningOutputTokens()).isZero();
    }
}
