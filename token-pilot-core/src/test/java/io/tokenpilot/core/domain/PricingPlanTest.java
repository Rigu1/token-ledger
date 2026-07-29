package io.tokenpilot.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PricingPlanTest {

    @Test
    @DisplayName("명시적으로 등록된 token type rate는 RESOLVED로 표현한다")
    void resolveExplicitRate() {
        PricingPlan plan = new PricingPlan(
                "gpt-4o",
                Map.of(TokenType.PROMPT, new BigDecimal("0.01")),
                Currency.getInstance("USD")
        );

        PricingResolution resolution = plan.resolveRate(TokenType.PROMPT);

        assertThat(resolution.status()).isEqualTo(PricingResolutionStatus.RESOLVED);
        assertThat(resolution.isResolved()).isTrue();
    }

    @Test
    @DisplayName("명시적으로 등록된 0 rate는 RESOLVED 무료 가격으로 표현한다")
    void resolveExplicitZeroRate() {
        PricingPlan plan = new PricingPlan(
                "free-model",
                Map.of(TokenType.PROMPT, BigDecimal.ZERO),
                Currency.getInstance("USD")
        );

        PricingResolution resolution = plan.resolveRate(TokenType.PROMPT);

        assertThat(resolution.status()).isEqualTo(PricingResolutionStatus.RESOLVED);
        assertThat(resolution.isResolved()).isTrue();
    }

    @Test
    @DisplayName("등록된 plan에 필요한 token type rate가 없으면 MISSING_RATE로 표현한다")
    void resolveMissingRate() {
        PricingPlan plan = new PricingPlan(
                "prompt-only-model",
                Map.of(TokenType.PROMPT, new BigDecimal("0.01")),
                Currency.getInstance("USD")
        );

        PricingResolution resolution = plan.resolveRate(TokenType.COMPLETION);

        assertThat(resolution.status()).isEqualTo(PricingResolutionStatus.MISSING_RATE);
        assertThat(resolution.isResolved()).isFalse();
    }

    @Test
    @DisplayName("legacy getRate의 0 fallback과 resolveRate의 missing 표현은 구분된다")
    void distinguishLegacyZeroFallbackFromMissingResolution() {
        PricingPlan plan = new PricingPlan(
                "prompt-only-model",
                Map.of(TokenType.PROMPT, new BigDecimal("0.01")),
                Currency.getInstance("USD")
        );

        assertThat(plan.getRate(TokenType.COMPLETION)).isEqualByComparingTo(BigDecimal.ZERO);

        PricingResolution resolution = plan.resolveRate(TokenType.COMPLETION);
        assertThat(resolution.status()).isEqualTo(PricingResolutionStatus.MISSING_RATE);
        assertThat(resolution.isResolved()).isFalse();
    }
}
