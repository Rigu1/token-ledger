package io.tokenpilot.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.tokenpilot.budget.AccountingTransitionStatus.APPLIED;
import static io.tokenpilot.budget.AccountingTransitionStatus.NOT_ALLOWED;
import static io.tokenpilot.budget.ReservationState.COMMITTED;
import static io.tokenpilot.budget.ReservationState.IN_FLIGHT;
import static io.tokenpilot.budget.ReservationState.RECONCILIATION_REQUIRED;
import static io.tokenpilot.budget.ReservationState.RELEASED;
import static io.tokenpilot.budget.ReservationState.RESERVED;
import static io.tokenpilot.budget.ReservationState.WRITTEN_OFF;
import static org.assertj.core.api.Assertions.assertThat;

class ReservationStateMachineTest {

    @Test
    @DisplayName("예약된 요청의 호출을 시작하면 진행 중 상태가 된다")
    void movesReservedRequestInFlightOnDispatch() {
        var transition = ReservationStateMachine.onDispatch(RESERVED);

        assertThat(transition.previousState()).isEqualTo(RESERVED);
        assertThat(transition.resultingState()).isEqualTo(IN_FLIGHT);
        assertThat(transition.status()).isEqualTo(APPLIED);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationState.class, names = "RESERVED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("예약 상태가 아닌 요청에는 호출 시작을 적용하지 않는다")
    void rejectsDispatchUnlessReserved(ReservationState currentState) {
        var transition = ReservationStateMachine.onDispatch(currentState);

        assertThat(transition.previousState()).isEqualTo(currentState);
        assertThat(transition.resultingState()).isEqualTo(currentState);
        assertThat(transition.status()).isEqualTo(NOT_ALLOWED);
    }

    @Test
    @DisplayName("호출 전에 예약을 취소하면 해제 상태가 된다")
    void releasesReservedRequestBeforeDispatch() {
        var transition = ReservationStateMachine.release(RESERVED);

        assertThat(transition.previousState()).isEqualTo(RESERVED);
        assertThat(transition.resultingState()).isEqualTo(RELEASED);
        assertThat(transition.status()).isEqualTo(APPLIED);
    }

    @Test
    @DisplayName("호출을 시작한 예약은 미과금 확인 없이 해제하지 않는다")
    void rejectsReleaseAfterDispatch() {
        var transition = ReservationStateMachine.release(IN_FLIGHT);

        assertThat(transition.previousState()).isEqualTo(IN_FLIGHT);
        assertThat(transition.resultingState()).isEqualTo(IN_FLIGHT);
        assertThat(transition.status()).isEqualTo(NOT_ALLOWED);
    }

    @Test
    @DisplayName("호출 후 actual을 알면 비용 확정 상태가 된다")
    void commitsInFlightReservationWhenActualIsKnown() {
        var transition = ReservationStateMachine.onActualKnown(IN_FLIGHT);

        assertThat(transition.previousState()).isEqualTo(IN_FLIGHT);
        assertThat(transition.resultingState()).isEqualTo(COMMITTED);
        assertThat(transition.status()).isEqualTo(APPLIED);
    }

    @Test
    @DisplayName("호출 후 actual을 알 수 없으면 정산 대기 상태가 된다")
    void requiresReconciliationWhenActualIsUnavailable() {
        var transition = ReservationStateMachine.onActualUnavailable(IN_FLIGHT);

        assertThat(transition.previousState()).isEqualTo(IN_FLIGHT);
        assertThat(transition.resultingState()).isEqualTo(RECONCILIATION_REQUIRED);
        assertThat(transition.status()).isEqualTo(APPLIED);
    }

    @Test
    @DisplayName("정산 대기 중 late actual이 도착하면 비용 확정 상태가 된다")
    void commitsReservationWhenLateActualArrives() {
        var transition = ReservationStateMachine.onLateActual(RECONCILIATION_REQUIRED);

        assertThat(transition.previousState()).isEqualTo(RECONCILIATION_REQUIRED);
        assertThat(transition.resultingState()).isEqualTo(COMMITTED);
        assertThat(transition.status()).isEqualTo(APPLIED);
    }

    @Test
    @DisplayName("정산 대기 중 write-off를 결정하면 상각 상태가 된다")
    void writesOffReservationAwaitingReconciliation() {
        var transition = ReservationStateMachine.writeOff(RECONCILIATION_REQUIRED);

        assertThat(transition.previousState()).isEqualTo(RECONCILIATION_REQUIRED);
        assertThat(transition.resultingState()).isEqualTo(WRITTEN_OFF);
        assertThat(transition.status()).isEqualTo(APPLIED);
    }

    @Test
    @DisplayName("상태 정보만으로 종료 명령 재사용을 판단하지 않는다")
    void doesNotInferTerminalReplayFromStateAlone() {
        var transition = ReservationStateMachine.onActualKnown(COMMITTED);

        assertThat(transition.previousState()).isEqualTo(COMMITTED);
        assertThat(transition.resultingState()).isEqualTo(COMMITTED);
        assertThat(transition.status()).isEqualTo(NOT_ALLOWED);
    }

    @Test
    @DisplayName("상태 정보만으로 종료 명령 충돌을 판단하지 않는다")
    void doesNotInferTerminalConflictFromStateAlone() {
        var transition = ReservationStateMachine.release(COMMITTED);

        assertThat(transition.previousState()).isEqualTo(COMMITTED);
        assertThat(transition.resultingState()).isEqualTo(COMMITTED);
        assertThat(transition.status()).isEqualTo(NOT_ALLOWED);
    }
}
