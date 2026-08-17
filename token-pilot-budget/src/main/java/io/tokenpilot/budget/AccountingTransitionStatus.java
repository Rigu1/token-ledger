package io.tokenpilot.budget;

/**
 * 예약 생성 이후 회계 상태 변경 명령의 결과입니다.
 *
 * <p>{@link #APPLIED}만 새로운 회계 변경이 적용되었음을 뜻합니다. 나머지 결과는 기존 상태와
 * 금액을 바꾸지 않았음을 뜻하므로 호출자는 예외 메시지나 저장소 내부 구현에 의존하지 않고
 * 재시도, 충돌 처리, 입력 보정을 결정할 수 있습니다.</p>
 */
public enum AccountingTransitionStatus {
    /** 요청한 회계 변경이 새로 적용되었습니다. */
    APPLIED,

    /** 같은 명령이 이미 적용되어 기존 결과가 재사용되었습니다. */
    REUSED,

    /** 같은 멱등성 식별자에 서로 다른 명령 내용이 사용되었습니다. */
    CONFLICT,

    /** 대상 예약을 찾을 수 없습니다. */
    NOT_FOUND,

    /** 명령 금액의 통화가 예약 통화와 다릅니다. */
    CURRENCY_MISMATCH,

    /** 명령 인자가 계약을 만족하지 않습니다. */
    INVALID_ARGUMENT,

    /** 현재 예약 상태에서는 요청한 전이가 허용되지 않습니다. */
    NOT_ALLOWED;

    /** 새로운 회계 변경이 적용되었는지 반환합니다. */
    public boolean isApplied() {
        return this == APPLIED;
    }
}
