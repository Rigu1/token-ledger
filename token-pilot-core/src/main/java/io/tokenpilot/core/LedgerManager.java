package io.tokenpilot.core;

import io.tokenpilot.core.domain.Cost;
import io.tokenpilot.core.domain.PricingPlan;
import io.tokenpilot.core.domain.PricingSnapshot;
import io.tokenpilot.core.domain.TokenUsage;

import java.util.Map;

/**
 * AI 호출에 대한 지출을 기록하고 관리하는 통합 매니저 인터페이스.
 */
public interface LedgerManager {
    /**
     * 특정 모델의 호출 정보를 기록하고 최종 비용을 계산합니다.
     * @param modelId 모델 식별자
     * @param usage   토큰 사용량
     * @param tags    추가 메타데이터 (tenant_id, user_id 등)
     * @return 산출된 비용
     */
    Cost record(String modelId, TokenUsage usage, Map<String, String> tags);

    /**
     * 이미 resolve된 가격 정책으로 호출 정보를 기록하고 최종 비용을 계산합니다.
     * @param plan    provider 호출 전에 resolve된 가격 정책
     * @param usage   토큰 사용량
     * @param tags    추가 메타데이터 (tenant_id, user_id 등)
     * @return 산출된 비용
     */
    Cost record(PricingPlan plan, TokenUsage usage, Map<String, String> tags);

    /**
     * 요청 단위 pricing snapshot으로 호출 정보를 기록하고 최종 비용을 계산합니다.
     * @param snapshot provider 호출 전에 보존된 pricing snapshot
     * @param usage    토큰 사용량
     * @param tags     추가 메타데이터 (tenant_id, user_id 등)
     * @return 산출된 비용
     */
    Cost record(PricingSnapshot snapshot, TokenUsage usage, Map<String, String> tags);
}
