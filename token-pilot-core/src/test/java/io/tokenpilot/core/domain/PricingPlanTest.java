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

        assertThat(resolution).isEqualTo(PricingResolution.RESOLVED);
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

        assertThat(resolution).isEqualTo(PricingResolution.RESOLVED);
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

        assertThat(resolution).isEqualTo(PricingResolution.MISSING_RATE);
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
        assertThat(resolution).isEqualTo(PricingResolution.MISSING_RATE);
        assertThat(resolution.isResolved()).isFalse();
    }

    @Test
    @DisplayName("COMPLETION rate가 명시적으로 있으면 REASONING fallback은 RESOLVED다")
    void resolveReasoningFallbackFromExplicitCompletionRate() {
        PricingPlan plan = new PricingPlan(
                "completion-model",
                Map.of(TokenType.COMPLETION, new BigDecimal("0.03")),
                Currency.getInstance("USD")
        );

        PricingResolution resolution = plan.resolveRate(TokenType.REASONING);

        assertThat(resolution).isEqualTo(PricingResolution.RESOLVED);
        assertThat(resolution.isResolved()).isTrue();
    }

    @Test
    @DisplayName("COMPLETION rate가 없으면 REASONING fallback은 MISSING_RATE다")
    void missingReasoningFallbackWithoutCompletionRate() {
        PricingPlan plan = new PricingPlan(
                "prompt-only-model",
                Map.of(TokenType.PROMPT, new BigDecimal("0.01")),
                Currency.getInstance("USD")
        );

        PricingResolution resolution = plan.resolveRate(TokenType.REASONING);

        assertThat(resolution).isEqualTo(PricingResolution.MISSING_RATE);
        assertThat(resolution.isResolved()).isFalse();
    }

    @Test
    @DisplayName("PROMPT rate가 명시적으로 있으면 cache token type fallback은 RESOLVED다")
    void resolveCacheFallbackFromExplicitPromptRate() {
        PricingPlan plan = new PricingPlan(
                "prompt-model",
                Map.of(TokenType.PROMPT, new BigDecimal("0.01")),
                Currency.getInstance("USD")
        );

        PricingResolution readResolution = plan.resolveRate(TokenType.CACHE_READ_PROMPT);
        PricingResolution creationResolution = plan.resolveRate(TokenType.CACHE_CREATION_PROMPT);

        assertThat(readResolution).isEqualTo(PricingResolution.RESOLVED);
        assertThat(readResolution.isResolved()).isTrue();
        assertThat(creationResolution).isEqualTo(PricingResolution.RESOLVED);
        assertThat(creationResolution.isResolved()).isTrue();
    }

    @Test
    @DisplayName("PROMPT rate가 없으면 cache token type fallback은 MISSING_RATE다")
    void missingCacheFallbackWithoutPromptRate() {
        PricingPlan plan = new PricingPlan(
                "completion-only-model",
                Map.of(TokenType.COMPLETION, new BigDecimal("0.03")),
                Currency.getInstance("USD")
        );

        PricingResolution readResolution = plan.resolveRate(TokenType.CACHE_READ_PROMPT);
        PricingResolution creationResolution = plan.resolveRate(TokenType.CACHE_CREATION_PROMPT);

        assertThat(readResolution).isEqualTo(PricingResolution.MISSING_RATE);
        assertThat(readResolution.isResolved()).isFalse();
        assertThat(creationResolution).isEqualTo(PricingResolution.MISSING_RATE);
        assertThat(creationResolution.isResolved()).isFalse();
    }

    @Test
    @DisplayName("fallback 기준 rate가 0으로 명시되어 있으면 RESOLVED다")
    void resolveFallbackFromExplicitZeroBaseRate() {
        PricingPlan plan = new PricingPlan(
                "zero-fallback-model",
                Map.of(
                        TokenType.PROMPT, BigDecimal.ZERO,
                        TokenType.COMPLETION, BigDecimal.ZERO
                ),
                Currency.getInstance("USD")
        );

        assertThat(plan.resolveRate(TokenType.REASONING)).isEqualTo(PricingResolution.RESOLVED);
        assertThat(plan.resolveRate(TokenType.CACHE_READ_PROMPT)).isEqualTo(PricingResolution.RESOLVED);
        assertThat(plan.resolveRate(TokenType.CACHE_CREATION_PROMPT)).isEqualTo(PricingResolution.RESOLVED);
    }

    @Test
    @DisplayName("빈 rates plan은 생성 가능하고 필요한 rate를 MISSING_RATE로 표현한다")
    void emptyRatesResolveMissingRate() {
        PricingPlan plan = new PricingPlan(
                "empty-rates-model",
                Map.of(),
                Currency.getInstance("USD")
        );

        assertThat(plan.resolveRate(TokenType.PROMPT)).isEqualTo(PricingResolution.MISSING_RATE);
    }
}
