package io.tokenpilot.core.domain;

/**
 * 토큰 사용량 값의 출처와 생성 방식을 나타냅니다.
 */
public enum UsageSource {
    /** provider가 포괄 총량을 직접 보고한 사용량 */
    PROVIDER_REPORTED,
    /** provider가 보고한 여러 필드를 어댑터가 정규화해 만든 사용량 */
    PROVIDER_DERIVED,
    /** 로컬 tokenizer가 계산한 사용량 */
    LOCAL_TOKENIZER,
    /** 휴리스틱으로 근사 추정한 사용량 */
    HEURISTIC_ESTIMATE,
    /** 사용량 정보를 얻을 수 없음 */
    UNAVAILABLE
}
