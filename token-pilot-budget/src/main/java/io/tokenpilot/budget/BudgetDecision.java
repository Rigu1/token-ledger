package io.tokenpilot.budget;

import io.tokenpilot.core.domain.Cost;

/**
 *  예산 평가 결과를 나타내는 객체
 *
 * - state: ALLOW / WARN / BLOCK / CURRENCY_MISMATCH 상태
 * - threshold: 현재 도달한 예산 임계치
 * - reason: 상태 설명
 * - currentUsage: 통화를 포함한 현재 사용량
 * - limit: 통화를 포함한 총 예산
 * - key: 평가 시점에 확정된 예산 bucket 식별자
 */
public record BudgetDecision(
    BudgetKey key,
    BudgetState state,
    BudgetThreshold threshold,
    String reason,
    Cost currentUsage,
    Cost limit
) {}
