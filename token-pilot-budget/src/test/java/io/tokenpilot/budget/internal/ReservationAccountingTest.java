package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.BudgetKey;
import io.tokenpilot.budget.BudgetSnapshot;
import io.tokenpilot.budget.BudgetWindow;
import io.tokenpilot.budget.ReservationAccounting;
import io.tokenpilot.budget.ReservationId;
import io.tokenpilot.budget.ReservationTransition;
import io.tokenpilot.core.domain.Cost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.concurrent.atomic.AtomicInteger;

import static io.tokenpilot.budget.AccountingTransitionStatus.CONFLICT;
import static io.tokenpilot.budget.AccountingTransitionStatus.CURRENCY_MISMATCH;
import static io.tokenpilot.budget.AccountingTransitionStatus.NOT_ALLOWED;
import static io.tokenpilot.budget.AccountingTransitionStatus.REUSED;
import static io.tokenpilot.budget.ReservationState.COMMITTED;
import static io.tokenpilot.budget.ReservationState.IN_FLIGHT;
import static io.tokenpilot.budget.ReservationState.RECONCILIATION_REQUIRED;
import static io.tokenpilot.budget.ReservationState.RELEASED;
import static io.tokenpilot.budget.ReservationState.RESERVED;
import static io.tokenpilot.budget.ReservationState.WRITTEN_OFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationAccountingTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Cost LIMIT = usd("100.00");
    private static final BudgetKey KEY = new BudgetKey(
            "budget-policy",
            "tenant",
            "tenant-a",
            BudgetWindow.parse("2026-08")
    );
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-15T12:34:56Z"),
            ZoneOffset.UTC
    );

    @Test
    @DisplayName("없는 예약을 commit하면 bucket 합계를 변경하지 않는다")
    void keepsBucketUnchangedWhenReservationDoesNotExist() {
        InMemoryBudgetStateStore store = store();
        store.addCost(KEY, LIMIT, usd("10.00"));

        assertThatThrownBy(
                () -> store.commit(new ReservationId("missing"), usd("40.00"))
        ).isInstanceOf(IllegalArgumentException.class);

        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.committedCost()).isEqualTo(usd("10.00"));
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("변경 없는 결과는 bucket snapshot을 그대로 유지한다")
    void preservesBucketSnapshotForEveryNoOpStatus() {
        InMemoryBudgetStateStore reusedStore = store();
        ReservationId reusedReservation = reserveInFlight(
                reusedStore,
                reusedStore,
                usd("60.00")
        );
        reusedStore.commit(reusedReservation, usd("40.00"));
        BudgetSnapshot beforeReused = reusedStore.snapshot(KEY, LIMIT);

        ReservationTransition reused = reusedStore.commit(
                reusedReservation,
                usd("40.00")
        );

        assertThat(reused.status()).isEqualTo(REUSED);
        assertThat(reusedStore.snapshot(KEY, LIMIT)).isEqualTo(beforeReused);

        InMemoryBudgetStateStore conflictStore = store();
        ReservationId conflictReservation = reserveInFlight(
                conflictStore,
                conflictStore,
                usd("60.00")
        );
        conflictStore.commit(conflictReservation, usd("40.00"));
        BudgetSnapshot beforeConflict = conflictStore.snapshot(KEY, LIMIT);

        ReservationTransition conflict = conflictStore.commit(
                conflictReservation,
                usd("50.00")
        );

        assertThat(conflict.status()).isEqualTo(CONFLICT);
        assertThat(conflictStore.snapshot(KEY, LIMIT)).isEqualTo(beforeConflict);

        InMemoryBudgetStateStore notAllowedStore = store();
        ReservationId notAllowedReservation = reserveInFlight(
                notAllowedStore,
                notAllowedStore,
                usd("60.00")
        );
        BudgetSnapshot beforeNotAllowed = notAllowedStore.snapshot(KEY, LIMIT);

        ReservationTransition notAllowed = notAllowedStore.releaseBeforeDispatch(
                notAllowedReservation
        );

        assertThat(notAllowed.status()).isEqualTo(NOT_ALLOWED);
        assertThat(notAllowedStore.snapshot(KEY, LIMIT)).isEqualTo(beforeNotAllowed);

        InMemoryBudgetStateStore mismatchStore = store();
        ReservationId mismatchReservation = reserveInFlight(
                mismatchStore,
                mismatchStore,
                usd("60.00")
        );
        BudgetSnapshot beforeMismatch = mismatchStore.snapshot(KEY, LIMIT);

        ReservationTransition mismatch = mismatchStore.commit(
                mismatchReservation,
                Cost.of(new BigDecimal("40.00"), Currency.getInstance("KRW"))
        );

        assertThat(mismatch.status()).isEqualTo(CURRENCY_MISMATCH);
        assertThat(mismatchStore.snapshot(KEY, LIMIT)).isEqualTo(beforeMismatch);
    }

    @Test
    @DisplayName("estimate보다 작은 actual을 commit하면 남은 예약액을 해제한다")
    void commitsActualBelowEstimate() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));

        ReservationTransition transition = store.commit(reservationId, usd("40.00"));

        assertThat(transition)
                .isEqualTo(ReservationTransition.applied(IN_FLIGHT, COMMITTED));
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("40.00"));
    }

    @Test
    @DisplayName("estimate와 같은 actual을 commit하면 예약액을 확정 비용으로 옮긴다")
    void commitsActualEqualToEstimate() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));

        ReservationTransition transition = store.commit(reservationId, usd("60.00"));

        assertThat(transition)
                .isEqualTo(ReservationTransition.applied(IN_FLIGHT, COMMITTED));
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("60.00"));
        assertThat(snapshot.activeReservationIds()).isEmpty();
    }

    @Test
    @DisplayName("estimate보다 큰 actual을 commit하면 초과 비용까지 모두 반영한다")
    void commitsActualAboveEstimate() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));

        ReservationTransition transition = store.commit(reservationId, usd("80.00"));

        assertThat(transition)
                .isEqualTo(ReservationTransition.applied(IN_FLIGHT, COMMITTED));
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("80.00"));
    }

    @Test
    @DisplayName("actual 통화가 다르면 commit하지 않고 통화 불일치를 반환한다")
    void rejectsCommitWhenActualCurrencyDiffers() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));

        ReservationTransition transition = store.commit(
                reservationId,
                Cost.of(new BigDecimal("40.00"), Currency.getInstance("KRW"))
        );

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(IN_FLIGHT, CURRENCY_MISMATCH)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("60.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("같은 actual로 commit을 반복하면 기존 정산을 재사용한다")
    void reusesCommitWhenActualIsUnchanged() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.commit(reservationId, usd("40.00"));

        ReservationTransition transition = store.commit(reservationId, usd("40.00"));

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(COMMITTED, REUSED)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("40.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("다른 actual로 commit을 반복하면 기존 정산을 보존하고 충돌을 반환한다")
    void rejectsCommitWhenActualHasChanged() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.commit(reservationId, usd("40.00"));

        ReservationTransition transition = store.commit(reservationId, usd("50.00"));

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(COMMITTED, CONFLICT)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("40.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("commit 이후 release를 요청하면 기존 정산을 보존하고 충돌을 반환한다")
    void rejectsReleaseAfterCommit() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.commit(reservationId, usd("40.00"));

        ReservationTransition transition = store.releaseConfirmedUnbilled(reservationId);

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(COMMITTED, CONFLICT)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("40.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("commit 이후 호출 전 release를 요청하면 기존 정산을 보존하고 충돌을 반환한다")
    void rejectsPreDispatchReleaseAfterCommit() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.commit(reservationId, usd("40.00"));

        ReservationTransition transition = store.releaseBeforeDispatch(reservationId);

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(COMMITTED, CONFLICT)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("40.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("확인된 미과금 release 이후 commit하면 기존 해제를 보존하고 충돌을 반환한다")
    void rejectsCommitAfterConfirmedUnbilledRelease() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.releaseConfirmedUnbilled(reservationId);

        ReservationTransition transition = store.commit(reservationId, usd("40.00"));

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(RELEASED, CONFLICT)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("actual을 확보하지 못해 정산 대기를 요청하면 estimate를 pending으로 옮긴다")
    void movesEstimateToPendingWhenActualIsUnavailable() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));

        ReservationTransition transition = store.markReconciliationRequired(reservationId);

        assertThat(transition).isEqualTo(
                ReservationTransition.applied(IN_FLIGHT, RECONCILIATION_REQUIRED)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("60.00"));
    }

    @Test
    @DisplayName("actual이 0이면 비용을 확정하고 actual을 모르면 estimate를 pending으로 유지한다")
    void distinguishesZeroActualFromUnavailableActual() {
        InMemoryBudgetStateStore zeroActualStore = store();
        ReservationId zeroActualReservation = reserveInFlight(
                zeroActualStore,
                zeroActualStore,
                usd("60.00")
        );
        InMemoryBudgetStateStore unavailableActualStore = store();
        ReservationId unavailableActualReservation = reserveInFlight(
                unavailableActualStore,
                unavailableActualStore,
                usd("60.00")
        );

        ReservationTransition zeroActualTransition = zeroActualStore.commit(
                zeroActualReservation,
                usd("0.00")
        );
        ReservationTransition unavailableActualTransition =
                unavailableActualStore.markReconciliationRequired(
                        unavailableActualReservation
                );

        assertThat(zeroActualTransition).isEqualTo(
                ReservationTransition.applied(IN_FLIGHT, COMMITTED)
        );
        BudgetSnapshot zeroActualSnapshot = zeroActualStore.snapshot(KEY, LIMIT);
        assertThat(zeroActualSnapshot.committedCost()).isEqualTo(usd("0.00"));
        assertThat(zeroActualSnapshot.pendingReconciliationLiability())
                .isEqualTo(usd("0.00"));

        assertThat(unavailableActualTransition).isEqualTo(
                ReservationTransition.applied(IN_FLIGHT, RECONCILIATION_REQUIRED)
        );
        BudgetSnapshot unavailableActualSnapshot = unavailableActualStore.snapshot(KEY, LIMIT);
        assertThat(unavailableActualSnapshot.committedCost()).isEqualTo(usd("0.00"));
        assertThat(unavailableActualSnapshot.pendingReconciliationLiability())
                .isEqualTo(usd("60.00"));
    }

    @Test
    @DisplayName("late actual을 전달해 reconcile하면 pending을 제거하고 비용을 확정한다")
    void reconcilesPendingReservationWithLateActual() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.markReconciliationRequired(reservationId);

        ReservationTransition transition = store.reconcileLateActual(
                reservationId,
                usd("40.00")
        );

        assertThat(transition).isEqualTo(
                ReservationTransition.applied(RECONCILIATION_REQUIRED, COMMITTED)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("40.00"));
    }

    @Test
    @DisplayName("late actual 통화가 다르면 pending을 유지하고 통화 불일치를 반환한다")
    void rejectsLateActualWhenCurrencyDiffers() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.markReconciliationRequired(reservationId);

        ReservationTransition transition = store.reconcileLateActual(
                reservationId,
                Cost.of(new BigDecimal("40.00"), Currency.getInstance("KRW"))
        );

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(
                        RECONCILIATION_REQUIRED,
                        CURRENCY_MISMATCH
                )
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("60.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("같은 late actual로 reconcile을 반복하면 기존 정산을 재사용한다")
    void reusesLateActualWhenActualIsUnchanged() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.markReconciliationRequired(reservationId);
        store.reconcileLateActual(reservationId, usd("40.00"));

        ReservationTransition transition = store.reconcileLateActual(
                reservationId,
                usd("40.00")
        );

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(COMMITTED, REUSED)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("40.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("다른 late actual로 reconcile을 반복하면 기존 정산을 보존하고 충돌을 반환한다")
    void rejectsLateActualWhenActualHasChanged() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.markReconciliationRequired(reservationId);
        store.reconcileLateActual(reservationId, usd("40.00"));

        ReservationTransition transition = store.reconcileLateActual(
                reservationId,
                usd("50.00")
        );

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(COMMITTED, CONFLICT)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("40.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("late actual 정산 이후 일반 commit을 요청하면 기존 정산을 보존하고 충돌을 반환한다")
    void rejectsDirectCommitAfterLateActualCommit() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.markReconciliationRequired(reservationId);
        store.reconcileLateActual(reservationId, usd("40.00"));

        ReservationTransition transition = store.commit(reservationId, usd("40.00"));

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(COMMITTED, CONFLICT)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("40.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("일반 commit 이후 late actual 정산을 요청하면 기존 정산을 보존하고 충돌을 반환한다")
    void rejectsLateActualAfterDirectCommit() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.commit(reservationId, usd("40.00"));

        ReservationTransition transition = store.reconcileLateActual(
                reservationId,
                usd("40.00")
        );

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(COMMITTED, CONFLICT)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("40.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("provider 호출 전에 release하면 예약액을 해제한다")
    void releasesReservationBeforeDispatch() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserve(store, usd("60.00"));

        ReservationTransition transition = store.releaseBeforeDispatch(reservationId);

        assertThat(transition).isEqualTo(
                ReservationTransition.applied(RESERVED, RELEASED)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("호출 전 release를 반복하면 기존 해제를 재사용한다")
    void reusesReleaseBeforeDispatch() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserve(store, usd("60.00"));
        store.releaseBeforeDispatch(reservationId);

        ReservationTransition transition = store.releaseBeforeDispatch(reservationId);

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(RELEASED, REUSED)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("호출 전 release 이후 확인된 미과금 release를 요청하면 충돌을 반환한다")
    void rejectsConfirmedUnbilledReleaseAfterPreDispatchRelease() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserve(store, usd("60.00"));
        store.releaseBeforeDispatch(reservationId);

        ReservationTransition transition = store.releaseConfirmedUnbilled(reservationId);

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(RELEASED, CONFLICT)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("provider 호출을 시작한 예약에는 호출 전 release를 적용하지 않는다")
    void keepsInFlightReservationWhenPreDispatchReleaseIsRequested() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));

        ReservationTransition transition = store.releaseBeforeDispatch(reservationId);

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(
                        IN_FLIGHT,
                        NOT_ALLOWED
                )
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("60.00"));
    }

    @Test
    @DisplayName("provider가 미과금을 확인하면 진행 중인 예약액을 해제한다")
    void releasesInFlightReservationWhenProviderConfirmsNoCharge() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));

        ReservationTransition transition = store.releaseConfirmedUnbilled(reservationId);

        assertThat(transition).isEqualTo(
                ReservationTransition.applied(IN_FLIGHT, RELEASED)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("확인된 미과금 release를 반복하면 기존 해제를 재사용한다")
    void reusesConfirmedUnbilledRelease() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.releaseConfirmedUnbilled(reservationId);

        ReservationTransition transition = store.releaseConfirmedUnbilled(reservationId);

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(RELEASED, REUSED)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("정산 대기 예약을 write-off하면 pending만 제거한다")
    void writesOffPendingReservation() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.markReconciliationRequired(reservationId);

        ReservationTransition transition = store.writeOff(reservationId);

        assertThat(transition).isEqualTo(
                ReservationTransition.applied(RECONCILIATION_REQUIRED, WRITTEN_OFF)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("write-off를 반복하면 기존 상각을 재사용한다")
    void reusesWriteOff() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.markReconciliationRequired(reservationId);
        store.writeOff(reservationId);

        ReservationTransition transition = store.writeOff(reservationId);

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(WRITTEN_OFF, REUSED)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("write-off 이후 late actual을 요청하면 기존 상각을 보존하고 충돌을 반환한다")
    void rejectsLateActualAfterWriteOff() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.markReconciliationRequired(reservationId);
        store.writeOff(reservationId);

        ReservationTransition transition = store.reconcileLateActual(
                reservationId,
                usd("40.00")
        );

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(WRITTEN_OFF, CONFLICT)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    @Test
    @DisplayName("late actual 정산 이후 write-off를 요청하면 기존 정산을 보존하고 충돌을 반환한다")
    void rejectsWriteOffAfterLateActual() {
        InMemoryBudgetStateStore store = store();
        ReservationId reservationId = reserveInFlight(store, store, usd("60.00"));
        store.markReconciliationRequired(reservationId);
        store.reconcileLateActual(reservationId, usd("40.00"));

        ReservationTransition transition = store.writeOff(reservationId);

        assertThat(transition).isEqualTo(
                ReservationTransition.unchanged(COMMITTED, CONFLICT)
        );
        BudgetSnapshot snapshot = store.snapshot(KEY, LIMIT);
        assertThat(snapshot.activeReservedCost()).isEqualTo(usd("0.00"));
        assertThat(snapshot.committedCost()).isEqualTo(usd("40.00"));
        assertThat(snapshot.pendingReconciliationLiability()).isEqualTo(usd("0.00"));
    }

    private static ReservationId reserveInFlight(
            InMemoryBudgetStateStore store,
            ReservationAccounting accounting,
            Cost estimate
    ) {
        ReservationId reservationId = reserve(store, estimate);
        accounting.markInFlight(reservationId);
        return reservationId;
    }

    private static ReservationId reserve(
            InMemoryBudgetStateStore store,
            Cost estimate
    ) {
        return store.checkAndReserve(
                KEY,
                LIMIT,
                estimate,
                "request-1"
        ).reservationId();
    }

    private static InMemoryBudgetStateStore store() {
        AtomicInteger sequence = new AtomicInteger();
        return new InMemoryBudgetStateStore(
                CLOCK,
                () -> new ReservationId("reservation-" + sequence.incrementAndGet())
        );
    }

    private static Cost usd(String amount) {
        return Cost.of(new BigDecimal(amount), USD);
    }
}
