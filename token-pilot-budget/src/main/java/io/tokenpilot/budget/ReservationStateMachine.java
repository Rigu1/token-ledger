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

    /** 공급자가 미과금을 확인한 진행 중 예약을 해제합니다. */
    public static ReservationTransition releaseConfirmedUnbilled(
            ReservationState currentState
    ) {
        return transitionFrom(
                currentState,
                ReservationState.IN_FLIGHT,
                ReservationState.RELEASED
        );
    }

    /** 전달받은 actual을 확정하기 위한 상태 전이를 판단합니다. */
    public static ReservationTransition commit(ReservationState currentState) {
        return transitionFrom(
                currentState,
                ReservationState.IN_FLIGHT,
                ReservationState.COMMITTED
        );
    }

    /** actual을 전달받지 못한 예약을 정산 대기로 전환할 수 있는지 판단합니다. */
    public static ReservationTransition markReconciliationRequired(ReservationState currentState) {
        return transitionFrom(
                currentState,
                ReservationState.IN_FLIGHT,
                ReservationState.RECONCILIATION_REQUIRED
        );
    }

    /** 정산 대기 중 전달받은 late actual을 확정할 수 있는지 판단합니다. */
    public static ReservationTransition reconcileLateActual(ReservationState currentState) {
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
