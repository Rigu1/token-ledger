package io.tokenledger.budget;

import io.tokenledger.core.domain.Cost;
import java.util.Currency;
import java.util.Map;


/**
 * 예산 판단을 위해 비용 누적 상태를 관리하는 저장소 인터페이스입니다.
 * <p>
 * 예산 식별자별(예: tenant)로 비용을 조회하고
 * 누적하는 책임을 가집니다.
 */

public interface BudgetStateStore {

  Cost getAccumulatedCost(Map<String, String> tags, Currency currency);

  void addCost(Map<String, String> tags, Cost cost);
}
