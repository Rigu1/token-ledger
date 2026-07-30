package io.tokenpilot.core.internal;

import io.tokenpilot.core.domain.PricingPlan;
import io.tokenpilot.core.domain.PricingResolution;
import io.tokenpilot.core.domain.TokenType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryPricingRegistryTest {

    private final InMemoryPricingRegistry registry = new InMemoryPricingRegistry();

    @Test
    @DisplayName("가격 정책을 등록하고 모델 ID로 조회할 수 있어야 한다")
    void shouldRegisterAndGetPlan() {
        // Given
        PricingPlan plan = new PricingPlan("claude-3",
                new BigDecimal("0.015"), new BigDecimal("0.075"), Currency.getInstance("USD"));

        // When
        registry.registerPlan(plan);
        Optional<PricingPlan> retrieved = registry.getPlan("claude-3");

        // Then
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().modelId()).isEqualTo("claude-3");
        assertThat(retrieved.get().promptPricePerK()).isEqualByComparingTo("0.015");
    }

    @Test
    @DisplayName("등록되지 않은 모델 조회 시 빈 Optional을 반환해야 한다")
    void shouldReturnEmptyWhenNotFound() {
        // When
        Optional<PricingPlan> retrieved = registry.getPlan("non-existent");

        // Then
        assertThat(retrieved).isEmpty();
    }

    @Test
    @DisplayName("등록되지 않은 모델의 가격 결정 결과는 MISSING_PLAN이어야 한다")
    void shouldResolveMissingPlanWhenModelIsNotRegistered() {
        PricingResolution resolution = registry.resolveRate("non-existent", TokenType.PROMPT);

        assertThat(resolution).isEqualTo(PricingResolution.MISSING_PLAN);
        assertThat(resolution.isResolved()).isFalse();
    }

    @Test
    @DisplayName("등록된 모델의 가격 결정 결과는 PricingPlan resolveRate 결과를 따라야 한다")
    void shouldDelegateRateResolutionToRegisteredPlan() {
        PricingPlan plan = new PricingPlan(
                "prompt-only-model",
                Map.of(TokenType.PROMPT, new BigDecimal("0.015")),
                Currency.getInstance("USD")
        );
        registry.registerPlan(plan);

        assertThat(registry.resolveRate("prompt-only-model", TokenType.PROMPT))
                .isEqualTo(PricingResolution.RESOLVED);
        assertThat(registry.resolveRate("prompt-only-model", TokenType.COMPLETION))
                .isEqualTo(PricingResolution.MISSING_RATE);
    }

    @Test
    @DisplayName("기대 통화와 plan 통화가 다르면 CURRENCY_MISMATCH여야 한다")
    void shouldResolveCurrencyMismatchWhenExpectedCurrencyDiffers() {
        PricingPlan plan = new PricingPlan(
                "usd-model",
                Map.of(TokenType.PROMPT, new BigDecimal("0.015")),
                Currency.getInstance("USD")
        );
        registry.registerPlan(plan);

        PricingResolution resolution = registry.resolveRate(
                "usd-model",
                TokenType.PROMPT,
                Currency.getInstance("KRW")
        );

        assertThat(resolution).isEqualTo(PricingResolution.CURRENCY_MISMATCH);
        assertThat(resolution.isResolved()).isFalse();
    }

    @Test
    @DisplayName("기대 통화와 plan 통화가 같으면 일반 rate resolution을 수행해야 한다")
    void shouldDelegateRateResolutionWhenExpectedCurrencyMatches() {
        PricingPlan plan = new PricingPlan(
                "usd-model",
                Map.of(TokenType.PROMPT, new BigDecimal("0.015")),
                Currency.getInstance("USD")
        );
        registry.registerPlan(plan);

        assertThat(registry.resolveRate("usd-model", TokenType.PROMPT, Currency.getInstance("USD")))
                .isEqualTo(PricingResolution.RESOLVED);
        assertThat(registry.resolveRate("usd-model", TokenType.COMPLETION, Currency.getInstance("USD")))
                .isEqualTo(PricingResolution.MISSING_RATE);
    }

    @Test
    @DisplayName("기대 통화가 없는 경로는 통화 검사를 수행하지 않고 일반 rate resolution을 수행해야 한다")
    void shouldSkipCurrencyCheckWhenExpectedCurrencyIsNotProvided() {
        PricingPlan plan = new PricingPlan(
                "krw-model",
                Map.of(TokenType.PROMPT, new BigDecimal("15")),
                Currency.getInstance("KRW")
        );
        registry.registerPlan(plan);

        assertThat(registry.resolveRate("krw-model", TokenType.PROMPT))
                .isEqualTo(PricingResolution.RESOLVED);
        assertThat(registry.resolveRate("krw-model", TokenType.COMPLETION))
                .isEqualTo(PricingResolution.MISSING_RATE);
    }

    @Test
    @DisplayName("기대 통화가 있는 경로는 null expectedCurrency를 허용하지 않는다")
    void shouldRejectNullExpectedCurrency() {
        assertThatThrownBy(() -> registry.resolveRate("any-model", TokenType.PROMPT, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("expectedCurrency must not be null");
    }
}
