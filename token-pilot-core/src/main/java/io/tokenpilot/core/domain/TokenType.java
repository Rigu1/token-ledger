package io.tokenpilot.core.domain;

/**
 * AI 모델 호출 시 발생하는 토큰의 세부 유형.
 */
public enum TokenType {
    /** 일반 입력 (Prompt) */
    PROMPT,
    /** 일반 출력 (Completion) */
    COMPLETION,
    /** 추론 (Reasoning) - 주로 출력 계열 */
    REASONING,
    /** 캐시에서 읽은 입력 (Cache Read Prompt) */
    CACHE_READ_PROMPT,
    /** 캐시에 새로 저장한 입력 (Cache Creation Prompt) */
    CACHE_CREATION_PROMPT;

    /**
     * 해당 토큰 타입이 입력(Prompt) 계열인지 확인합니다.
     *
     * @return 입력 계열이면 {@code true}
     */
    public boolean isPrompt() {
        return this == PROMPT || this == CACHE_READ_PROMPT || this == CACHE_CREATION_PROMPT;
    }

    /**
     * 해당 토큰 타입이 출력(Completion) 계열인지 확인합니다.
     *
     * @return 출력 계열이면 {@code true}
     */
    public boolean isCompletion() {
        return this == COMPLETION || this == REASONING;
    }
}
