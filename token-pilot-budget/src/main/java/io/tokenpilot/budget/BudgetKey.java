package io.tokenpilot.budget;

import java.util.Objects;

/**
 * 월별 예산 bucket을 식별하는 불변 key입니다.
 *
 * @param policyId  예산 정책 식별자
 * @param targetType 예산 대상 종류
 * @param targetId   예산 대상 식별자
 * @param window     월별 예산 기간
 */
public record BudgetKey(
    String policyId,
    String targetType,
    String targetId,
    BudgetWindow window
) {

  public BudgetKey {
    policyId = requireText(policyId, "policyId");
    targetType = requireText(targetType, "targetType");
    targetId = requireText(targetId, "targetId");
    Objects.requireNonNull(window, "window must not be null");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
