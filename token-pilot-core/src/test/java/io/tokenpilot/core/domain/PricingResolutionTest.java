package io.tokenpilot.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingResolutionTest {

    @Test
    @DisplayName("PricingResolutionStatus는 public API에서 bounded 상태값을 제공한다")
    void exposesPricingResolutionStatuses() {
        assertThat(PricingResolutionStatus.values())
                .containsExactly(
                        PricingResolutionStatus.RESOLVED,
                        PricingResolutionStatus.MISSING_PLAN,
                        PricingResolutionStatus.MISSING_RATE,
                        PricingResolutionStatus.CURRENCY_MISMATCH
                );
    }

    @Test
    @DisplayName("RESOLVED 결과는 성공 상태로 표현된다")
    void resolvedIsSuccessful() {
        PricingResolution resolution = PricingResolution.resolved();

        assertThat(resolution.status()).isEqualTo(PricingResolutionStatus.RESOLVED);
        assertThat(resolution.isResolved()).isTrue();
    }

    @Test
    @DisplayName("MISSING_PLAN 결과는 실패 상태로 표현된다")
    void missingPlanIsNotResolved() {
        PricingResolution resolution = PricingResolution.missingPlan();

        assertThat(resolution.status()).isEqualTo(PricingResolutionStatus.MISSING_PLAN);
        assertThat(resolution.isResolved()).isFalse();
    }

    @Test
    @DisplayName("MISSING_RATE 결과는 실패 상태로 표현된다")
    void missingRateIsNotResolved() {
        PricingResolution resolution = PricingResolution.missingRate();

        assertThat(resolution.status()).isEqualTo(PricingResolutionStatus.MISSING_RATE);
        assertThat(resolution.isResolved()).isFalse();
    }

    @Test
    @DisplayName("CURRENCY_MISMATCH 결과는 실패 상태로 표현된다")
    void currencyMismatchIsNotResolved() {
        PricingResolution resolution = PricingResolution.currencyMismatch();

        assertThat(resolution.status()).isEqualTo(PricingResolutionStatus.CURRENCY_MISMATCH);
        assertThat(resolution.isResolved()).isFalse();
    }

    @Test
    @DisplayName("PricingResolution은 null status를 허용하지 않는다")
    void rejectsNullStatus() {
        assertThatThrownBy(() -> new PricingResolution(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status must not be null");
    }

    @Test
    @DisplayName("PricingResolutionStatus 자체가 low-cardinality pricing miss reason이다")
    void statusItselfIsLowCardinalityReason() {
        assertThat(PricingResolutionStatus.MISSING_PLAN.name()).isEqualTo("MISSING_PLAN");
        assertThat(PricingResolutionStatus.MISSING_RATE.name()).isEqualTo("MISSING_RATE");
        assertThat(PricingResolutionStatus.CURRENCY_MISMATCH.name()).isEqualTo("CURRENCY_MISMATCH");
    }
}
