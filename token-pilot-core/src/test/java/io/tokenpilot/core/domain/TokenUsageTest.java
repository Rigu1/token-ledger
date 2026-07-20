package io.tokenpilot.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenUsageTest {

    @Test
    @DisplayName("세부량이 없으면 입력과 출력 총량만 사용한다")
    void shouldUseInputAndOutputTotalsWithoutDetails() {
        TokenUsage usage = TokenUsage.from(100, 200);

        assertThat(usage.promptTokens()).isEqualTo(100);
        assertThat(usage.completionTokens()).isEqualTo(200);
        assertThat(usage.totalTokens()).isEqualTo(300);
        assertThat(usage.details()).isEqualTo(new TokenUsageDetails(0, 0, 0));
    }

    @Test
    @DisplayName("입력과 출력 토큰은 모두 0일 수 있다")
    void shouldAllowZeroInputAndOutputTokens() {
        TokenUsage usage = TokenUsage.from(0, 0);

        assertThat(usage.totalTokens()).isZero();
    }

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
    @DisplayName("cached input은 전체 input에 중복 합산되지 않는다")
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

    @Test
    @DisplayName("cached input은 전체 input보다 클 수 없다")
    void shouldRejectCachedInputTokensGreaterThanInputTokens() {
        assertThatThrownBy(() ->
                new TokenUsage(
                        100,
                        200,
                        new TokenUsageDetails(101, 0, 0),
                        Map.of()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "cachedInputTokens must not exceed inputTokens"
                );
    }

    @Test
    @DisplayName("cached output은 전체 output보다 클 수 없다")
    void shouldRejectCachedOutputTokensGreaterThanOutputTokens() {
        assertThatThrownBy(() ->
                new TokenUsage(
                        100,
                        200,
                        new TokenUsageDetails(0, 0, 201),
                        Map.of()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Output details must not exceed outputTokens");
    }

    @Test
    @DisplayName("reasoning과 cached output의 합은 전체 output보다 클 수 없다")
    void shouldRejectOutputDetailsGreaterThanOutputTokens() {
        assertThatThrownBy(() ->
                new TokenUsage(
                        100,
                        200,
                        new TokenUsageDetails(0, 151, 50),
                        Map.of()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Output details must not exceed outputTokens");
    }

    @Test
    @DisplayName("reasoning output은 전체 output보다 클 수 없다")
    void shouldRejectReasoningOutputTokensGreaterThanOutputTokens() {
        assertThatThrownBy(() ->
                new TokenUsage(
                        100,
                        200,
                        new TokenUsageDetails(0, 201, 0),
                        Map.of()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Output details must not exceed outputTokens");
    }

    @Test
    @DisplayName("metadata는 원본 Map의 변경에 영향을 받지 않는다")
    void shouldDefensivelyCopyMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("model", "gpt-4o");
        TokenUsage usage = new TokenUsage(
                100,
                200,
                new TokenUsageDetails(0, 0, 0),
                metadata
        );

        metadata.put("model", "changed");

        assertThat(usage.metadata()).containsEntry("model", "gpt-4o");
    }

    @Test
    @DisplayName("metadata가 null이면 빈 Map을 사용한다")
    void shouldUseEmptyMetadataWhenMetadataIsNull() {
        TokenUsage usage = new TokenUsage(
                100,
                200,
                new TokenUsageDetails(0, 0, 0),
                null
        );

        assertThat(usage.metadata()).isEmpty();
    }

    @Test
    @DisplayName("metadata의 key는 null일 수 없다")
    void shouldRejectMetadataWithNullKey() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(null, "value");

        assertThatThrownBy(() ->
                new TokenUsage(
                        100,
                        200,
                        new TokenUsageDetails(0, 0, 0),
                        metadata
                )
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("metadata의 value는 null일 수 없다")
    void shouldRejectMetadataWithNullValue() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", null);

        assertThatThrownBy(() ->
                new TokenUsage(
                        100,
                        200,
                        new TokenUsageDetails(0, 0, 0),
                        metadata
                )
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("breakdown 합계는 전체 토큰과 같을 수 있다")
    void shouldAllowDetailsEqualToTotalTokens() {
        TokenUsage usage = new TokenUsage(
                100,
                200,
                new TokenUsageDetails(100, 150, 50),
                Map.of()
        );

        assertThat(usage.inputTokens()).isEqualTo(100);
        assertThat(usage.outputTokens()).isEqualTo(200);
    }

    @Test
    @DisplayName("전체 토큰 합계가 long 범위를 넘으면 예외가 발생한다")
    void shouldRejectTotalTokenOverflow() {
        TokenUsage usage = new TokenUsage(
                Long.MAX_VALUE,
                1,
                new TokenUsageDetails(0, 0, 0),
                Map.of()
        );

        assertThatThrownBy(usage::totalTokens)
                .isInstanceOf(ArithmeticException.class);
    }
}
