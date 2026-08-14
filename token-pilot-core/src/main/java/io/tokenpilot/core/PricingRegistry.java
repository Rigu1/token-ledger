package io.tokenpilot.core;

import io.tokenpilot.core.domain.PricingPlan;
import io.tokenpilot.core.domain.PricingResolution;
import io.tokenpilot.core.domain.PricingSnapshot;
import io.tokenpilot.core.domain.ModelDefinition;
import io.tokenpilot.core.domain.TokenType;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
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
     * 모델 식별자와 pricing policy id로 등록된 가격 정책을 조회합니다.
     * @param modelId 모델 식별자
     * @param pricingPolicyId pricing policy 식별자
     * @return 가격 정책 (존재하지 않을 경우 empty)
     */
    Optional<PricingPlan> getPlan(String modelId, String pricingPolicyId);

    /**
     * 모델 식별자와 pricing policy id로 요청 단위 pricing snapshot을 resolve합니다.
     * @param modelId 모델 식별자
     * @param pricingPolicyId pricing policy 식별자
     * @return pricing snapshot (존재하지 않을 경우 empty)
     */
    Optional<PricingSnapshot> resolveSnapshot(String modelId, String pricingPolicyId);

    /**
     * canonical model definition과 연결된 catalog version으로 pricing snapshot을 resolve합니다.
     * alias는 이 메서드에서 canonical id로 변환된 뒤에만 가격 조회에 사용됩니다.
     *
     * @param modelDefinition canonical model metadata
     * @return model definition과 연결된 pricing snapshot
     */
    default Optional<PricingSnapshot> resolveSnapshot(ModelDefinition modelDefinition) {
        Objects.requireNonNull(modelDefinition, "modelDefinition must not be null");
        return getPlan(modelDefinition.canonicalModelId(), modelDefinition.pricingPolicyId())
                .filter(plan -> plan.modelId().equals(modelDefinition.canonicalModelId()))
                .filter(plan -> plan.pricingPolicyId().equals(modelDefinition.pricingPolicyId()))
                .filter(plan -> plan.currency().equals(modelDefinition.pricingCurrency()))
                .map(plan -> PricingSnapshot.from(
                        plan,
                        modelDefinition.catalogVersion(),
                        Instant.now()
                ));
    }

    /**
     * canonical model registry를 거쳐 alias 가격 조회를 canonical 정책으로 고정합니다.
     *
     * @param modelRegistry canonical model lookup
     * @param modelIdOrAlias canonical id 또는 exact alias
     * @return canonical model definition과 일치하는 pricing snapshot
     */
    default Optional<PricingSnapshot> resolveSnapshot(
            ModelRegistry modelRegistry,
            String modelIdOrAlias
    ) {
        Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
        Objects.requireNonNull(modelIdOrAlias, "modelIdOrAlias must not be null");
        return modelRegistry.find(modelIdOrAlias).flatMap(this::resolveSnapshot);
    }

    /**
     * 모델과 토큰 타입에 대한 가격 결정 결과를 조회합니다.
     * @param modelId 모델 식별자
     * @param tokenType 토큰 타입
     * @return 가격 결정 결과
     */
    PricingResolution resolveRate(String modelId, TokenType tokenType);

    /**
     * 모델과 토큰 타입에 대한 가격 결정 결과를 기대 통화 기준으로 조회합니다.
     * @param modelId 모델 식별자
     * @param tokenType 토큰 타입
     * @param expectedCurrency 기대 통화
     * @return 가격 결정 결과
     */
    PricingResolution resolveRate(String modelId, TokenType tokenType, Currency expectedCurrency);

    /**
     * 새로운 가격 정책을 등록하거나 업데이트합니다.
     * @param plan 가격 정책
     */
    void registerPlan(PricingPlan plan);
}
