package io.tokenpilot.core.domain;

/**
 * counted token 결과의 계산 정확도를 나타냅니다.
 */
public enum TokenCountAccuracy {
    /**
     * 계산값이 정확하며 tokens와 safeUpperBoundTokens가 동일한 상태입니다.
     */
    EXACT,

    /**
     * 계산값이 추정치이며 safeUpperBoundTokens가 tokens 이상인 상태입니다.
     */
    HEURISTIC
}
