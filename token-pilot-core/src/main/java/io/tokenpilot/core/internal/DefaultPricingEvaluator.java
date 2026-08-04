package io.tokenpilot.core.internal;

import io.tokenpilot.core.PricingEvaluator;
import io.tokenpilot.core.domain.PricingPlan;
import io.tokenpilot.core.domain.PricingReconciliationResult;
import io.tokenpilot.core.domain.PricingResolution;
import io.tokenpilot.core.domain.PricingSnapshot;
import io.tokenpilot.core.domain.TokenType;

import java.util.Objects;
import java.util.Optional;

class DefaultPricingEvaluator implements PricingEvaluator {

    @Override
    public PricingResolution validateSnapshotRates(Optional<PricingSnapshot> snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");

        if (snapshot.isEmpty()) {
            return PricingResolution.MISSING_PLAN;
        }

        return resolveRequiredRates(snapshot.get());
    }

    @Override
    public PricingReconciliationResult determineReconciliation(
            Optional<PricingSnapshot> snapshot,
            String actualModelId
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");

        if (snapshot.isEmpty()) {
            return PricingReconciliationResult.UNPRICED;
        }

        if (!snapshot.get().modelId().equals(actualModelId)) {
            return PricingReconciliationResult.RECONCILIATION_REQUIRED;
        }

        return PricingReconciliationResult.RECONCILED;
    }

    private PricingResolution resolveRequiredRates(PricingSnapshot snapshot) {
        PricingPlan plan = new PricingPlan(
                snapshot.modelId(),
                snapshot.pricingPolicyId(),
                snapshot.rates(),
                snapshot.currency()
        );

        PricingResolution promptResolution = plan.resolveRate(TokenType.PROMPT);
        if (!promptResolution.isResolved()) {
            return promptResolution;
        }

        return plan.resolveRate(TokenType.COMPLETION);
    }
}
