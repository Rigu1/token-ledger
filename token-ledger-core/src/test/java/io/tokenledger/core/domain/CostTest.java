package io.tokenledger.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CostTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency KRW = Currency.getInstance("KRW");

    @Test
    @DisplayName("amount null을 거부한다.")
    void shouldRejectNullAmount() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Cost(null, USD));
    }

    @Test
    @DisplayName("currency null을 거부한다.\n")
    void shouldRejectNullCurrency() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Cost(BigDecimal.ONE, null));
    }

    @Test
    @DisplayName("usage cost는 음수를 거부한다.\n")
    void shouldRejectNegativeUsageCost() {
        assertThatThrownBy(() -> Cost.of(new BigDecimal("-0.01"), USD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("0 값을 만들 때 통화를 보존해야 한다.")
    void shouldPreserveCurrencyWhenCreatingZeroCost() {
        Cost cost = Cost.zero(KRW);

        assertThat(cost.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(cost.currency()).isEqualTo(KRW);
    }

    @Test
    @DisplayName("null 비용을 추가하면 거부해야 한다.")
    void shouldRejectAddWithNullCost() {
        Cost cost = Cost.of(BigDecimal.ONE, USD);

        assertThatNullPointerException()
                .isThrownBy(() -> cost.add(null));
    }

    @Test
    @DisplayName("동일한 통화로 비용을 추가해야 한다.")
    void shouldAddCostsWithSameCurrency() {
        Cost first = Cost.of(new BigDecimal("1.25"), USD);
        Cost second = Cost.of(new BigDecimal("2.75"), USD);

        Cost result = first.add(second);

        assertThat(result.amount()).isEqualByComparingTo("4.00");
        assertThat(result.currency()).isEqualTo(USD);
    }

    @Test
    @DisplayName("다른 통화로 비용을 추가하면 거부해야 한다.")
    void shouldRejectAddWithDifferentCurrency() {
        Cost usdCost = Cost.of(BigDecimal.ONE, USD);
        Cost krwCost = Cost.of(BigDecimal.ONE, KRW);

        assertThatThrownBy(() -> usdCost.add(krwCost))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 비용과 비교하면 거부해야 한다.")
    void shouldRejectCompareWithNullCost() {
        Cost cost = Cost.of(BigDecimal.ONE, USD);

        assertThatNullPointerException()
                .isThrownBy(() -> cost.compareTo(null));
    }

    @Test
    @DisplayName("동일한 통화로 비용을 비교해야 한다.")
    void shouldCompareCostsWithSameCurrency() {
        Cost lower = Cost.of(new BigDecimal("1.25"), USD);
        Cost higher = Cost.of(new BigDecimal("2.75"), USD);
        Cost sameAmount = Cost.of(new BigDecimal("1.250"), USD);

        assertThat(lower.compareTo(higher)).isNegative();
        assertThat(higher.compareTo(lower)).isPositive();
        assertThat(lower.compareTo(sameAmount)).isZero();
    }

    @Test
    @DisplayName("다른 통화로 비용을 비교하면 거부해야 한다.")
    void shouldRejectCompareWithDifferentCurrency() {
        Cost usdCost = Cost.of(BigDecimal.ONE, USD);
        Cost krwCost = Cost.of(BigDecimal.ONE, KRW);

        assertThatThrownBy(() -> usdCost.compareTo(krwCost))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("수치적으로 동일한 금액은 동일하게 취급해야 한다.")
    void shouldTreatNumericallyEqualAmountsAsEqualRegardlessOfScale() {
        Cost oneDecimal = Cost.of(new BigDecimal("1.0"), USD);
        Cost twoDecimals = Cost.of(new BigDecimal("1.00"), USD);

        assertThat(oneDecimal).isEqualTo(twoDecimals);
        assertThat(oneDecimal.hashCode()).isEqualTo(twoDecimals.hashCode());
    }

    @Test
    @DisplayName("수치적으로 동일하지만 다른 통화를 동일하게 취급하면 안된다.")
    void shouldNotTreatSameAmountWithDifferentCurrencyAsEqual() {
        Cost usdCost = Cost.of(new BigDecimal("1.00"), USD);
        Cost krwCost = Cost.of(new BigDecimal("1.00"), KRW);

        assertThat(usdCost).isNotEqualTo(krwCost);
    }

    @Test
    @DisplayName("0.0000004 비용이 Cost 생성 시 0이 되지 않는다.")
    void shouldTreatZeroCostAsZero() {
        Cost cost = Cost.of(new BigDecimal("0.0000004"), USD);

        assertThat(cost.amount()).isEqualByComparingTo("0.0000004");
        assertThat(cost.amount()).isNotEqualByComparingTo(BigDecimal.ZERO);
    }
}