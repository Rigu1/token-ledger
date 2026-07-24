package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.BudgetStateStore;
import io.tokenpilot.core.domain.Cost;

import java.util.Currency;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BudgetStateStore의 인메모리 기반 구현체입니다.
 * <p>
 * 예산 사용량을 메모리 내에서 누적 관리하며, 테스트 및 간단한 실행 환경을 위한 구현입니다.
 */
public class InMemoryBudgetStateStore implements BudgetStateStore {

    private final Map<String, Cost> store = new ConcurrentHashMap<>();

    @Override
    public Cost getAccumulatedCost(Map<String, String> tags, Currency currency) {
        return store.getOrDefault(key(tags, currency), Cost.zero(currency));
    }

    @Override
    public void addCost(Map<String, String> tags, Cost cost) {
        store.merge(key(tags, cost.currency()), cost, Cost::add);
    }

    private String key(Map<String, String> tags, Currency currency) {
        return tags.getOrDefault("tenant_id", "default")
                + ":"
                + currency.getCurrencyCode();
    }
}
