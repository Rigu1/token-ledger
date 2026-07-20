package domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.tokenpilot.core.domain.TokenUsageDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class TokenUsageDetailTest {

    @Test
    @DisplayName("cached input 토큰은 음수일 수 없다")
    void shouldRejectNegativeCachedInputTokens() {
        assertThatThrownBy(() ->
                new TokenUsageDetails(-1, 0, 0)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("reasoning output 토큰은 음수일 수 없다")
    void shouldRejectNegativeReasoningOutputTokens() {
        assertThatThrownBy(() ->
                new TokenUsageDetails(0, -1, 0)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("cached output 토큰은 음수일 수 없다")
    void shouldRejectNegativeCachedOutputTokens() {
        assertThatThrownBy(() ->
                new TokenUsageDetails(0, 0, -1)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("모든 세부 토큰은 0일 수 있다")
    void shouldAllowZeroDetailTokens() {
        TokenUsageDetails details = new TokenUsageDetails(0, 0, 0);

        assertThat(details.cachedInputTokens()).isZero();
        assertThat(details.reasoningOutputTokens()).isZero();
        assertThat(details.cachedOutputTokens()).isZero();
    }
}
