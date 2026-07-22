package io.tokenpilot.notification;

import io.tokenpilot.budget.BudgetKey;
import io.tokenpilot.budget.BudgetState;
import io.tokenpilot.budget.BudgetThreshold;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 예산 임계치 도달 시 발생하는 알림 이벤트
 */
public record BudgetNotificationEvent(
    BudgetKey key,
    BudgetThreshold threshold,
    BudgetState state,
    String reason,
    BigDecimal currentUsage,
    BigDecimal limit,
    Map<String, String> tags
) {}
