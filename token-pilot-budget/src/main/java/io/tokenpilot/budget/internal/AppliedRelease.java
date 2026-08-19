package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.AccountingTransitionStatus;
import io.tokenpilot.budget.ReservationState;
import io.tokenpilot.budget.ReservationTransition;

import java.util.Objects;

import static io.tokenpilot.budget.AccountingTransitionStatus.CONFLICT;
import static io.tokenpilot.budget.AccountingTransitionStatus.REUSED;

/**
 * 이미 적용된 release의 종류와 재호출 판단을 보관합니다.
 */
record AppliedRelease(Kind kind) {

    AppliedRelease {
        Objects.requireNonNull(kind, "kind must not be null");
    }

    static AppliedRelease beforeDispatch() {
        return new AppliedRelease(Kind.BEFORE_DISPATCH);
    }

    static AppliedRelease confirmedUnbilled() {
        return new AppliedRelease(Kind.CONFIRMED_UNBILLED);
    }

    ReservationTransition evaluate(Kind requestedKind, ReservationState state) {
        Objects.requireNonNull(requestedKind, "requestedKind must not be null");
        Objects.requireNonNull(state, "state must not be null");

        AccountingTransitionStatus status = kind == requestedKind ? REUSED : CONFLICT;
        return ReservationTransition.unchanged(state, status);
    }

    enum Kind {
        BEFORE_DISPATCH,
        CONFIRMED_UNBILLED
    }
}
