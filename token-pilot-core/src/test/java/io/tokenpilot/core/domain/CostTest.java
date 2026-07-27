package io.tokenpilot.core.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency KRW = Currency.getInstance("KRW");

    @Test
    void amount와_currency는_필수다() {
        assertThatNullPointerException().isThrownBy(() -> new Cost(null, USD));
        assertThatNullPointerException().isThrownBy(() -> new Cost(BigDecimal.ONE, null));
    }

    @Test
    void 음수_cost를_거부한다() {
        assertThatThrownBy(() -> Cost.of(new BigDecimal("-0.01"), USD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zero는_지정한_통화를_보존한다() {
        Cost cost = Cost.zero(KRW);

        assertThat(cost.value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(cost.currency()).isEqualTo(KRW);
    }

    @Test
    void 같은_통화만_산술과_비교를_허용한다() {
        Cost oneUsd = Cost.of(new BigDecimal("1.00"), USD);
        Cost twoUsd = Cost.of(new BigDecimal("2.00"), USD);
        Cost oneKrw = Cost.of(new BigDecimal("1.00"), KRW);

        assertThat(oneUsd.add(twoUsd).value()).isEqualByComparingTo("3.00");
        assertThat(oneUsd.compareTo(twoUsd)).isNegative();
        assertThatThrownBy(() -> oneUsd.add(oneKrw))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> oneUsd.compareTo(oneKrw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scale이_달라도_금액과_통화가_같으면_동일하다() {
        Cost oneDecimal = Cost.of(new BigDecimal("1.0"), USD);
        Cost twoDecimals = Cost.of(new BigDecimal("1.00"), USD);

        assertThat(oneDecimal).isEqualTo(twoDecimals);
        assertThat(oneDecimal.hashCode()).isEqualTo(twoDecimals.hashCode());
    }

    @Test
    void 작은_비용을_생성할_때_반올림하지_않는다() {
        Cost cost = Cost.of(new BigDecimal("0.0000004"), USD);

        assertThat(cost.value()).isEqualByComparingTo("0.0000004");
        assertThat(cost.value()).isNotEqualByComparingTo(BigDecimal.ZERO);
    }
}
