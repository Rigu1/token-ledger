import static org.assertj.core.api.Assertions.assertThat;

import io.tokenpilot.core.domain.TokenType;
import io.tokenpilot.core.domain.TokenUsage;
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
}
