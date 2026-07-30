package io.tokenpilot.core.internal;

import io.tokenpilot.core.PricingRegistry;
import io.tokenpilot.core.PricingProvider;
import io.tokenpilot.core.domain.PricingPlan;
import io.tokenpilot.core.domain.PricingResolution;
import io.tokenpilot.core.domain.TokenType;

import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 메모리 기반 가격 정책 저장소 구현체.
 */
class InMemoryPricingRegistry implements PricingRegistry {
    private final Map<String, PricingPlan> plans = new ConcurrentHashMap<>();

    public InMemoryPricingRegistry() {
    }

    public InMemoryPricingRegistry(List<PricingProvider> providers) {
        if (providers != null) {
            providers.stream()
                    .map(PricingProvider::getAllPlans)
                    .flatMap(Collection::stream)
                    .forEach(this::registerPlan);
        }
    }

    @Override
    public Optional<PricingPlan> getPlan(String modelId) {
        return Optional.ofNullable(plans.get(modelId));
    }

    @Override
    public PricingResolution resolveRate(String modelId, TokenType tokenType) {
        return getPlan(modelId)
                .map(plan -> plan.resolveRate(tokenType))
                .orElse(PricingResolution.MISSING_PLAN);
    }

    @Override
    public PricingResolution resolveRate(String modelId, TokenType tokenType, Currency expectedCurrency) {
        Objects.requireNonNull(expectedCurrency, "expectedCurrency must not be null");

        return getPlan(modelId)
                .map(plan -> {
                    if (!plan.currency().equals(expectedCurrency)) {
                        return PricingResolution.CURRENCY_MISMATCH;
                    }

                    return plan.resolveRate(tokenType);
                })
                .orElse(PricingResolution.MISSING_PLAN);
    }

    @Override
    public void registerPlan(PricingPlan plan) {
        plans.put(plan.modelId(), plan);
    }
}
