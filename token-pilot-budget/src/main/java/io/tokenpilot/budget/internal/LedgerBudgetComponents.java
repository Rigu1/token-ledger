package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.BudgetEvaluator;
import io.tokenpilot.budget.BudgetPolicy;
import io.tokenpilot.budget.BudgetStateStore;

import java.time.Clock;

/**
 * 예산 제어 컴포넌트 생성을 위한 팩토리 클래스입니다.
 */
public final class LedgerBudgetComponents {

    private LedgerBudgetComponents() {
    }

    public static BudgetStateStore inMemoryBudgetStateStore() {
        return new InMemoryBudgetStateStore();
    }

    public static BudgetEvaluator defaultBudgetEvaluator(
        BudgetStateStore store,
        BudgetPolicy policy,
        Clock clock
    ) {
        return new DefaultBudgetEvaluator(store, policy, clock);
    }
}
