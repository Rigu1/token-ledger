package io.tokenpilot.budget;

import io.tokenpilot.core.domain.Cost;

/**
 * 예약의 회계 상태와 금액을 변경하는 단일 진입점입니다.
 */
public interface ReservationAccounting {

    /** 예약을 사용한 provider 호출 시작을 기록합니다. */
    ReservationTransition markInFlight(ReservationId reservationId);

    /** provider 호출 전에 사용하지 않은 예약을 해제합니다. */
    ReservationTransition releaseBeforeDispatch(ReservationId reservationId);

    /** provider가 미과금을 확인한 진행 중 예약을 해제합니다. */
    ReservationTransition releaseConfirmedUnbilled(ReservationId reservationId);

    /** 전달받은 actual 비용을 예약에 확정합니다. */
    ReservationTransition commit(ReservationId reservationId, Cost actualCost);

    /** actual을 확보하지 못한 예약을 정산 대기로 전환합니다. */
    ReservationTransition markReconciliationRequired(ReservationId reservationId);

    /** 정산 대기 중 전달받은 late actual 비용을 확정합니다. */
    ReservationTransition reconcileLateActual(
            ReservationId reservationId,
            Cost actualCost
    );

    /** 후속 정산할 수 없는 pending 예약을 명시적으로 상각합니다. */
    ReservationTransition writeOff(ReservationId reservationId);
}
