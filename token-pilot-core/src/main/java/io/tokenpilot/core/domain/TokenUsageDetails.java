package io.tokenpilot.core.domain;

/**
 * 전체 입력/출력 토큰에 포함되는 세부 토큰 사용량.
 *
 * @param cachedInputTokens     전체 입력에 포함된 cached input 토큰
 * @param reasoningOutputTokens 전체 출력에 포함된 reasoning 토큰
 * @param cachedOutputTokens    전체 출력에 포함된 cached output 토큰
 */
public record TokenUsageDetails(
        long cachedInputTokens,
        long reasoningOutputTokens,
        long cachedOutputTokens
) {
    /**
     * 모든 세부 토큰 수가 0 이상인지 검증합니다.
     *
     * @throws IllegalArgumentException 세부 토큰 수가 음수인 경우
     */
    public TokenUsageDetails {
        if (cachedInputTokens < 0) {
            throw new IllegalArgumentException("cachedInputTokens must be non-negative");
        }
        if (reasoningOutputTokens < 0) {
            throw new IllegalArgumentException("reasoningOutputTokens must be non-negative");
        }
        if (cachedOutputTokens < 0) {
            throw new IllegalArgumentException("cachedOutputTokens must be non-negative");
        }
    }
}
