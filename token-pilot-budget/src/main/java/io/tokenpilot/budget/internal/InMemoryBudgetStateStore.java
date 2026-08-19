package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.ActualUsageCommand;
import io.tokenpilot.budget.BudgetKey;
import io.tokenpilot.budget.BudgetReservation;
import io.tokenpilot.budget.BudgetReservationRequest;
import io.tokenpilot.budget.BudgetReservationResult;
import io.tokenpilot.budget.BudgetSnapshot;
import io.tokenpilot.budget.BudgetStateStore;
import io.tokenpilot.budget.IdempotencyKey;
import io.tokenpilot.budget.ReservationAccounting;
import io.tokenpilot.budget.ReservationId;
import io.tokenpilot.budget.ReservationReconciliation;
import io.tokenpilot.budget.ReservationState;
import io.tokenpilot.budget.ReservationStateMachine;
import io.tokenpilot.budget.ReservationTransition;
import io.tokenpilot.core.CostCalculator;
import io.tokenpilot.core.domain.Cost;
import io.tokenpilot.core.internal.LedgerComponents;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * resolved {@link BudgetKey}별 확정 비용과 원자적 예약을 관리하는 인메모리 저장소입니다.
 *
 * <p>bucket별 monitor가 조회·통화 검증·한도 검증·예약 갱신을 함께 보호하고,
 * 별도의 idempotency index가 같은 요청의 중복 예약을 차단합니다.</p>
 */
public class InMemoryBudgetStateStore implements BudgetStateStore, ReservationAccounting {

    private final ConcurrentMap<BudgetKey, Bucket> store = new ConcurrentHashMap<>();
    private final ConcurrentMap<IdempotencyKey, ReservationId> idempotencyIndex =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<ReservationId, BudgetKey> reservationIndex =
            new ConcurrentHashMap<>();
    private final Clock clock;
    private final Supplier<ReservationId> reservationIdGenerator;
    private final ReservationUsageAccounting usageAccounting;

    public InMemoryBudgetStateStore() {
        this(
                Clock.systemUTC(),
                ReservationId::random,
                LedgerComponents.defaultCostCalculator()
        );
    }

    public InMemoryBudgetStateStore(
            Clock clock,
            Supplier<ReservationId> reservationIdGenerator
    ) {
        this(
                clock,
                reservationIdGenerator,
                LedgerComponents.defaultCostCalculator()
        );
    }

