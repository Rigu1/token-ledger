package io.tokenpilot.core;

import io.tokenpilot.core.domain.TokenCountResult;

/**
 * LLM 호출 전에 입력의 토큰 수를 계산하는 framework-independent 계약입니다.
 * 계산 결과에는 토큰 수뿐 아니라 계산 범위와 estimator/tokenization 기준이 포함됩니다.
 */
public interface TokenEstimator {

    /**
     * 전달된 문자열의 토큰 수를 계산합니다.
     * 빈 문자열은 unavailable이 아닌 0-token counted 결과로 계산해야 합니다.
     *
     * @param text 계산할 문자열
     * @return 계산값과 계산 기준을 보존한 결과
     * @throws NullPointerException text가 null인 경우
     */
    TokenCountResult estimate(String text);
}
