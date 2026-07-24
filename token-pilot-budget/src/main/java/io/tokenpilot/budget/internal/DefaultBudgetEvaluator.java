package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.BudgetDecision;
import io.tokenpilot.budget.BudgetEvaluator;
import io.tokenpilot.budget.BudgetState;
import io.tokenpilot.budget.BudgetStateStore;
import io.tokenpilot.budget.BudgetThreshold;
import io.tokenpilot.budget.exception.BudgetExceededException;

import io.tokenpilot.core.domain.Cost;
import java.math.BigDecimal;
import java.util.Map;

public class DefaultBudgetEvaluator implements BudgetEvaluator {

    private final BudgetStateStore store;
    private final Cost monthlyLimit;

    public DefaultBudgetEvaluator(
            BudgetStateStore store,
            Cost monthlyLimit
    ) {
        this.store = store;
        this.monthlyLimit = monthlyLimit;
    }

    @Override
    public BudgetDecision evaluate(
            Map<String, String> tags,
            Cost cost
    ) {

        Cost currentUsage = store.getAccumulatedCost(tags, monthlyLimit.currency());
        Cost newUsage = currentUsage.add(cost);

        Cost halfThreshold = threshold(BigDecimal.valueOf(0.5));
        Cost warningThreshold = threshold(BigDecimal.valueOf(0.8));

        if (newUsage.compareTo(monthlyLimit) >= 0) {
            BudgetDecision decision = new BudgetDecision(
                    BudgetState.BLOCK,
                    BudgetThreshold.EXCEEDED,
                    "월 예산을 초과했습니다",
                    newUsage,
                    monthlyLimit
            );
            throw new BudgetExceededException(decision);
        }

        // ✅ 80% 이상
        if (newUsage.compareTo(warningThreshold) >= 0) {
            return new BudgetDecision(
                    BudgetState.WARN,
                    BudgetThreshold.WARNING,
                    "월 예산의 80% 이상 사용",
                    newUsage,
                    monthlyLimit
            );
        }

        // ✅ 50% 이상
        if (newUsage.compareTo(halfThreshold) >= 0) {
            return new BudgetDecision(
                    BudgetState.ALLOW,
                    BudgetThreshold.HALF,
                    "월 예산의 50% 이상 사용",
                    newUsage,
                    monthlyLimit
            );
        }

        // ✅ 정상
        return new BudgetDecision(
                BudgetState.ALLOW,
                BudgetThreshold.NONE,
                "정상 범위 사용",
                newUsage,
                monthlyLimit
        );
    }

    @Override
    public BudgetDecision evaluate(Map<String, String> tags) {

        Cost usage = store.getAccumulatedCost(tags, monthlyLimit.currency());

        Cost halfThreshold = threshold(BigDecimal.valueOf(0.5));
        Cost warningThreshold = threshold(BigDecimal.valueOf(0.8));

        if (usage.compareTo(monthlyLimit) >= 0) {
            return new BudgetDecision(
                    BudgetState.BLOCK,
                    BudgetThreshold.EXCEEDED,
                    "월 예산을 초과했습니다",
                    usage,
                    monthlyLimit
            );
        }

        if (usage.compareTo(warningThreshold) >= 0) {
            return new BudgetDecision(
                    BudgetState.WARN,
                    BudgetThreshold.WARNING,
                    "월 예산의 80% 이상 사용",
                    usage,
                    monthlyLimit
            );
        }

        if (usage.compareTo(halfThreshold) >= 0) {
            return new BudgetDecision(
                    BudgetState.ALLOW,
                    BudgetThreshold.HALF,
                    "월 예산의 50% 이상 사용",
                    usage,
                    monthlyLimit
            );
        }

        return new BudgetDecision(
                BudgetState.ALLOW,
                BudgetThreshold.NONE,
                "정상 범위 사용",
                usage,
                monthlyLimit
        );
    }

    private Cost threshold(BigDecimal rate) {
        return Cost.of(
                monthlyLimit.value().multiply(rate),
                monthlyLimit.currency()
        );
    }
}
