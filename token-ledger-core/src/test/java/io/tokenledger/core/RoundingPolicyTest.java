package io.tokenledger.core;

import io.tokenledger.core.domain.Cost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class RoundingPolicyTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Test
    @DisplayName("비용 경계 반올림은 scale 6, HALF_UP을 적용한다")
    void shouldRoundCostWithScaleSixAndHalfUp() {
        Cost cost = Cost.of(new BigDecimal("0.0000005"), USD);

        Cost roundedCost = RoundingPolicy.COST_BOUNDARY_ROUNDING.apply(cost);

        assertThat(roundedCost.amount()).isEqualByComparingTo("0.000001");
        assertThat(roundedCost.amount().scale()).isEqualTo(6);
        assertThat(roundedCost.currency()).isEqualTo(USD);
    }

    @Test
    @DisplayName("비용 경계 반올림은 통화를 보존한다")
    void shouldPreserveCurrencyWhenRoundingCost() {
        Currency krw = Currency.getInstance("KRW");
        Cost cost = Cost.of(new BigDecimal("12.3456789"), krw);

        Cost roundedCost = RoundingPolicy.COST_BOUNDARY_ROUNDING.apply(cost);

        assertThat(roundedCost.amount()).isEqualByComparingTo("12.345679");
        assertThat(roundedCost.currency()).isEqualTo(krw);
    }
}