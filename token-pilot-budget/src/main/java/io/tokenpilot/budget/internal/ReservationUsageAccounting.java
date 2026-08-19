package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.ActualUsageCommand;
import io.tokenpilot.budget.BudgetReservation;
import io.tokenpilot.budget.ReservationId;
import io.tokenpilot.budget.ReservationReconciliation;
import io.tokenpilot.budget.ReservationTransition;
import io.tokenpilot.core.CostCalculator;
import io.tokenpilot.core.domain.Cost;
import io.tokenpilot.core.domain.PricingSnapshot;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * actual usage를 예약 시점 가격으로 계산하고 저장소 상태 전이에 연결합니다.
 */
final class ReservationUsageAccounting {

    private final CostCalculator costCalculator;

    ReservationUsageAccounting(CostCalculator costCalculator) {
        this.costCalculator = Objects.requireNonNull(
                costCalculator,
                "costCalculator must not be null"
        );
    }

    ReservationReconciliation commit(
            ActualUsageCommand command,
            BudgetReservation reservation,
            BiFunction<ReservationId, Cost, ReservationTransition> transition
    ) {
        return reconcile(command, reservation, transition);
    }

    ReservationReconciliation reconcileLateActual(
            ActualUsageCommand command,
            BudgetReservation reservation,
            BiFunction<ReservationId, Cost, ReservationTransition> transition
    ) {
        return reconcile(command, reservation, transition);
    }

    private ReservationReconciliation reconcile(
            ActualUsageCommand command,
            BudgetReservation reservation,
            BiFunction<ReservationId, Cost, ReservationTransition> transition
    ) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(reservation, "reservation must not be null");
        Objects.requireNonNull(transition, "transition must not be null");
        if (!reservation.belongsTo(command.requestId())) {
            throw new IllegalArgumentException(
                    "requestId must match the reservation request"
            );
        }

        PricingSnapshot snapshot = reservation.pricingSnapshot().orElseThrow(
                () -> new IllegalStateException(
                        "reservation does not contain a pricing snapshot"
                )
        );
        Cost actualCost = costCalculator.calculate(command.usage(), snapshot);
        if (!snapshot.currency().equals(actualCost.currency())) {
            throw new IllegalStateException(
                    "calculated cost must use the pricing snapshot currency"
            );
        }
        ReservationTransition appliedTransition = transition.apply(
                reservation.id(),
                actualCost
        );
        return new ReservationReconciliation(
                command.requestId(),
                command.attemptId(),
                reservation.id(),
                reservation.key(),
                command.responseModelId(),
                snapshot,
                reservation.amount(),
                actualCost,
                appliedTransition
        );
    }
}
