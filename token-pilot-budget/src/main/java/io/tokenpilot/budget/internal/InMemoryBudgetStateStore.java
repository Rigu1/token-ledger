package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.BudgetKey;
import io.tokenpilot.budget.BudgetStateStore;
import io.tokenpilot.core.domain.Cost;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * resolved {@link BudgetKey}별 비용을 관리하는 인메모리 저장소입니다.
 */
public class InMemoryBudgetStateStore implements BudgetStateStore {

  private final ConcurrentMap<BudgetKey, Bucket> store = new ConcurrentHashMap<>();

  @Override
  public Cost getAccumulatedCost(BudgetKey key, Cost limit) {
    validateArguments(key, limit);
    Bucket bucket = store.get(key);
    if (bucket == null) {
      return Cost.zero(limit.currency());
    }
    bucket.validate(limit);
    return bucket.accumulatedCost();
  }

  @Override
  public void addCost(BudgetKey key, Cost limit, Cost amount) {
    validateArguments(key, limit);
    Objects.requireNonNull(amount, "amount must not be null");
    if (!limit.currency().equals(amount.currency())) {
      throw new IllegalArgumentException("Budget currency does not match cost currency");
    }

    store.compute(key, (ignored, bucket) -> {
      if (bucket == null) {
        return new Bucket(limit, amount);
      }
      bucket.validate(limit);
      return new Bucket(limit, bucket.accumulatedCost().add(amount));
    });
  }

  private void validateArguments(BudgetKey key, Cost limit) {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(limit, "limit must not be null");
  }

  private record Bucket(Cost limit, Cost accumulatedCost) {

    private void validate(Cost expectedLimit) {
      if (!limit.equals(expectedLimit)) {
        throw new IllegalArgumentException("Budget policy snapshot changed for an existing key");
      }
    }
  }
}
