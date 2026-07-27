package io.tokenpilot.core.domain;

/**
 * 정규화된 전체 입력/출력 토큰에 포함되는 선택적 세부 사용량.
 * {@code null}은 provider가 값을 보고하지 않았음을, {@code 0}은 값을
 * 보고했으나 실제 사용량이 없었음을 뜻합니다.
 *
 * @param cacheReadInputTokens     전체 입력에 포함된 cache read 토큰 또는 미보고 시 {@code null}
 * @param cacheCreationInputTokens 전체 입력에 포함된 cache creation 토큰 또는 미보고 시 {@code null}
 * @param reasoningOutputTokens    전체 출력에 포함된 reasoning 토큰 또는 미보고 시 {@code null}
 */
public record TokenUsageDetails(
        Long cacheReadInputTokens,
        Long cacheCreationInputTokens,
        Long reasoningOutputTokens
) {
    /**
     * 모든 세부 토큰 수가 0 이상인지 검증합니다.
     *
     * @throws IllegalArgumentException 세부 토큰 수가 음수인 경우
     */
    public TokenUsageDetails {
        isEmptyToken(cacheReadInputTokens);
        isEmptyToken(cacheCreationInputTokens);
        isEmptyToken(reasoningOutputTokens);
    }

    /**
     * provider가 세부 사용량을 보고하지 않은 상태를 생성합니다.
     *
     * @return 모든 세부량이 미보고 상태인 객체
     */
    public static TokenUsageDetails unreported() {
        return new TokenUsageDetails(null, null, null);
    }

    private void isEmptyToken(Long token){
        if(token != null && token < 0){
            throw new IllegalArgumentException("tokens must be non-negative");
        }
    }
}
