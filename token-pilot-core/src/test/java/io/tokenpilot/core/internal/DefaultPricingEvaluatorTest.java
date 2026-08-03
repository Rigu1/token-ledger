package io.tokenpilot.core.internal;

import io.tokenpilot.core.PricingEvaluator;
import io.tokenpilot.core.domain.PricingPlan;
import io.tokenpilot.core.domain.PricingReconciliationResult;
import io.tokenpilot.core.domain.PricingResolution;
import io.tokenpilot.core.domain.PricingSnapshot;
import io.tokenpilot.core.domain.TokenType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPricingEvaluatorTest {

    private final PricingEvaluator evaluator = LedgerComponents.defaultPricingEvaluator();

    @Test
    @DisplayName("snapshot이 없으면 MISSING_PLAN이어야 한다")
    void resolveMissingSnapshotAsMissingPlan() {
        PricingResolution resolution = evaluator.validateSnapshotRates(Optional.empty());

        assertThat(resolution).isEqualTo(PricingResolution.MISSING_PLAN);
    }

    @Test
    @DisplayName("prompt와 completion rate가 있으면 snapshot rate 검증에 성공해야 한다")
    void validateRequiredSnapshotRates() {
        PricingSnapshot snapshot = snapshot(Map.of(
                TokenType.PROMPT, new BigDecimal("0.01"),
                TokenType.COMPLETION, new BigDecimal("0.03")
        ));

        PricingResolution resolution = evaluator.validateSnapshotRates(Optional.of(snapshot));

        assertThat(resolution).isEqualTo(PricingResolution.RESOLVED);
    }

    @Test
    @DisplayName("completion rate가 없으면 snapshot rate 검증은 MISSING_RATE여야 한다")
    void rejectSnapshotWithoutCompletionRate() {
        PricingSnapshot snapshot = snapshot(Map.of(
                TokenType.PROMPT, new BigDecimal("0.01")
        ));

        PricingResolution resolution = evaluator.validateSnapshotRates(Optional.of(snapshot));

        assertThat(resolution).isEqualTo(PricingResolution.MISSING_RATE);
    }

    @Test
    @DisplayName("snapshot model과 actual model이 같으면 RECONCILED여야 한다")
    void reconcileMatchingActualModel() {
        PricingSnapshot snapshot = snapshot(Map.of(
                TokenType.PROMPT, new BigDecimal("0.01"),
                TokenType.COMPLETION, new BigDecimal("0.03")
        ));

        PricingReconciliationResult result = evaluator.determineReconciliation(
                Optional.of(snapshot),
                "gpt-4o"
        );

        assertThat(result).isEqualTo(PricingReconciliationResult.RECONCILED);
    }

    @Test
    @DisplayName("snapshot model과 actual model이 다르면 RECONCILIATION_REQUIRED여야 한다")
    void requireReconciliationForDifferentActualModel() {
        PricingSnapshot snapshot = snapshot(Map.of(
                TokenType.PROMPT, new BigDecimal("0.01"),
                TokenType.COMPLETION, new BigDecimal("0.03")
        ));

        PricingReconciliationResult result = evaluator.determineReconciliation(
                Optional.of(snapshot),
                "gpt-4o-mini"
        );

        assertThat(result).isEqualTo(PricingReconciliationResult.RECONCILIATION_REQUIRED);
    }

    @Test
    @DisplayName("snapshot이 없으면 reconciliation 결과는 UNPRICED여야 한다")
    void leaveMissingSnapshotUnpriced() {
        PricingReconciliationResult result = evaluator.determineReconciliation(
                Optional.empty(),
                "gpt-4o"
        );

        assertThat(result).isEqualTo(PricingReconciliationResult.UNPRICED);
    }

    private static PricingSnapshot snapshot(Map<TokenType, BigDecimal> rates) {
        PricingPlan plan = new PricingPlan(
                "gpt-4o",
                "standard",
                rates,
                Currency.getInstance("USD")
        );
        return PricingSnapshot.from(
                plan,
                PricingSnapshot.DEFAULT_CATALOG_VERSION,
                Instant.parse("2026-07-30T00:00:00Z")
        );
    }
}
