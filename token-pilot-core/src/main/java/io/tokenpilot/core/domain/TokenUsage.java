package io.tokenpilot.core.domain;

import java.util.Collections;
import java.util.Map;

/**
 * AI 모델 호출 시 발생하는 토큰 사용량 정보.
 * 전체 입력/출력 토큰과 세부 사용량을 관리합니다.
 *
 * @param inputTokens  전체 입력 토큰
 * @param outputTokens 전체 출력 토큰
 * @param details      세부 토큰 사용량
 * @param metadata     추가 메타데이터 (예: 모델 정보 등)
 */
public record TokenUsage(
        long inputTokens,
        long outputTokens,
        TokenUsageDetails details,
        Map<String, Object> metadata
) {
    public TokenUsage {
        metadata = (metadata != null) ? Collections.unmodifiableMap(metadata) : Map.of();
    }

    /**
     * 기본 입력/출력 토큰을 사용하는 {@link TokenUsage}를 생성합니다.
     */
    public static TokenUsage from(long prompt, long completion) {
        return new TokenUsage(
                prompt,
                completion,
                new TokenUsageDetails(0, 0, 0),
                Map.of()
        );
    }

    /**
     * 입력/출력/추론 토큰을 포함하는 {@link TokenUsage}를 생성합니다.
     */
    public static TokenUsage from(long prompt, long completion, long reasoning) {
        return new TokenUsage(
                prompt,
                completion,
                new TokenUsageDetails(0, reasoning, 0),
                Map.of()
        );
    }

    /**
     * 모든 종류의 입력/출력 토큰 수의 합계를 반환합니다.
     */
    public long promptTokens() {
        return inputTokens;
    }

    /**
     * 모든 종류의 출력(추론 포함) 토큰 수의 합계를 반환합니다.
     */
    public long completionTokens() {
        return outputTokens;
    }

    /**
     * 전체 사용 토큰 수의 합계를 반환합니다.
     */
    public long totalTokens() {
        return inputTokens + outputTokens;
    }

    /**
     * 특정 토큰 타입의 사용량을 가져옵니다. 없을 시 0을 반환합니다.
     */
    public long getCount(TokenType type) {
        return switch (type) {
            case PROMPT -> inputTokens;
            case COMPLETION -> outputTokens;
            case REASONING -> details.reasoningOutputTokens();
            case CACHED_PROMPT -> details.cachedInputTokens();
            case CACHED_COMPLETION -> details.cachedOutputTokens();
        };
    }
}
