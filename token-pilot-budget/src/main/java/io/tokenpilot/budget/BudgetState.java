package io.tokenpilot.budget;

/**
 * 예산 평가 결과 상태를 나타냅니다.
 * <p>
 * ALLOW : 호출 허용
 * WARN  : 예산 경고
 * BLOCK : 호출 차단
 * CURRENCY_MISMATCH : 예산과 비용 통화 불일치
 */
public enum BudgetState {
  ALLOW,
  WARN,
  BLOCK,
  CURRENCY_MISMATCH
}
