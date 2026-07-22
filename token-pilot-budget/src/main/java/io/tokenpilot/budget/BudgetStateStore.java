package io.tokenpilot.budget;

import io.tokenpilot.core.domain.Cost;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * resolved {@link BudgetKey}별 누적 비용과 limit/currency snapshot을 관리합니다.
 */
public interface BudgetStateStore {

  BigDecimal getAccumulatedCost(BudgetKey key, BigDecimal limit, Currency currency);

  void addCost(BudgetKey key, BigDecimal limit, Currency currency, Cost amount);
}
