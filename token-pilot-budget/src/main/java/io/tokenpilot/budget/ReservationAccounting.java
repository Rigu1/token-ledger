package io.tokenpilot.budget;

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

    /** provider actual usage를 예약 시점 가격으로 계산하여 확정합니다. */
    ReservationReconciliation commit(ActualUsageCommand command);

    /** actual을 확보하지 못한 예약을 정산 대기로 전환합니다. */
    ReservationTransition markReconciliationRequired(ReservationId reservationId);

    /** 늦게 도착한 provider actual usage를 예약 시점 가격으로 계산하여 확정합니다. */
    ReservationReconciliation reconcileLateActual(ActualUsageCommand command);

    /** 후속 정산할 수 없는 pending 예약을 명시적으로 상각합니다. */
    ReservationTransition writeOff(ReservationId reservationId);
}
