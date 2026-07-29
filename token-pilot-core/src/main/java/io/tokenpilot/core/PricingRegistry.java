package io.tokenpilot.core;

import io.tokenpilot.core.domain.PricingPlan;
import io.tokenpilot.core.domain.PricingResolution;
import io.tokenpilot.core.domain.TokenType;

import java.util.Optional;

/**
 * AI 모델별 가격 정책을 관리하는 저장소 인터페이스.
 */
public interface PricingRegistry {
    /**
     * 모델 식별자로 등록된 가격 정책을 조회합니다.
     * @param modelId 모델 식별자
     * @return 가격 정책 (존재하지 않을 경우 empty)
     */
    Optional<PricingPlan> getPlan(String modelId);

    /**
     * 모델과 토큰 타입에 대한 가격 결정 결과를 조회합니다.
     * 등록되지 않은 모델은 {@link io.tokenpilot.core.domain.PricingResolutionStatus#MISSING_PLAN}으로 표현합니다.
     * @param modelId 모델 식별자
     * @param tokenType 토큰 타입
     * @return 가격 결정 결과
     */
    default PricingResolution resolveRate(String modelId, TokenType tokenType) {
        return getPlan(modelId)
                .map(plan -> plan.resolveRate(tokenType))
                .orElseGet(PricingResolution::missingPlan);
    }

    /**
     * 새로운 가격 정책을 등록하거나 업데이트합니다.
     * @param plan 가격 정책
     */
    void registerPlan(PricingPlan plan);
}
