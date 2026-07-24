package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.BudgetEvaluator;
import io.tokenpilot.budget.BudgetStateStore;
import io.tokenpilot.core.domain.Cost;

/**
 * 예산 제어 컴포넌트 생성을 위한 팩토리 클래스입니다.
 */
public final class LedgerBudgetComponents {

    private LedgerBudgetComponents() {
    }

    public static BudgetStateStore inMemoryBudgetStateStore() {
        return new InMemoryBudgetStateStore();
    }

    public static BudgetEvaluator defaultBudgetEvaluator(BudgetStateStore store, Cost monthlyLimit) {
        return new DefaultBudgetEvaluator(store, monthlyLimit);
    }
}
