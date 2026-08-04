package io.tokenpilot.core.internal;

import io.tokenpilot.core.domain.Cost;
import io.tokenpilot.core.domain.PricingPlan;
import io.tokenpilot.core.domain.PricingResolution;
import io.tokenpilot.core.domain.TokenType;
import io.tokenpilot.core.domain.TokenUsage;
import io.tokenpilot.core.domain.TokenUsageDetails;
import io.tokenpilot.core.domain.UsageSource;
import io.tokenpilot.core.exception.MissingPricingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCostCalculatorTest {

    private final DefaultCostCalculator calculator = new DefaultCostCalculator();

    @Test
    @DisplayName("기본 입력/출력 토큰 비용이 정확하게 계산되어야 한다")
    void calculateStandardTokens() {
        // Given: Input $0.01/1k, Output $0.03/1k
        PricingPlan plan = new PricingPlan("gpt-4o", new BigDecimal("0.01"), new BigDecimal("0.03"));
        // Usage: Input 1000, Output 2000
        TokenUsage usage = TokenUsage.from(1000, 2000);

        // When
        Cost cost = calculator.calculate(usage, plan);

        // Then: (1000 * 0.01 / 1000) + (2000 * 0.03 / 1000) = 0.01 + 0.06 = 0.07
        assertThat(cost.value()).isEqualByComparingTo("0.070000");
    }

    @Test
    @DisplayName("포괄 총량을 배타적인 과금 구간으로 나누어 중복 없이 계산한다")
    void calculateInclusiveTotalsWithoutDoubleChargingDetails() {
        PricingPlan plan = new PricingPlan(
                "provider-model",
                Map.of(
                        TokenType.PROMPT, new BigDecimal("0.01"),
                        TokenType.CACHE_READ_PROMPT, new BigDecimal("0.002"),
                        TokenType.CACHE_CREATION_PROMPT, new BigDecimal("0.0125"),
                        TokenType.COMPLETION, new BigDecimal("0.03"),
                        TokenType.REASONING, new BigDecimal("0.05")
                ),
                Currency.getInstance("USD")
        );
        TokenUsage usage = new TokenUsage(
                1_000,
                1_000,
                new TokenUsageDetails(400L, 100L, 200L),
                UsageSource.PROVIDER_REPORTED,
                Map.of()
        );

        Cost cost = calculator.calculate(usage, plan);

        // 500 normal input + 400 cache read + 100 cache creation
        // 800 normal output + 200 reasoning output
        assertThat(cost.value()).isEqualByComparingTo("0.041050");
    }

    @Test
    @DisplayName("작은 1 token 비용을 반올림 없이 계산한다")
    void calculateOneTokenWithoutRounding() {
        PricingPlan plan = new PricingPlan(
                "tiny-model",
                new BigDecimal("0.0004"),
                BigDecimal.ZERO
        );

        Cost cost = calculator.calculate(TokenUsage.from(1, 0), plan);

        assertThat(cost.value()).isEqualByComparingTo("0.0000004");
    }

    @Test
    @DisplayName("1,000 token 비용은 1K 단가와 정확히 같다")
    void calculateOneThousandTokensAtRate() {
        PricingPlan plan = new PricingPlan(
                "tiny-model",
                new BigDecimal("0.0004"),
                BigDecimal.ZERO
        );

        Cost cost = calculator.calculate(TokenUsage.from(1_000, 0), plan);

        assertThat(cost.value()).isEqualByComparingTo("0.0004");
    }

    @Test
    @DisplayName("실제 completion 사용량에 필요한 rate가 없으면 MISSING_RATE여야 한다")
    void failWhenActualCompletionRateIsMissing() {
        PricingPlan plan = new PricingPlan(
                "prompt-only-model",
                Map.of(TokenType.PROMPT, new BigDecimal("0.01")),
                Currency.getInstance("USD")
        );
        TokenUsage usage = TokenUsage.from(1_000, 1_000);

        assertThatThrownBy(() -> calculator.calculate(usage, plan))
                .isInstanceOf(MissingPricingException.class)
                .extracting(exception -> ((MissingPricingException) exception).getResolution())
                .isEqualTo(PricingResolution.MISSING_RATE);
    }

    @Test
    @DisplayName("실제 사용량이 없는 token type의 누락 rate는 계산을 실패시키지 않아야 한다")
    void doNotRequireRateWhenActualUsageIsZero() {
        PricingPlan plan = new PricingPlan(
                "prompt-only-model",
                Map.of(TokenType.PROMPT, new BigDecimal("0.01")),
                Currency.getInstance("USD")
        );
        TokenUsage usage = TokenUsage.from(1_000, 0);

        Cost cost = calculator.calculate(usage, plan);

        assertThat(cost.value()).isEqualByComparingTo("0.01");
    }
}
