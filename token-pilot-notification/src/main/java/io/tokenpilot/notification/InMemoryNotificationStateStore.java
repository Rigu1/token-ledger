package io.tokenpilot.notification;

import io.tokenpilot.budget.BudgetKey;
import io.tokenpilot.budget.BudgetThreshold;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 메모리 기반 상태 저장소
 */
public class InMemoryNotificationStateStore implements NotificationStateStore {

  private final Map<BudgetKey, BudgetThreshold> store = new ConcurrentHashMap<>();

  @Override
  public BudgetThreshold getLastNotifiedThreshold(BudgetKey key) {
    return store.getOrDefault(key, BudgetThreshold.NONE);
  }

  @Override
  public void updateLastNotifiedThreshold(
      BudgetKey key,
      BudgetThreshold threshold
  ) {
    store.put(key, threshold);
  }
}
