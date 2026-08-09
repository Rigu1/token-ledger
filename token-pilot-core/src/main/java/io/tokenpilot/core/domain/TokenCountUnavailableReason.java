package io.tokenpilot.core.domain;

/**
 * token estimator가 계산 결과를 제공하지 못한 이유를 제한된 값으로 나타냅니다.
 */
public enum TokenCountUnavailableReason {
    /**
     * 설정된 estimator가 token 계산 결과를 제공할 수 없는 상태입니다.
     */
    ESTIMATOR_UNAVAILABLE
}
