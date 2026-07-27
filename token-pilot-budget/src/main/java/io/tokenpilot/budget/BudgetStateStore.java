package io.tokenpilot.budget;

import io.tokenpilot.core.domain.Cost;

/**
 * resolved {@link BudgetKey}별 누적 비용과 limit/currency snapshot을 관리합니다.
 */
public interface BudgetStateStore {

  Cost getAccumulatedCost(BudgetKey key, Cost limit);

  void addCost(BudgetKey key, Cost limit, Cost amount);
}
