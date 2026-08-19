package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.AccountingTransitionStatus;
import io.tokenpilot.budget.ReservationState;
import io.tokenpilot.budget.ReservationTransition;
import io.tokenpilot.core.domain.Cost;

import java.util.Objects;

import static io.tokenpilot.budget.AccountingTransitionStatus.CONFLICT;
import static io.tokenpilot.budget.AccountingTransitionStatus.REUSED;

/**
 * 이미 적용된 commit의 종류, actual과 재호출 판단을 보관합니다.
 */
record AppliedCommit(Kind kind, Cost actualCost) {

    AppliedCommit {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(actualCost, "actualCost must not be null");
    }

    static AppliedCommit direct(Cost actualCost) {
        return new AppliedCommit(Kind.DIRECT, actualCost);
    }

    static AppliedCommit lateActual(Cost actualCost) {
        return new AppliedCommit(Kind.LATE_ACTUAL, actualCost);
    }

    ReservationTransition evaluate(
            Kind requestedKind,
            ReservationState state,
            Cost requestedActualCost
    ) {
        Objects.requireNonNull(requestedKind, "requestedKind must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(requestedActualCost, "requestedActualCost must not be null");

        AccountingTransitionStatus status = kind == requestedKind
                && actualCost.equals(requestedActualCost)
                ? REUSED
                : CONFLICT;
        return ReservationTransition.unchanged(state, status);
    }

    enum Kind {
        DIRECT,
        LATE_ACTUAL
    }
}
