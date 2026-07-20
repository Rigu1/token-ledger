package io.tokenpilot.core.domain;

import java.util.Map;
import java.util.Objects;

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
    /**
     * 토큰 총량과 세부량의 포함 관계를 검증하고 metadata를 불변 복사합니다.
     *
     * @throws IllegalArgumentException 토큰 수가 음수이거나 세부량이 총량을 초과한 경우
     * @throws NullPointerException details가 null이거나 metadata에 null key/value가 있는 경우
     */
    public TokenUsage {
        if (inputTokens < 0) {
            throw new IllegalArgumentException(
                    "inputTokens must be non-negative"
            );
        }

        if (outputTokens < 0) {
            throw new IllegalArgumentException(
                    "outputTokens must be non-negative"
            );
        }

        details = Objects.requireNonNull(
                details,
                "details must not be null"
        );

        if (details.cachedInputTokens() > inputTokens) {
            throw new IllegalArgumentException(
                    "cachedInputTokens must not exceed inputTokens"
            );
        }

        if (details.cachedOutputTokens() > outputTokens
                || details.reasoningOutputTokens()
                > outputTokens - details.cachedOutputTokens()) {
            throw new IllegalArgumentException(
                    "Output details must not exceed outputTokens"
            );
        }

        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * 기본 입력/출력 토큰을 사용하는 {@link TokenUsage}를 생성합니다.
     *
     * @param prompt 전체 입력 토큰
     * @param completion 전체 출력 토큰
     * @return 세부량과 metadata가 비어 있는 사용량
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
     *
     * @param prompt 전체 입력 토큰
     * @param completion 전체 출력 토큰
     * @param reasoning 전체 출력에 포함된 reasoning 토큰
     * @return reasoning 세부량을 포함하는 사용량
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
     *
     * @return 전체 입력 토큰
     */
    public long promptTokens() {
        return inputTokens;
    }

    /**
     * 모든 종류의 출력(추론 포함) 토큰 수의 합계를 반환합니다.
     *
     * @return 전체 출력 토큰
     */
    public long completionTokens() {
        return outputTokens;
    }

    /**
     * 전체 사용 토큰 수의 합계를 반환합니다.
     *
     * @return 전체 입력과 출력 토큰의 합
     * @throws ArithmeticException 합계가 {@code long} 범위를 초과한 경우
     */
    public long totalTokens() {
        return Math.addExact(inputTokens, outputTokens);
    }

    /**
     * 특정 토큰 타입의 전체 또는 세부 사용량을 반환합니다.
     *
     * @param type 조회할 토큰 타입
     * @return 해당 토큰 타입의 사용량
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
