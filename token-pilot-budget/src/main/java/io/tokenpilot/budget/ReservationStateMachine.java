package io.tokenpilot.budget;

import java.util.Objects;

/**
 * 예약의 회계 상태 전이 가능 여부를 판단합니다.
 */
public final class ReservationStateMachine {

    private ReservationStateMachine() {
    }

    /** 예약된 요청의 공급자 호출을 시작합니다. */
    public static ReservationTransition onDispatch(ReservationState currentState) {
        return transitionFrom(
                currentState,
                ReservationState.RESERVED,
                ReservationState.IN_FLIGHT
        );
    }

    /** 공급자 호출 전에 예약을 해제합니다. */
    public static ReservationTransition release(ReservationState currentState) {
        return transitionFrom(
                currentState,
                ReservationState.RESERVED,
                ReservationState.RELEASED
        );
    }

    /** 공급자 호출 후 actual을 알 수 있으면 비용을 확정합니다. */
    public static ReservationTransition onActualKnown(ReservationState currentState) {
        return transitionFrom(
                currentState,
                ReservationState.IN_FLIGHT,
                ReservationState.COMMITTED
        );
    }

    /** 공급자 호출 후 actual을 알 수 없으면 정산 대기로 전환합니다. */
    public static ReservationTransition onActualUnavailable(ReservationState currentState) {
        return transitionFrom(
                currentState,
                ReservationState.IN_FLIGHT,
                ReservationState.RECONCILIATION_REQUIRED
        );
    }

    /** 정산 대기 중 late actual이 도착하면 비용을 확정합니다. */
    public static ReservationTransition onLateActual(ReservationState currentState) {
        return transitionFrom(
                currentState,
                ReservationState.RECONCILIATION_REQUIRED,
                ReservationState.COMMITTED
        );
    }

    /** 정산 대기 중인 예약을 명시적으로 상각합니다. */
    public static ReservationTransition writeOff(ReservationState currentState) {
        return transitionFrom(
                currentState,
                ReservationState.RECONCILIATION_REQUIRED,
                ReservationState.WRITTEN_OFF
        );
    }

    private static ReservationTransition transitionFrom(
            ReservationState currentState,
            ReservationState requiredState,
            ReservationState resultingState
    ) {
        Objects.requireNonNull(currentState, "currentState must not be null");

        if (currentState == requiredState) {
            return ReservationTransition.applied(currentState, resultingState);
        }

        return ReservationTransition.unchanged(
                currentState,
                AccountingTransitionStatus.NOT_ALLOWED
        );
    }
}
