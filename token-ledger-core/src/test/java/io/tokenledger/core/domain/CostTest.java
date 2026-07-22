package io.tokenledger.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

public class CostTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency KRW = Currency.getInstance("KRW");

    @Test
    void shouldRejectNullAmount() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Cost(null, USD));
    }

    @Test
    void shouldRejectNullCurrency() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Cost(BigDecimal.ONE, null));
    }

    @Test
    void shouldRejectNegativeUsageCost() {
        assertThatThrownBy(() -> Cost.of(new BigDecimal("-0.01"), USD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldPreserveCurrencyWhenCreatingZeroCost() {
        Cost cost = Cost.zero(KRW);

        assertThat(cost.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(cost.currency()).isEqualTo(KRW);
    }

    @Test
    void shouldAddCostsWithSameCurrency() {
        Cost first = Cost.of(new BigDecimal("1.25"), USD);
        Cost second = Cost.of(new BigDecimal("2.75"), USD);

        Cost result = first.add(second);

        assertThat(result.amount()).isEqualByComparingTo("4.00");
        assertThat(result.currency()).isEqualTo(USD);
    }

    @Test
    void shouldRejectAddWithDifferentCurrency() {
        Cost usdCost = Cost.of(BigDecimal.ONE, USD);
        Cost krwCost = Cost.of(BigDecimal.ONE, KRW);

        assertThatThrownBy(() -> usdCost.add(krwCost))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldTreatNumericallyEqualAmountsAsEqualRegardlessOfScale() {
        Cost oneDecimal = Cost.of(new BigDecimal("1.0"), USD);
        Cost twoDecimals = Cost.of(new BigDecimal("1.00"), USD);

        assertThat(oneDecimal).isEqualTo(twoDecimals);
        assertThat(oneDecimal.hashCode()).isEqualTo(twoDecimals.hashCode());
    }

    @Test
    void shouldNotTreatSameAmountWithDifferentCurrencyAsEqual() {
        Cost usdCost = Cost.of(new BigDecimal("1.00"), USD);
        Cost krwCost = Cost.of(new BigDecimal("1.00"), KRW);

        assertThat(usdCost).isNotEqualTo(krwCost);
    }
}