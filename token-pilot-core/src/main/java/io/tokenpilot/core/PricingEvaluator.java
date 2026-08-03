package io.tokenpilot.core;

import io.tokenpilot.core.domain.PricingReconciliationResult;
import io.tokenpilot.core.domain.PricingResolution;
import io.tokenpilot.core.domain.PricingSnapshot;

import java.util.Optional;

/**
 * Pricing snapshot의 사용 가능 여부와 actual model 정합성을 판단하는 정책 계약.
 */
public interface PricingEvaluator {

    /**
     * Snapshot에 요청 처리에 필요한 rate가 있는지 검증합니다.
     *
     * @param snapshot 검증할 pricing snapshot, 조회되지 않은 경우 empty
     * @return snapshot 및 필수 rate의 resolution
     */
    PricingResolution validateSnapshotRates(Optional<PricingSnapshot> snapshot);

    /**
     * 호출 전 snapshot을 actual 응답 모델에 적용할 수 있는지 판단합니다.
     *
     * @param snapshot 호출 전에 확정한 pricing snapshot, 확정되지 않은 경우 empty
     * @param actualModelId provider가 반환한 actual model id
     * @return pricing reconciliation 판단 결과
     */
    PricingReconciliationResult determineReconciliation(
            Optional<PricingSnapshot> snapshot,
            String actualModelId
    );
}
