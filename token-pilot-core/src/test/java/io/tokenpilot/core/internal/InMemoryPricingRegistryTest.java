package io.tokenpilot.core.internal;

import io.tokenpilot.core.domain.PricingPlan;
import io.tokenpilot.core.domain.PricingResolution;
import io.tokenpilot.core.domain.PricingSnapshot;
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
        String modelId = "claude-3-5-sonnet-20241022";
        PricingPlan plan = new PricingPlan(modelId,
                new BigDecimal("0.015"), new BigDecimal("0.075"), Currency.getInstance("USD"));

        registry.registerPlan(plan);
        Optional<PricingPlan> retrieved = registry.getPlan(modelId);

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().modelId()).isEqualTo(modelId);
        assertThat(retrieved.get().promptPricePerK()).isEqualByComparingTo("0.015");
    }

    @Test
    @DisplayName("모델 ID와 pricing policy ID로 가격 정책을 조회할 수 있어야 한다")
    void shouldRegisterAndGetPlanByModelIdAndPricingPolicyId() {
        String modelId = "gpt-4o-2024-08-06";
        String pricingPolicyId = "openai-gpt-4o-2024-08-06-standard";
        PricingPlan plan = new PricingPlan(
                modelId,
                pricingPolicyId,
                Map.of(TokenType.PROMPT, new BigDecimal("0.0025")),
                Currency.getInstance("USD")
        );

        registry.registerPlan(plan);
        Optional<PricingPlan> retrieved = registry.getPlan(modelId, pricingPolicyId);

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().modelId()).isEqualTo(modelId);
        assertThat(retrieved.get().pricingPolicyId()).isEqualTo(pricingPolicyId);
    }

    @Test
    @DisplayName("동일 모델 ID라도 pricing policy ID가 다르면 다른 가격 정책으로 조회되어야 한다")
    void shouldDistinguishPlansByPricingPolicyId() {
        String modelId = "gpt-4o-2024-08-06";
        PricingPlan standardPlan = new PricingPlan(
                modelId,
                "standard",
                Map.of(TokenType.PROMPT, new BigDecimal("0.0025")),
                Currency.getInstance("USD")
        );
        PricingPlan discountedPlan = new PricingPlan(
                modelId,
                "discounted",
                Map.of(TokenType.PROMPT, new BigDecimal("0.0010")),
                Currency.getInstance("USD")
        );

        registry.registerPlan(standardPlan);
        registry.registerPlan(discountedPlan);

        assertThat(registry.getPlan(modelId, "standard")).contains(standardPlan);
        assertThat(registry.getPlan(modelId, "discounted")).contains(discountedPlan);
    }

    @Test
    @DisplayName("모델 ID와 pricing policy ID로 요청 단위 pricing snapshot을 조회할 수 있어야 한다")
    void shouldGetSnapshotByModelIdAndPricingPolicyId() {
        String modelId = "gpt-4o-2024-08-06";
        String pricingPolicyId = "standard";
        PricingPlan plan = new PricingPlan(
                modelId,
                pricingPolicyId,
                Map.of(TokenType.PROMPT, new BigDecimal("0.0025")),
                Currency.getInstance("USD")
        );

        registry.registerPlan(plan);

        Optional<PricingSnapshot> snapshot = registry.resolveSnapshot(modelId, pricingPolicyId);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().modelId()).isEqualTo(modelId);
        assertThat(snapshot.get().pricingPolicyId()).isEqualTo(pricingPolicyId);
        assertThat(snapshot.get().catalogVersion()).isEqualTo(PricingSnapshot.DEFAULT_CATALOG_VERSION);
        assertThat(snapshot.get().checkedAt()).isNotNull();
        assertThat(snapshot.get().currency()).isEqualTo(Currency.getInstance("USD"));
        assertThat(snapshot.get().rates()).containsEntry(TokenType.PROMPT, new BigDecimal("0.0025"));
    }

    @Test
    @DisplayName("#32가 같은 model id로 resolve한 입력은 동일 pricing policy snapshot을 사용해야 한다")
    void shouldUseSameSnapshotWhenResolvedModelIdIsSame() {
        String modelId = "gpt-4o-2024-08-06";
        String aliasResolvedModelId = modelId;
        String canonicalModelId = modelId;
        String pricingPolicyId = "standard";
        PricingPlan plan = new PricingPlan(
                modelId,
                pricingPolicyId,
                Map.of(TokenType.PROMPT, new BigDecimal("0.0025")),
                Currency.getInstance("USD")
        );
        registry.registerPlan(plan);

        PricingSnapshot aliasSnapshot = registry.resolveSnapshot(aliasResolvedModelId, pricingPolicyId).orElseThrow();
        PricingSnapshot canonicalSnapshot = registry.resolveSnapshot(canonicalModelId, pricingPolicyId).orElseThrow();

        assertThat(aliasSnapshot.modelId()).isEqualTo(canonicalSnapshot.modelId());
        assertThat(aliasSnapshot.pricingPolicyId()).isEqualTo(canonicalSnapshot.pricingPolicyId());
        assertThat(aliasSnapshot.catalogVersion()).isEqualTo(canonicalSnapshot.catalogVersion());
        assertThat(aliasSnapshot.currency()).isEqualTo(canonicalSnapshot.currency());
        assertThat(aliasSnapshot.rates()).containsAllEntriesOf(canonicalSnapshot.rates());
    }

    @Test
    @DisplayName("registry 변경 후 새 요청은 변경된 pricing plan으로 snapshot을 resolve해야 한다")
    void shouldResolveNewSnapshotAfterRegistryChanges() {
        String modelId = "gpt-4o-2024-08-06";
        String pricingPolicyId = "standard";
        registry.registerPlan(new PricingPlan(
                modelId,
                pricingPolicyId,
                Map.of(TokenType.PROMPT, new BigDecimal("0.0025")),
                Currency.getInstance("USD")
        ));
        PricingSnapshot firstSnapshot = registry.resolveSnapshot(modelId, pricingPolicyId).orElseThrow();

        registry.registerPlan(new PricingPlan(
                modelId,
                pricingPolicyId,
                Map.of(TokenType.PROMPT, new BigDecimal("0.0050")),
                Currency.getInstance("USD")
        ));

        PricingSnapshot nextSnapshot = registry.resolveSnapshot(modelId, pricingPolicyId).orElseThrow();

        assertThat(firstSnapshot.rates()).containsEntry(TokenType.PROMPT, new BigDecimal("0.0025"));
        assertThat(nextSnapshot.rates()).containsEntry(TokenType.PROMPT, new BigDecimal("0.0050"));
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
    @DisplayName("currency mismatch는 등록된 pricing 상태를 변경하지 않아야 한다")
    void shouldNotChangePricingStateWhenCurrencyMismatches() {
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
        assertThat(registry.getPlan("usd-model")).contains(plan);
        assertThat(registry.resolveSnapshot("usd-model", PricingPlan.DEFAULT_PRICING_POLICY_ID))
                .isPresent()
                .get()
                .satisfies(snapshot -> {
                    assertThat(snapshot.modelId()).isEqualTo("usd-model");
                    assertThat(snapshot.pricingPolicyId()).isEqualTo(PricingPlan.DEFAULT_PRICING_POLICY_ID);
                    assertThat(snapshot.currency()).isEqualTo(Currency.getInstance("USD"));
                    assertThat(snapshot.rates()).containsEntry(TokenType.PROMPT, new BigDecimal("0.015"));
                });
        assertThat(registry.resolveRate("usd-model", TokenType.PROMPT, Currency.getInstance("USD")))
                .isEqualTo(PricingResolution.RESOLVED);
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
