package io.tokenpilot.core;

import io.tokenpilot.core.domain.Cost;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class RoundingPolicyTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void 외부_비용_경계에서_scale_6_HALF_UP을_적용한다() {
        Cost rounded = RoundingPolicy.COST_BOUNDARY_ROUNDING.apply(
                Cost.of(new BigDecimal("0.0000005"), USD)
        );

        assertThat(rounded.value()).isEqualByComparingTo("0.000001");
        assertThat(rounded.value().scale()).isEqualTo(6);
        assertThat(rounded.currency()).isEqualTo(USD);
    }

    @Test
    void 작은_비용은_누적한_뒤_한_번만_반올림한다() {
        Cost unitCost = Cost.of(new BigDecimal("0.0000004"), USD);
        Cost accumulated = Cost.zero(USD);

        for (int index = 0; index < 1_000; index++) {
            accumulated = accumulated.add(unitCost);
        }

        Cost rounded = RoundingPolicy.COST_BOUNDARY_ROUNDING.apply(accumulated);

        assertThat(accumulated.value()).isEqualByComparingTo("0.0004000");
        assertThat(rounded.value()).isEqualByComparingTo("0.000400");
        assertThat(rounded.value().scale()).isEqualTo(6);
    }
}
