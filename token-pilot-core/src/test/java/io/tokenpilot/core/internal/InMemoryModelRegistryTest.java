package io.tokenpilot.core.internal;

import io.tokenpilot.core.ModelRegistry;
import io.tokenpilot.core.PricingRegistry;
import io.tokenpilot.core.domain.ModelDefinition;
import io.tokenpilot.core.domain.PricingPlan;
import io.tokenpilot.core.domain.TokenizationBasis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryModelRegistryTest {

    private static final ModelDefinition GPT = new ModelDefinition(
            "gpt-4o-2024-08-06",
            Set.of("gpt-4o"),
            "o200k_base",
            new TokenizationBasis("o200k_base"),
            128_000,
            "standard",
            "catalog-v1",
            URI.create("https://example.com/gpt-4o"),
            Instant.parse("2026-08-14T00:00:00Z")
    );

    @Test
    @DisplayName("canonical id와 alias는 같은 immutable definition으로 해석된다")
    void resolvesCanonicalIdAndAliasToSameDefinition() {
        ModelRegistry registry = new InMemoryModelRegistry(List.of(GPT));

        assertThat(registry.find("gpt-4o")).containsSame(GPT);
        assertThat(registry.find("gpt-4o-2024-08-06")).containsSame(GPT);
        assertThat(registry.find("Gpt-4o")).isEmpty();
        assertThat(registry.find(" gpt-4o ")).isEmpty();
        assertThat(registry.find("unknown-model")).isEmpty();
    }

    @Test
    @DisplayName("registry는 생성 이후 입력 collection 변경의 영향을 받지 않는다")
    void copiesInputDefinitions() {
        List<ModelDefinition> definitions = new ArrayList<>(List.of(GPT));
        ModelRegistry registry = new InMemoryModelRegistry(definitions);

        definitions.clear();

        assertThat(registry.find("gpt-4o")).containsSame(GPT);
    }

    @Test
    @DisplayName("canonical model과 pricing policy를 함께 사용해 가격을 조회한다")
    void linksCanonicalModelToPricingPolicy() {
        ModelRegistry models = new InMemoryModelRegistry(List.of(GPT));
        PricingRegistry pricing = new InMemoryPricingRegistry();
        PricingPlan plan = new PricingPlan(
                GPT.canonicalModelId(),
                GPT.pricingPolicyId(),
                Map.of(io.tokenpilot.core.domain.TokenType.PROMPT, new BigDecimal("0.001")),
                Currency.getInstance("USD")
        );
        pricing.registerPlan(plan);

        ModelDefinition resolved = models.find("gpt-4o").orElseThrow();

        assertThat(pricing.resolveSnapshot(models, "gpt-4o"))
                .isPresent()
                .get()
                .satisfies(snapshot -> {
                    assertThat(snapshot.modelId()).isEqualTo(GPT.canonicalModelId());
                    assertThat(snapshot.pricingPolicyId()).isEqualTo(GPT.pricingPolicyId());
                    assertThat(snapshot.catalogVersion()).isEqualTo(GPT.catalogVersion());
                });
    }

    @Test
    @DisplayName("model 정의와 가격 정책의 통화가 다르면 snapshot을 만들지 않는다")
    void rejectsPricingCurrencyMismatch() {
        PricingRegistry pricing = new InMemoryPricingRegistry();
        pricing.registerPlan(new PricingPlan(
                GPT.canonicalModelId(),
                GPT.pricingPolicyId(),
                Map.of(io.tokenpilot.core.domain.TokenType.PROMPT, new BigDecimal("0.001")),
                Currency.getInstance("EUR")
        ));

        assertThat(pricing.resolveSnapshot(GPT)).isEmpty();
    }

    @Test
    @DisplayName("canonical id 또는 alias 중복 등록을 거부한다")
    void rejectsDuplicateCanonicalAndAlias() {
        assertThatThrownBy(() -> new InMemoryModelRegistry(List.of(
                GPT,
                definition("gpt-4o-2024-08-06", Set.of("other"))
        )))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InMemoryModelRegistry(List.of(
                GPT,
                definition("other-model", Set.of("gpt-4o"))
        )))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("기본 catalog는 versioned canonical model과 alias를 제공한다")
    void exposesDefaultCatalog() {
        ModelRegistry registry = LedgerComponents.defaultModelRegistry();

        ModelDefinition mini = registry.find("gpt-4o-mini").orElseThrow();
        ModelDefinition full = registry.find("gpt-4o").orElseThrow();

        assertThat(mini.canonicalModelId()).isEqualTo("gpt-4o-mini-2024-07-18");
        assertThat(full.canonicalModelId()).isEqualTo("gpt-4o-2024-08-06");
        assertThat(mini.maxContextTokens()).isEqualTo(128_000);
        assertThat(mini.sourceUri()).hasScheme("https");
        assertThat(mini.sourceAsOf()).isEqualTo(Instant.parse("2026-08-14T00:00:00Z"));
    }

    private static ModelDefinition definition(String canonicalId, Set<String> aliases) {
        return new ModelDefinition(
                canonicalId,
                aliases,
                "o200k_base",
                new TokenizationBasis("o200k_base"),
                128_000,
                "standard",
                "catalog-v1",
                URI.create("https://example.com/model"),
                Instant.parse("2026-08-14T00:00:00Z")
        );
    }
}
