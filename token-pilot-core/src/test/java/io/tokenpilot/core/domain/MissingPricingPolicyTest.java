package io.tokenpilot.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissingPricingPolicyTest {

    @Test
    @DisplayName("MissingPricingPolicy는 FAIL_OPEN과 FAIL_CLOSED를 제공한다")
    void exposesMissingPricingPolicies() {
        assertThat(MissingPricingPolicy.values())
                .containsExactly(
                        MissingPricingPolicy.FAIL_OPEN,
                        MissingPricingPolicy.FAIL_CLOSED
                );
    }

    @Test
    @DisplayName("PricingResolution은 pricing 상태만 표현한다")
    void pricingResolutionDoesNotIncludePolicyStates() {
        assertThat(PricingResolution.values())
                .extracting(Enum::name)
                .doesNotContain("FAIL_OPEN", "FAIL_CLOSED");
    }
}
