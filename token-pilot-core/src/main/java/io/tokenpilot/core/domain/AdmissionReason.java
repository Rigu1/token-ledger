package io.tokenpilot.core.domain;

/**
 * context admission 판정의 제한된 사유입니다.
 */
public enum AdmissionReason {
    /** 입력과 예약 출력량이 context window 안에 있습니다. */
    WITHIN_CONTEXT,
    /** 보수적 입력 상한 또는 예약 출력량이 context window를 넘습니다. */
    CONTEXT_EXCEEDED,
    /** TEXT_ONLY 등 전체 요청을 포함하지 않은 결과입니다. */
    INCOMPLETE_SCOPE,
    /** estimator 결과와 모델의 tokenizer 기준이 호환되지 않습니다. */
    INCOMPATIBLE_TOKENIZER,
    /** registry에 모델이 등록되어 있지 않습니다. */
    UNKNOWN_MODEL,
    /** token 계산 결과를 사용할 수 없습니다. */
    COUNT_UNAVAILABLE
}
