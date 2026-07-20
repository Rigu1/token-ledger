package domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.tokenpilot.core.domain.TokenType;
import io.tokenpilot.core.domain.TokenUsage;
import io.tokenpilot.core.domain.TokenUsageDetails;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class TokenUsageTest {

    @Test
    @DisplayName("reasoning 토큰은 output total의 부분집합이어야 한다")
    void shouldNotAddReasoningTokensToOutputTotalAgain() {
        TokenUsage usage = TokenUsage.from(
                100,
                200,
                150
        );

        assertThat(usage.promptTokens()).isEqualTo(100);
        assertThat(usage.completionTokens()).isEqualTo(200);
        assertThat(usage.totalTokens()).isEqualTo(300);
        assertThat(usage.getCount(TokenType.REASONING)).isEqualTo(150);
    }

    @Test
    @DisplayName("cache의 input이 중복되면 안된다.")
    void shouldNotAddCachedInputTokensToInputTotalAgain() {
        TokenUsage usage = new TokenUsage(
                100,
                200,
                new TokenUsageDetails(
                        40, // cachedInputTokens
                        0,  // reasoningOutputTokens
                        0   // cachedOutputTokens
                ),
                Map.of()
        );

        assertThat(usage.promptTokens()).isEqualTo(100);
        assertThat(usage.completionTokens()).isEqualTo(200);
        assertThat(usage.totalTokens()).isEqualTo(300);
        assertThat(usage.getCount(TokenType.CACHED_PROMPT)).isEqualTo(40);
    }

    @Test
    @DisplayName("input 토큰은 음수일 수 없다")
    void shouldRejectNegativeInputTokens() {
        assertThatThrownBy(() ->
                new TokenUsage(
                        -1,
                        0,
                        new TokenUsageDetails(0, 0, 0),
                        Map.of()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("inputTokens must be non-negative");
    }

    @Test
    @DisplayName("output 토큰은 음수일 수 없다")
    void shouldRejectNegativeOutputTokens() {
        assertThatThrownBy(() ->
                new TokenUsage(
                        0,
                        -1,
                        new TokenUsageDetails(0, 0, 0),
                        Map.of()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outputTokens must be non-negative");
    }

    @Test
    @DisplayName("토큰 세부 정보는 null일 수 없다")
    void shouldRejectNullTokenUsageDetails() {
        assertThatThrownBy(() ->
                new TokenUsage(100, 200, null, Map.of())
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("details must not be null");
    }
}
