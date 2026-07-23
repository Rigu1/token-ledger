package io.tokenpilot.core.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * 계산된 AI 호출 비용 정보.
 *
 * @param value    비용 (BigDecimal)
 * @param currency 통화 (Currency)
 */
public record Cost(
        BigDecimal value,
        Currency currency
) {

    public Cost {
        Objects.requireNonNull(value, "value cant be null");
        Objects.requireNonNull(currency, "currency cant be null");

        validateNonNegativeAmount(value);
    }

    private static void validateNonNegativeAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cost value must not be negative");
        }
    }

    public static Cost of(BigDecimal value, Currency currency) {
        return new Cost(value, currency);
    }

    public static Cost zero(Currency currency) {
        return of(BigDecimal.ZERO, currency);
    }

    public Cost add(Cost other) {
        Objects.requireNonNull(other, "cost cant be null");
        validateSameCurrency(other);

        BigDecimal totalAmount = value.add(other.value);

        return new Cost(totalAmount, currency);
    }

    public int compareTo(Cost other) {
        Objects.requireNonNull(other, "cost cant be null");
        validateSameCurrency(other);

        return value.compareTo(other.value);
    }

    private void validateSameCurrency(Cost other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot operate on costs with different currencies");
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Cost other)) {
            return false;
        }

        return value.compareTo(other.value) == 0
                && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value.stripTrailingZeros(), currency);
    }
}
