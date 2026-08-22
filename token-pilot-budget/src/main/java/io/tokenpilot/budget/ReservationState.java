package io.tokenpilot.budget;

/**
 * 예산 예약의 현재 회계 상태입니다.
 */
public enum ReservationState {
    /** 안전 상한액이 예약되었지만 공급자 호출은 시작되지 않은 상태입니다. */
    RESERVED,

    /** 예약을 사용해 공급자 호출이 진행 중인 상태입니다. */
    IN_FLIGHT,

    /** 실제 사용량을 알 수 없어 후속 정산이 필요한 상태입니다. */
    RECONCILIATION_REQUIRED,

    /** 실제 사용 금액이 확정된 종료 상태입니다. */
    COMMITTED,

    /** 사용되지 않은 예약 금액이 해제된 종료 상태입니다. */
    RELEASED,

    /** 실제 사용량을 끝내 확정할 수 없어 정책에 따라 상각된 종료 상태입니다. */
    WRITTEN_OFF;

    /**
     * 더 이상 정상적인 회계 전이를 허용하지 않는 종료 상태인지 반환합니다.
     *
     * <p>{@link #RECONCILIATION_REQUIRED}는 후속 정산을 기다리는 상태이므로 종료 상태가 아닙니다.</p>
     */
    public boolean isClosed() {
        return this == COMMITTED || this == RELEASED || this == WRITTEN_OFF;
    }
}
