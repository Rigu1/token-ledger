package io.tokenpilot.core;

import io.tokenpilot.core.domain.ModelDefinition;

import java.util.Optional;

/**
 * versioned model definition을 canonical id 또는 exact alias로 조회합니다.
 *
 * <p>조회 결과의 {@link ModelDefinition#canonicalModelId()}와
 * {@link ModelDefinition#pricingPolicyId()}를 함께 사용해
 * {@link PricingRegistry}에서 가격 정책을 조회해야 합니다. alias 문자열을
 * 가격 조회에 직접 전달하면 context와 pricing이 서로 다른 모델을 가리킬 수
 * 있습니다.</p>
 */
public interface ModelRegistry {

    /**
     * canonical model id 또는 exact alias를 canonical definition으로 해석합니다.
     *
     * @param modelIdOrAlias canonical id 또는 alias
     * @return 등록된 모델 definition, 미등록이면 empty
     */
    Optional<ModelDefinition> find(String modelIdOrAlias);
}
