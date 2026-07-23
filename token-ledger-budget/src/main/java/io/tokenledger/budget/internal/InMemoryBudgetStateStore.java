package io.tokenledger.budget.internal;

import io.tokenledger.budget.BudgetStateStore;

import io.tokenledger.core.domain.Cost;
import java.math.BigDecimal;
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

    private String key(Map<String, String> tags) {
        return tags.getOrDefault("tenant_id", "default");
    }

    // 아직 사용 기록이 없으면 0원
    @Override
    public Cost getAccumulatedCost(Map<String, String> tags, Currency currency) {
        return store.getOrDefault(key(tags), Cost.zero(currency));
    }

    // 기존 값에 amount를 더함
    @Override
    public void addCost(Map<String, String> tags, Cost cost) {
        store.merge(key(tags), cost, Cost::add);
    }
}
