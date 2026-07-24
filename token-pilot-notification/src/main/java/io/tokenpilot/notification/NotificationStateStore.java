package io.tokenpilot.notification;

import io.tokenpilot.budget.BudgetKey;
import io.tokenpilot.budget.BudgetThreshold;

/**
 * 알림 중복 방지를 위한 상태 저장소
 */
public interface NotificationStateStore {

  BudgetThreshold getLastNotifiedThreshold(BudgetKey key);

  void updateLastNotifiedThreshold(
      BudgetKey key,
      BudgetThreshold threshold
  );
}