    public InMemoryBudgetStateStore(
            Clock clock,
            Supplier<ReservationId> reservationIdGenerator,
            CostCalculator costCalculator
    ) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.reservationIdGenerator = Objects.requireNonNull(
                reservationIdGenerator,
                "reservationIdGenerator must not be null"
        );
        this.usageAccounting = new ReservationUsageAccounting(
                Objects.requireNonNull(costCalculator, "costCalculator must not be null")
        );
    }

    @Override
    public Cost getAccumulatedCost(BudgetKey key, Cost limit) {
        validateArguments(key, limit);
        Bucket bucket = store.get(key);
        if (bucket == null) {
            return Cost.zero(limit.currency());
        }
        synchronized (bucket) {
            bucket.validate(limit);
            return bucket.committedCost;
        }
    }

    @Override
    public void addCost(BudgetKey key, Cost limit, Cost amount) {
        validateArguments(key, limit);
        Objects.requireNonNull(amount, "amount must not be null");
        validateCurrency(limit, amount);

        Bucket bucket = store.computeIfAbsent(key, ignored -> new Bucket(limit));
        synchronized (bucket) {
            bucket.validate(limit);
            bucket.committedCost = bucket.committedCost.add(amount);
        }
    }

    @Override
    public BudgetReservationResult checkAndReserve(BudgetReservationRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        AtomicReference<BudgetReservationResult> result = new AtomicReference<>();
        idempotencyIndex.compute(
                request.idempotencyKey(),
                (ignored, existingReservationId) -> reserveOrReturnExisting(
                        request,
                        existingReservationId,
                        result
                )
        );

        return Objects.requireNonNull(result.get(), "reservation result must be set");
    }

    @Override
    public ReservationTransition markInFlight(ReservationId reservationId) {
        return updateState(reservationId, ReservationStateMachine::onDispatch);
    }

    @Override
    public ReservationTransition releaseBeforeDispatch(ReservationId reservationId) {
        Bucket bucket = bucketFor(reservationId);
        synchronized (bucket) {
            ReservationAccountingState accountingState = accountingState(
                    bucket,
                    reservationId
            );
            BudgetReservation reservation = accountingState.reservation();
            ReservationTransition transition =
                    accountingState.evaluateReleaseBeforeDispatch();
            if (!transition.status().isApplied()) {
                return transition;
            }

            BudgetReservation updated = withState(
                    reservation,
                    transition.resultingState()
            );
            releaseActiveReservation(
                    bucket,
                    accountingState,
                    accountingState.releasedBeforeDispatch(updated)
            );
            return transition;
        }
    }

    @Override
    public ReservationTransition releaseConfirmedUnbilled(ReservationId reservationId) {
        Bucket bucket = bucketFor(reservationId);
        synchronized (bucket) {
            ReservationAccountingState accountingState = accountingState(
                    bucket,
                    reservationId
            );
            BudgetReservation reservation = accountingState.reservation();
            ReservationTransition transition =
                    accountingState.evaluateConfirmedUnbilledRelease();
            if (!transition.status().isApplied()) {
                return transition;
            }

            BudgetReservation updated = withState(
                    reservation,
                    transition.resultingState()
            );
            releaseActiveReservation(
                    bucket,
                    accountingState,
                    accountingState.confirmedUnbilledReleased(updated)
            );
            return transition;
        }
    }

    ReservationTransition commitCost(ReservationId reservationId, Cost actualCost) {
        Objects.requireNonNull(actualCost, "actualCost must not be null");
        Bucket bucket = bucketFor(reservationId);
        synchronized (bucket) {
            ReservationAccountingState accountingState = accountingState(
                    bucket,
                    reservationId
            );
            ReservationTransition transition = accountingState.evaluateCommit(actualCost);
            if (!transition.status().isApplied()) {
                return transition;
            }

            commitActiveReservation(
                    bucket,
                    accountingState,
                    actualCost,
                    transition.resultingState()
            );
            return transition;
        }
    }

    @Override
    public ReservationReconciliation commit(ActualUsageCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return usageAccounting.commit(
                command,
                reservationForAccounting(command.reservationId()),
                this::commitCost
        );
    }

    @Override
    public ReservationTransition markReconciliationRequired(ReservationId reservationId) {
        Bucket bucket = bucketFor(reservationId);
        synchronized (bucket) {
            ReservationAccountingState accountingState = accountingState(
                    bucket,
                    reservationId
            );
            BudgetReservation reservation = accountingState.reservation();
            ReservationTransition transition =
                    ReservationStateMachine.markReconciliationRequired(
                            reservation.state()
                    );
            if (!transition.status().isApplied()) {
                return transition;
            }

            moveActiveReservationToPending(
                    bucket,
                    accountingState,
                    transition.resultingState()
            );
            return transition;
        }
    }

    ReservationTransition reconcileLateActualCost(
            ReservationId reservationId,
            Cost actualCost
    ) {
        Objects.requireNonNull(actualCost, "actualCost must not be null");
        Bucket bucket = bucketFor(reservationId);
        synchronized (bucket) {
            ReservationAccountingState accountingState = accountingState(
                    bucket,
                    reservationId
            );
            ReservationTransition transition = accountingState.evaluateLateActual(actualCost);
            if (!transition.status().isApplied()) {
                return transition;
            }

            commitPendingReservation(
                    bucket,
                    accountingState,
                    actualCost,
                    transition.resultingState()
            );
            return transition;
        }
    }

    @Override
    public ReservationReconciliation reconcileLateActual(ActualUsageCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return usageAccounting.reconcileLateActual(
                command,
                reservationForAccounting(command.reservationId()),
                this::reconcileLateActualCost
        );
    }

    @Override
    public ReservationTransition writeOff(ReservationId reservationId) {
        Bucket bucket = bucketFor(reservationId);
        synchronized (bucket) {
            ReservationAccountingState accountingState = accountingState(
                    bucket,
                    reservationId
            );
            ReservationTransition transition = accountingState.evaluateWriteOff();
            if (!transition.status().isApplied()) {
                return transition;
            }

            writeOffPendingReservation(
                    bucket,
                    accountingState,
                    transition.resultingState()
            );
            return transition;
        }
    }

    @Override
    public BudgetSnapshot snapshot(BudgetKey key, Cost limit) {
        validateArguments(key, limit);
        Bucket bucket = store.get(key);
        if (bucket == null) {
            return BudgetSnapshot.empty(key, limit);
        }
        synchronized (bucket) {
            bucket.validate(limit);
            return bucket.snapshot(key);
        }
    }

    private void validateArguments(BudgetKey key, Cost limit) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(limit, "limit must not be null");
        if (limit.value().signum() <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
    }

    private void validateCurrency(Cost limit, Cost amount) {
        if (!limit.currency().equals(amount.currency())) {
            throw new IllegalArgumentException("Budget currency does not match cost currency");
        }
    }

    BudgetReservation reservationForAccounting(ReservationId reservationId) {
        Bucket bucket = bucketFor(reservationId);
        synchronized (bucket) {
            return accountingState(bucket, reservationId).reservation();
        }
    }

    private ReservationId reserveOrReturnExisting(
            BudgetReservationRequest request,
            ReservationId existingReservationId,
            AtomicReference<BudgetReservationResult> result
    ) {
        if (existingReservationId != null) {
            result.set(resultForExistingReservation(existingReservationId, request));
            return existingReservationId;
        }

        BudgetReservationResult reservationResult = reserveNewRequest(request);
        result.set(reservationResult);
        return reservationResult.reservationId();
    }

    private BudgetReservationResult resultForExistingReservation(
            ReservationId existingReservationId,
            BudgetReservationRequest request
    ) {
        Bucket bucket = bucketFor(existingReservationId);
        synchronized (bucket) {
            BudgetReservation existing = accountingState(
                    bucket,
                    existingReservationId
            ).reservation();
            BudgetSnapshot existingSnapshot = bucket.snapshot(existing.key());
            if (existing.matches(request)) {
                return BudgetReservationResult.reused(existing, existingSnapshot);
            }
            return BudgetReservationResult.conflict(
                    existing,
                    existingSnapshot,
                    "동일 idempotency key에 다른 예약 요청이 사용되었습니다"
            );
        }
    }

    private BudgetReservationResult reserveNewRequest(BudgetReservationRequest request) {
        if (!request.limit().currency().equals(request.safeUpperBoundCost().currency())) {
            return BudgetReservationResult.currencyMismatch(
                    BudgetSnapshot.empty(request.key(), request.limit())
            );
        }

        Bucket bucket = store.computeIfAbsent(
                request.key(),
                ignored -> new Bucket(request.limit())
        );
        synchronized (bucket) {
            return reserveInBucket(bucket, request);
        }
    }

    private BudgetReservationResult reserveInBucket(
            Bucket bucket,
            BudgetReservationRequest request
    ) {
        if (!bucket.limit.currency().equals(request.limit().currency())) {
            return BudgetReservationResult.currencyMismatch(
                    bucket.snapshot(request.key())
            );
        }
        if (!bucket.limit.equals(request.limit())) {
            return BudgetReservationResult.conflict(
                    null,
                    bucket.snapshot(request.key()),
                    "기존 budget bucket의 limit snapshot이 변경되었습니다"
            );
        }

        Cost projectedUsage = bucket.effectiveUsage().add(request.safeUpperBoundCost());
        if (projectedUsage.compareTo(request.limit()) >= 0) {
            return BudgetReservationResult.blocked(
                    bucket.snapshot(request.key()),
                    "예약 후 사용량이 예산 한도에 도달하거나 초과합니다"
            );
        }

        return createReservation(bucket, request);
    }

    private BudgetReservationResult createReservation(
            Bucket bucket,
            BudgetReservationRequest request
    ) {
        ReservationId reservationId = Objects.requireNonNull(
                reservationIdGenerator.get(),
                "reservationIdGenerator returned null"
        );
        BudgetReservation reservation = BudgetReservation.reserved(
                reservationId,
                request,
                clock.instant()
        );
        if (bucket.reservationsById.containsKey(reservationId)) {
            throw duplicateReservationId();
        }
        if (reservationIndex.putIfAbsent(reservationId, request.key()) != null) {
            throw duplicateReservationId();
        }

        bucket.activeReservedCost = bucket.activeReservedCost.add(
                request.safeUpperBoundCost()
        );
        bucket.reservationsById.put(
                reservationId,
                ReservationAccountingState.reserved(reservation)
        );
        return BudgetReservationResult.created(
                reservation,
                bucket.snapshot(request.key())
        );
    }

    private static IllegalStateException duplicateReservationId() {
        return new IllegalStateException(
                "reservationIdGenerator returned a duplicate reservation id"
        );
    }

    private ReservationTransition updateState(
            ReservationId reservationId,
            java.util.function.Function<ReservationState, ReservationTransition> transitionRule
    ) {
        Objects.requireNonNull(transitionRule, "transitionRule must not be null");
        Bucket bucket = bucketFor(reservationId);
        synchronized (bucket) {
            ReservationAccountingState accountingState = accountingState(
                    bucket,
                    reservationId
            );
            BudgetReservation reservation = accountingState.reservation();
            ReservationTransition transition = Objects.requireNonNull(
                    transitionRule.apply(reservation.state()),
                    "transitionRule must return a transition"
            );
            if (transition.status().isApplied()) {
                BudgetReservation updated = withState(
                        reservation,
                        transition.resultingState()
                );
                replaceReservationSnapshot(
                        bucket,
                        accountingState,
                        accountingState.withReservation(updated)
                );
            }
            return transition;
        }
    }

    private void commitActiveReservation(
            Bucket bucket,
            ReservationAccountingState accountingState,
            Cost actualCost,
            ReservationState resultingState
    ) {
        BudgetReservation reservation = accountingState.reservation();
        Cost remainingReserved = subtract(
                bucket.activeReservedCost,
                reservation.amount()
        );
        Cost committed = bucket.committedCost.add(actualCost);
        BudgetReservation updated = withState(reservation, resultingState);

        bucket.activeReservedCost = remainingReserved;
        bucket.committedCost = committed;
        replaceReservationSnapshot(
                bucket,
                accountingState,
                accountingState.committed(updated, actualCost)
        );
    }

    private void releaseActiveReservation(
            Bucket bucket,
            ReservationAccountingState accountingState,
            ReservationAccountingState updatedAccountingState
    ) {
        BudgetReservation reservation = accountingState.reservation();
        Cost remainingReserved = subtract(
                bucket.activeReservedCost,
                reservation.amount()
        );

        bucket.activeReservedCost = remainingReserved;
        replaceReservationSnapshot(
                bucket,
                accountingState,
                updatedAccountingState
        );
    }

    private void moveActiveReservationToPending(
            Bucket bucket,
            ReservationAccountingState accountingState,
            ReservationState resultingState
    ) {
        BudgetReservation reservation = accountingState.reservation();
        Cost remainingReserved = subtract(
                bucket.activeReservedCost,
                reservation.amount()
        );
        Cost pending = bucket.pendingReconciliationLiability.add(
                reservation.amount()
        );
        BudgetReservation updated = withState(reservation, resultingState);

        bucket.activeReservedCost = remainingReserved;
        bucket.pendingReconciliationLiability = pending;
        replaceReservationSnapshot(
                bucket,
                accountingState,
                accountingState.withReservation(updated)
        );
    }

    private void commitPendingReservation(
            Bucket bucket,
            ReservationAccountingState accountingState,
            Cost actualCost,
            ReservationState resultingState
    ) {
        BudgetReservation reservation = accountingState.reservation();
        Cost remainingPending = subtract(
                bucket.pendingReconciliationLiability,
                reservation.amount()
        );
        Cost committed = bucket.committedCost.add(actualCost);
        BudgetReservation updated = withState(reservation, resultingState);

        bucket.pendingReconciliationLiability = remainingPending;
        bucket.committedCost = committed;
        replaceReservationSnapshot(
                bucket,
                accountingState,
                accountingState.lateActualCommitted(updated, actualCost)
        );
    }

    private void writeOffPendingReservation(
            Bucket bucket,
            ReservationAccountingState accountingState,
            ReservationState resultingState
    ) {
        BudgetReservation reservation = accountingState.reservation();
        Cost remainingPending = subtract(
                bucket.pendingReconciliationLiability,
                reservation.amount()
        );
        BudgetReservation updated = withState(reservation, resultingState);

        bucket.pendingReconciliationLiability = remainingPending;
        replaceReservationSnapshot(
                bucket,
                accountingState,
                accountingState.withReservation(updated)
        );
    }

    private void replaceReservationSnapshot(
            Bucket bucket,
            ReservationAccountingState previous,
            ReservationAccountingState updated
    ) {
        BudgetReservation previousReservation = previous.reservation();
        bucket.reservationsById.put(previousReservation.id(), updated);
    }

    private Bucket bucketFor(ReservationId reservationId) {
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        BudgetKey key = reservationIndex.get(reservationId);
        if (key == null) {
            throw new IllegalArgumentException("reservation does not exist");
        }
        return Objects.requireNonNull(store.get(key), "reservation bucket must exist");
    }

    private static ReservationAccountingState accountingState(
            Bucket bucket,
            ReservationId reservationId
    ) {
        return Objects.requireNonNull(
                bucket.reservationsById.get(reservationId),
                "reservation must exist in its bucket"
        );
    }

    private static Cost subtract(Cost total, Cost amount) {
        if (total.compareTo(amount) < 0) {
            throw new IllegalStateException("reserved cost must not become negative");
        }
        return Cost.of(total.value().subtract(amount.value()), total.currency());
    }

    private static BudgetReservation withState(
            BudgetReservation reservation,
            ReservationState state
    ) {
        return new BudgetReservation(
                reservation.id(),
                reservation.key(),
                reservation.limit(),
                reservation.amount(),
                reservation.requestId(),
                reservation.idempotencyKey(),
                reservation.modelId(),
                reservation.pricingPolicyId(),
                reservation.catalogVersion(),
                reservation.pricingSnapshot(),
                state,
                reservation.createdAt()
        );
    }

    private static final class Bucket {
        private final Cost limit;
        private Cost committedCost;
        private Cost activeReservedCost;
        private Cost pendingReconciliationLiability;
        private final Map<ReservationId, ReservationAccountingState> reservationsById =
                new LinkedHashMap<>();

        private Bucket(Cost limit) {
            this.limit = limit;
            this.committedCost = Cost.zero(limit.currency());
            this.activeReservedCost = Cost.zero(limit.currency());
            this.pendingReconciliationLiability = Cost.zero(limit.currency());
        }

        private void validate(Cost expectedLimit) {
            if (!limit.equals(expectedLimit)) {
                throw new IllegalArgumentException(
                        "Budget policy snapshot changed for an existing key"
                );
            }
        }

        private Cost effectiveUsage() {
            return committedCost
                    .add(activeReservedCost)
                    .add(pendingReconciliationLiability);
        }

        private BudgetSnapshot snapshot(BudgetKey key) {
            return new BudgetSnapshot(
                    key,
                    limit,
                    committedCost,
                    activeReservedCost,
                    pendingReconciliationLiability,
                    activeReservationIds()
            );
        }

        private Set<ReservationId> activeReservationIds() {
            return reservationsById.entrySet().stream()
                    .filter(entry -> {
                        ReservationState state = entry.getValue().reservation().state();
                        return state == ReservationState.RESERVED
                                || state == ReservationState.IN_FLIGHT;
                    })
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

}
