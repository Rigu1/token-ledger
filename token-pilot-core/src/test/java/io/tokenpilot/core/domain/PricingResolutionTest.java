package io.tokenpilot.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PricingResolutionTest {

    @Test
    @DisplayName("PricingResolution은 public API에서 bounded 상태값을 제공한다")
    void exposesPricingResolutionValues() {
        assertThat(PricingResolution.values())
                .containsExactly(
                        PricingResolution.RESOLVED,
                        PricingResolution.MISSING_PLAN,
                        PricingResolution.MISSING_RATE,
                        PricingResolution.CURRENCY_MISMATCH
                );
    }

    @Test
    @DisplayName("RESOLVED 결과는 성공 상태로 표현된다")
    void resolvedIsSuccessful() {
        PricingResolution resolution = PricingResolution.RESOLVED;

        assertThat(resolution.isResolved()).isTrue();
    }

    @Test
    @DisplayName("MISSING_PLAN 결과는 실패 상태로 표현된다")
    void missingPlanIsNotResolved() {
        PricingResolution resolution = PricingResolution.MISSING_PLAN;

        assertThat(resolution.isResolved()).isFalse();
    }

    @Test
    @DisplayName("MISSING_RATE 결과는 실패 상태로 표현된다")
    void missingRateIsNotResolved() {
        PricingResolution resolution = PricingResolution.MISSING_RATE;

        assertThat(resolution.isResolved()).isFalse();
    }

    @Test
    @DisplayName("CURRENCY_MISMATCH 결과는 실패 상태로 표현된다")
    void currencyMismatchIsNotResolved() {
        PricingResolution resolution = PricingResolution.CURRENCY_MISMATCH;

        assertThat(resolution.isResolved()).isFalse();
    }

    @Test
    @DisplayName("PricingResolution은 별도 payload 없이 상태 자체로 표현된다")
    void resolutionItselfIsState() {
        assertThat(PricingResolution.RESOLVED.name()).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("PricingResolution 자체가 low-cardinality pricing miss reason이다")
    void resolutionItselfIsLowCardinalityReason() {
        assertThat(PricingResolution.values())
                .filteredOn(resolution -> !resolution.isResolved())
                .extracting(PricingResolution::name)
                .containsExactly(
                        "MISSING_PLAN",
                        "MISSING_RATE",
                        "CURRENCY_MISMATCH"
                );
    }

    @Test
    @DisplayName("pricing miss reason은 model/tenant/user id를 포함하지 않는다")
    void pricingMissReasonDoesNotContainHighCardinalityIdentifiers() {
        assertThat(PricingResolution.values())
                .filteredOn(resolution -> !resolution.isResolved())
                .extracting(PricingResolution::name)
                .allSatisfy(reason -> assertThat(reason)
                        .doesNotContain("gpt")
                        .doesNotContain("model")
                        .doesNotContain("tenant")
                        .doesNotContain("user")
                        .doesNotContain("policy"));
    }

    @Test
    @DisplayName("PricingResolution은 RECONCILIATION_REQUIRED 상태값을 갖지 않는다")
    void reconciliationRequiredIsNotPricingResolution() {
        assertThat(PricingResolution.values())
                .noneMatch(value -> value.name().equals("RECONCILIATION_REQUIRED"));
    }

    @Test
    @DisplayName("PricingResolution은 UNPRICED 상태값을 갖지 않는다")
    void unpricedIsNotPricingResolution() {
        assertThat(PricingResolution.values())
                .noneMatch(value -> value.name().equals("UNPRICED"));
    }
}
