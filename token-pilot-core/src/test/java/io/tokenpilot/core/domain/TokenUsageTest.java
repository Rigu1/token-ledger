package io.tokenpilot.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenUsageTest {

    @Test
    @DisplayName("세부량이 보고되지 않으면 입력과 출력 총량만 사용한다")
    void shouldUseInputAndOutputTotalsWithoutReportedDetails() {
        TokenUsage usage = TokenUsage.from(100, 200);

        assertThat(usage.inputTokens()).isEqualTo(100);
        assertThat(usage.outputTokens()).isEqualTo(200);
        assertThat(usage.totalTokens()).isEqualTo(300);
        assertThat(usage.details()).isEqualTo(TokenUsageDetails.unreported());
        assertThat(usage.source()).isEqualTo(UsageSource.PROVIDER_REPORTED);
    }

    @Test
    @DisplayName("입력과 출력 토큰은 모두 0일 수 있다")
    void shouldAllowZeroInputAndOutputTokens() {
        assertThat(TokenUsage.from(0, 0).totalTokens()).isZero();
    }

    @Test
    @DisplayName("reasoning 토큰은 output total에 중복 합산되지 않는다")
    void shouldNotAddReasoningTokensToOutputTotalAgain() {
        TokenUsage usage = TokenUsage.from(100, 200, 150);

        assertThat(usage.inputTokens()).isEqualTo(100);
        assertThat(usage.outputTokens()).isEqualTo(200);
        assertThat(usage.totalTokens()).isEqualTo(300);
        assertThat(usage.details().reasoningOutputTokens()).isEqualTo(150);
        assertThat(usage.getCount(TokenType.REASONING)).isEqualTo(150);
    }

    @Test
    @DisplayName("cache read와 creation 토큰은 input total에 중복 합산되지 않는다")
    void shouldNotAddCacheBreakdownToInputTotalAgain() {
        TokenUsage usage = reportedUsage(
                100,
                200,
                new TokenUsageDetails(40L, 20L, null),
                Map.of()
        );

        assertThat(usage.totalTokens()).isEqualTo(300);
        assertThat(usage.getCount(TokenType.CACHE_READ_PROMPT)).isEqualTo(40);
        assertThat(usage.getCount(TokenType.CACHE_CREATION_PROMPT)).isEqualTo(20);
    }

    @Test
    @DisplayName("input 토큰은 음수일 수 없다")
    void shouldRejectNegativeInputTokens() {
        assertThatThrownBy(() -> reportedUsage(-1, 0, TokenUsageDetails.unreported(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("inputTokens must be non-negative");
    }

    @Test
    @DisplayName("output 토큰은 음수일 수 없다")
    void shouldRejectNegativeOutputTokens() {
        assertThatThrownBy(() -> reportedUsage(0, -1, TokenUsageDetails.unreported(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outputTokens must be non-negative");
    }

    @Test
    @DisplayName("토큰 세부 정보는 null일 수 없다")
    void shouldRejectNullTokenUsageDetails() {
        assertThatThrownBy(() -> reportedUsage(100, 200, null, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("details must not be null");
    }

    @Test
    @DisplayName("사용량 출처는 null일 수 없다")
    void shouldRejectNullUsageSource() {
        assertThatThrownBy(() -> new TokenUsage(
                100,
                200,
                TokenUsageDetails.unreported(),
                null,
                Map.of()
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("source must not be null");
    }

    @Test
    @DisplayName("cache read 토큰은 전체 input보다 클 수 없다")
    void shouldRejectCacheReadInputTokensGreaterThanInputTokens() {
        assertThatThrownBy(() -> reportedUsage(
                100,
                200,
                new TokenUsageDetails(101L, null, null),
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Input details must not exceed inputTokens");
    }

    @Test
    @DisplayName("cache creation 토큰은 전체 input보다 클 수 없다")
    void shouldRejectCacheCreationInputTokensGreaterThanInputTokens() {
        assertThatThrownBy(() -> reportedUsage(
                100,
                200,
                new TokenUsageDetails(null, 101L, null),
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Input details must not exceed inputTokens");
    }

    @Test
    @DisplayName("cache read와 creation의 합은 전체 input보다 클 수 없다")
    void shouldRejectCacheBreakdownGreaterThanInputTokens() {
        assertThatThrownBy(() -> reportedUsage(
                100,
                200,
                new TokenUsageDetails(60L, 41L, null),
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Input details must not exceed inputTokens");
    }

    @Test
    @DisplayName("cache breakdown 검증은 long overflow 없이 수행한다")
    void shouldRejectCacheBreakdownWithoutOverflow() {
        assertThatThrownBy(() -> reportedUsage(
                Long.MAX_VALUE,
                0,
                new TokenUsageDetails(Long.MAX_VALUE, 1L, null),
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Input details must not exceed inputTokens");
    }

    @Test
    @DisplayName("reasoning output은 전체 output보다 클 수 없다")
    void shouldRejectReasoningOutputTokensGreaterThanOutputTokens() {
        assertThatThrownBy(() -> reportedUsage(
                100,
                200,
                new TokenUsageDetails(null, null, 201L),
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reasoningOutputTokens must not exceed outputTokens");
    }

    @Test
    @DisplayName("metadata는 원본 Map의 변경에 영향을 받지 않는다")
    void shouldDefensivelyCopyMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("model", "gpt-4o");
        TokenUsage usage = reportedUsage(100, 200, TokenUsageDetails.unreported(), metadata);

        metadata.put("model", "changed");

        assertThat(usage.metadata()).containsEntry("model", "gpt-4o");
    }

    @Test
    @DisplayName("metadata가 null이면 빈 Map을 사용한다")
    void shouldUseEmptyMetadataWhenMetadataIsNull() {
        TokenUsage usage = reportedUsage(100, 200, TokenUsageDetails.unreported(), null);

        assertThat(usage.metadata()).isEmpty();
    }

    @Test
    @DisplayName("metadata의 key는 null일 수 없다")
    void shouldRejectMetadataWithNullKey() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(null, "value");

        assertThatThrownBy(() -> reportedUsage(100, 200, TokenUsageDetails.unreported(), metadata))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("metadata의 value는 null일 수 없다")
    void shouldRejectMetadataWithNullValue() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", null);

        assertThatThrownBy(() -> reportedUsage(100, 200, TokenUsageDetails.unreported(), metadata))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("breakdown 합계는 전체 토큰과 같을 수 있다")
    void shouldAllowDetailsEqualToTotalTokens() {
        TokenUsage usage = reportedUsage(
                100,
                200,
                new TokenUsageDetails(70L, 30L, 200L),
                Map.of()
        );

        assertThat(usage.inputTokens()).isEqualTo(100);
        assertThat(usage.outputTokens()).isEqualTo(200);
    }

    @Test
    @DisplayName("전체 토큰 합계가 long 범위를 넘으면 예외가 발생한다")
    void shouldRejectTotalTokenOverflow() {
        TokenUsage usage = reportedUsage(
                Long.MAX_VALUE,
                1,
                TokenUsageDetails.unreported(),
                Map.of()
        );

        assertThatThrownBy(usage::totalTokens)
                .isInstanceOf(ArithmeticException.class);
    }

    private TokenUsage reportedUsage(
            long inputTokens,
            long outputTokens,
            TokenUsageDetails details,
            Map<String, Object> metadata
    ) {
        return new TokenUsage(
                inputTokens,
                outputTokens,
                details,
                UsageSource.PROVIDER_REPORTED,
                metadata
        );
    }
}
