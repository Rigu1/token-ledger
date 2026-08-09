package io.tokenpilot.core.domain;

/**
 * token 계산에 포함된 입력 범위를 나타냅니다.
 * 기존 결과의 scope만 변경할 수 없으며, 더 넓은 범위를 계산했다면 새로운 결과를 생성해야 합니다.
 */
public enum TokenCountScope {
    /**
     * 전달된 문자열의 UTF-8 내용만 계산한 범위입니다.
     * role, tool schema, media metadata, structured-output schema와 provider framing은 포함하지 않으므로
     * 전체 요청이 context window에 들어간다는 근거로 사용할 수 없습니다.
     */
    TEXT_ONLY,

    /**
     * 실제 전송 요청의 tokenizable content와 필요한 framing/headroom을 모두 포함한 범위입니다.
     */
    REQUEST
}
