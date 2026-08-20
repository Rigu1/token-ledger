package io.tokenpilot.budget;

/**
 * 예약의 회계 상태와 금액을 변경하는 단일 진입점입니다.
 *
 * <table>
 *   <caption>예약 회계 명령의 허용 전이와 금액 이동</caption>
 *   <thead>
 *     <tr>
 *       <th>명령</th>
 *       <th>허용 상태</th>
 *       <th>결과 상태</th>
 *       <th>금액 이동</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>{@link #markInFlight(ReservationId)}</td>
 *       <td>{@link ReservationState#RESERVED}</td>
 *       <td>{@link ReservationState#IN_FLIGHT}</td>
 *       <td>없음</td>
 *     </tr>
 *     <tr>
 *       <td>{@code release(CANCELLED_BEFORE_DISPATCH)}</td>
 *       <td>{@link ReservationState#RESERVED}</td>
 *       <td>{@link ReservationState#RELEASED}</td>
 *       <td>{@code activeReservedCost -= estimate}</td>
 *     </tr>
 *     <tr>
 *       <td>{@code release(PROVIDER_CONFIRMED_UNBILLED)}</td>
 *       <td>{@link ReservationState#IN_FLIGHT}</td>
 *       <td>{@link ReservationState#RELEASED}</td>
 *       <td>{@code activeReservedCost -= estimate}</td>
 *     </tr>
 *     <tr>
 *       <td>{@link #commit(ActualUsageCommand)}</td>
 *       <td>{@link ReservationState#IN_FLIGHT}</td>
 *       <td>{@link ReservationState#COMMITTED}</td>
 *       <td>{@code activeReservedCost -= estimate; committedCost += actual}</td>
 *     </tr>
 *     <tr>
 *       <td>{@link #markReconciliationRequired(ReservationId, ReservationAccountingReason)}</td>
 *       <td>{@link ReservationState#IN_FLIGHT}</td>
 *       <td>{@link ReservationState#RECONCILIATION_REQUIRED}</td>
 *       <td>{@code activeReservedCost -= estimate; pendingReconciliationLiability += estimate}</td>
 *     </tr>
 *     <tr>
 *       <td>{@link #reconcileLateActual(ActualUsageCommand)}</td>
 *       <td>{@link ReservationState#RECONCILIATION_REQUIRED}</td>
 *       <td>{@link ReservationState#COMMITTED}</td>
 *       <td>{@code pendingReconciliationLiability -= estimate; committedCost += actual}</td>
 *     </tr>
 *     <tr>
 *       <td>{@link #writeOff(ReservationId, ReservationAccountingReason)}</td>
 *       <td>{@link ReservationState#RECONCILIATION_REQUIRED}</td>
 *       <td>{@link ReservationState#WRITTEN_OFF}</td>
 *       <td>{@code pendingReconciliationLiability -= estimate}</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <p>표의 전이가 새로 적용되면 {@link AccountingTransitionStatus#APPLIED}입니다.
 * 동일한 종료 명령과 값 또는 정산 대기 명령의 재호출은 상태와 금액을 유지하고
 * {@link AccountingTransitionStatus#REUSED}, 다른 actual 또는 상충하는 종료 명령은
 * {@link AccountingTransitionStatus#CONFLICT}입니다. 아직 종료 명령이 적용되지 않았지만
 * 현재 상태가 표의 허용 상태가 아니면 {@link AccountingTransitionStatus#NOT_ALLOWED}입니다.</p>
 *
 * <p>존재하지 않는 예약과 명령에 허용되지 않은 reason은 상태를 변경하기 전에
 * {@link IllegalArgumentException}으로 거부합니다. 모든 상태와 금액 변경은 같은 budget
 * bucket의 임계 구역 안에서 함께 적용됩니다.</p>
 */
public interface ReservationAccounting {

    /** 예약을 사용한 provider 호출 시작을 기록합니다. */
    ReservationTransition markInFlight(ReservationId reservationId);

    /** provider 호출 전에 사용하지 않은 예약을 해제합니다. */
    default ReservationTransition releaseBeforeDispatch(ReservationId reservationId) {
        return release(
                reservationId,
                ReservationAccountingReason.CANCELLED_BEFORE_DISPATCH
        );
    }

    /** provider가 미과금을 확인한 진행 중 예약을 해제합니다. */
    default ReservationTransition releaseConfirmedUnbilled(ReservationId reservationId) {
        return release(
                reservationId,
                ReservationAccountingReason.PROVIDER_CONFIRMED_UNBILLED
        );
    }

    ReservationTransition release(
            ReservationId reservationId,
            ReservationAccountingReason reason
    );

    /** provider actual usage를 예약 시점 가격으로 계산하여 확정합니다. */
    ReservationReconciliation commit(ActualUsageCommand command);

    /** actual을 확보하지 못한 예약을 정산 대기로 전환합니다. */
    default ReservationTransition markReconciliationRequired(
            ReservationId reservationId
    ) {
        return markReconciliationRequired(
                reservationId,
                ReservationAccountingReason.ACTUAL_USAGE_UNAVAILABLE
        );
    }

    ReservationTransition markReconciliationRequired(
            ReservationId reservationId,
            ReservationAccountingReason reason
    );

    /** 늦게 도착한 provider actual usage를 예약 시점 가격으로 계산하여 확정합니다. */
    ReservationReconciliation reconcileLateActual(ActualUsageCommand command);

    /** 후속 정산할 수 없는 pending 예약을 명시적으로 상각합니다. */
    default ReservationTransition writeOff(ReservationId reservationId) {
        return writeOff(
                reservationId,
                ReservationAccountingReason.MANUAL_WRITE_OFF
        );
    }

    ReservationTransition writeOff(
            ReservationId reservationId,
            ReservationAccountingReason reason
    );
}
