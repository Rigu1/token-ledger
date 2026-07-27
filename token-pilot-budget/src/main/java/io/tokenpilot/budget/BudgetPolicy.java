package io.tokenpilot.budget;

import io.tokenpilot.core.domain.Cost;

import java.time.ZoneId;
import java.util.Objects;

/**
 * 월별 예산 key 생성과 금액 검증에 사용하는 정책 snapshot입니다.
 * 대상 tag가 없고 {@code fallbackTargetId}도 설정되지 않으면 평가는 fail-closed 됩니다.
 *
 * @param id 정책 식별자
 * @param targetType 예산 대상 종류
 * @param targetTagKey 대상 식별자를 읽을 tag key
 * @param fallbackTargetId 누락된 대상에 사용할 명시적 fallback, 미설정 시 {@code null}
 * @param monthlyLimit 통화를 포함한 월별 한도
 * @param zoneId 월 경계를 계산할 시간대
 */
public record BudgetPolicy(
    String id,
    String targetType,
    String targetTagKey,
    String fallbackTargetId,
    Cost monthlyLimit,
    ZoneId zoneId
) {

  public BudgetPolicy {
    id = requireText(id, "id");
    targetType = requireText(targetType, "targetType");
    targetTagKey = requireText(targetTagKey, "targetTagKey");
    if (fallbackTargetId != null && fallbackTargetId.isBlank()) {
      throw new IllegalArgumentException("fallbackTargetId must not be blank");
    }
    Objects.requireNonNull(monthlyLimit, "monthlyLimit must not be null");
    if (monthlyLimit.value().signum() <= 0) {
      throw new IllegalArgumentException("monthlyLimit must be greater than zero");
    }
    Objects.requireNonNull(zoneId, "zoneId must not be null");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
