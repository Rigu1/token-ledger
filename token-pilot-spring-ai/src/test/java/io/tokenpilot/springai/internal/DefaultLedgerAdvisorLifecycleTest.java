package io.tokenpilot.springai.internal;

import io.tokenpilot.budget.AccountingTransitionStatus;
import io.tokenpilot.budget.BudgetDecision;
import io.tokenpilot.budget.BudgetEvaluator;
import io.tokenpilot.budget.BudgetKey;
import io.tokenpilot.budget.BudgetReservation;
import io.tokenpilot.budget.BudgetReservationResult;
import io.tokenpilot.budget.BudgetState;
import io.tokenpilot.budget.BudgetStateStore;
import io.tokenpilot.budget.BudgetThreshold;
import io.tokenpilot.budget.BudgetWindow;
import io.tokenpilot.budget.IdempotencyKey;
import io.tokenpilot.budget.ReservationAccounting;
import io.tokenpilot.budget.ReservationAccountingReason;
import io.tokenpilot.budget.ReservationId;
import io.tokenpilot.budget.ReservationState;
import io.tokenpilot.budget.ReservationTransition;
import io.tokenpilot.core.CostCalculator;
import io.tokenpilot.core.LedgerManager;
import io.tokenpilot.core.PricingEvaluator;
import io.tokenpilot.core.PricingRegistry;
import io.tokenpilot.core.domain.Cost;
import io.tokenpilot.core.domain.MissingPricingPolicy;
import io.tokenpilot.core.domain.PreflightCostResult;
import io.tokenpilot.core.domain.PricingSnapshot;
import io.tokenpilot.core.domain.TokenUsage;
import io.tokenpilot.springai.UsageExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class DefaultLedgerAdvisorLifecycleTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final ReservationId RESERVATION_ID =
            new ReservationId("reservation-1");

    private RequestPreflight preflight;
    private BudgetEvaluator budgetEvaluator;
    private Object accountingWriter;
    private BudgetStateStore stateStore;
    private ReservationAccounting accounting;
    private UsageExtractor usageExtractor;
    private CallAdvisorChain provider;
    private DefaultLedgerAdvisor advisor;

    @BeforeEach
    void setUp() {
        preflight = mock(RequestPreflight.class);
        budgetEvaluator = mock(BudgetEvaluator.class);
        accountingWriter = mock(
                BudgetStateStore.class,
                withSettings().extraInterfaces(ReservationAccounting.class)
        );
        stateStore = (BudgetStateStore) accountingWriter;
        accounting = (ReservationAccounting) accountingWriter;
        usageExtractor = mock(UsageExtractor.class);
        provider = mock(CallAdvisorChain.class);
        RequestContextAccessor contextAccessor = new RequestContextAccessor();
        advisor = new DefaultLedgerAdvisor(
                mock(LedgerManager.class),
                usageExtractor,
                budgetEvaluator,
                stateStore,
                mock(CostCalculator.class),
                mock(PricingRegistry.class),
                mock(PricingEvaluator.class),
                MissingPricingPolicy.FAIL_CLOSED,
                preflight,
                contextAccessor,
                new IdempotencyKeyResolver(
                        contextAccessor,
                        () -> new IdempotencyKey("generated-key")
                )
        );
    }

    @Test
    @DisplayName("정상 call은 예약과 provider를 거쳐 actual을 한 번 commit한다")
    void commitsSuccessfulCallOnce() {
        ChatClientRequest request = request();
        ChatClientResponse response = response();
        PreflightCostResult.Bounded costBound = stubDispatch(request, response);
        when(usageExtractor.extract(response)).thenReturn(TokenUsage.from(10, 5));

        ChatClientResponse result = advisor.adviseCall(request, provider);

        assertThat(result).isSameAs(response);
        var order = inOrder(preflight, stateStore, accounting, provider, usageExtractor);
        order.verify(preflight).resolve(request);
        order.verify(stateStore).checkAndReserve(
                argThat(reservation -> reservation.pricingSnapshot().orElseThrow()
                        .equals(costBound.pricingSnapshot()))
        );
        order.verify(accounting).markInFlight(RESERVATION_ID);
        order.verify(provider).nextCall(any());
        order.verify(usageExtractor).extract(response);
        order.verify(accounting).commit(
                argThat(command -> command.requestId().equals("request-1")
                        && command.attemptId().equals("attempt-1")
                        && command.reservationId().equals(RESERVATION_ID)
                        && command.responseModelId().equals("model-v1"))
        );
    }

    @Test
    @DisplayName("preflight 실패는 예약과 provider 호출 전에 종료한다")
    void stopsBeforeReservationWhenPreflightFails() {
        ChatClientRequest request = request();
        when(preflight.resolve(request))
                .thenThrow(new IllegalStateException("MODEL_UNRESOLVED"));

        assertThatThrownBy(() -> advisor.adviseCall(request, provider))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MODEL_UNRESOLVED");

        verifyNoInteractions(accountingWriter, provider, usageExtractor);
    }

    @Test
    @DisplayName("dispatch 준비 실패는 예약을 release하고 provider를 호출하지 않는다")
    void releasesBeforeDispatch() {
        ChatClientRequest request = request();
        stubReservation(request);
        when(accounting.markInFlight(RESERVATION_ID))
                .thenReturn(ReservationTransition.unchanged(
                        ReservationState.RESERVED,
                        AccountingTransitionStatus.NOT_ALLOWED
                ));

        assertThatThrownBy(() -> advisor.adviseCall(request, provider))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not enter IN_FLIGHT");

        verify(accounting).releaseBeforeDispatch(RESERVATION_ID);
        verifyNoInteractions(provider, usageExtractor);
    }

    @Test
    @DisplayName("IN_FLIGHT 이후 downstream 실패는 pending liability로 보존한다")
    void preservesPendingLiabilityAfterDownstreamFailure() {
        ChatClientRequest request = request();
        RuntimeException failure = new IllegalStateException("provider failed");
        stubReservation(request);
        when(accounting.markInFlight(RESERVATION_ID))
                .thenReturn(inFlight());
        when(provider.nextCall(any())).thenThrow(failure);

        assertThatThrownBy(() -> advisor.adviseCall(request, provider))
                .isSameAs(failure);

        verify(accounting).markReconciliationRequired(
                RESERVATION_ID,
                ReservationAccountingReason.ACTUAL_USAGE_UNAVAILABLE
        );
        verifyNoInteractions(usageExtractor);
    }

    @Test
    @DisplayName("usage unavailable은 0원 commit 없이 provider 응답을 보존한다")
    void preservesResponseWhenUsageIsUnavailable() {
        ChatClientRequest request = request();
        ChatClientResponse response = response();
        stubDispatch(request, response);
        when(usageExtractor.extract(response))
                .thenReturn(TokenUsage.unavailable(Map.of()));
        when(accounting.markReconciliationRequired(
                RESERVATION_ID,
                ReservationAccountingReason.ACTUAL_USAGE_UNAVAILABLE
        )).thenThrow(new IllegalStateException("recording failed"));

        ChatClientResponse result = advisor.adviseCall(request, provider);

        assertThat(result).isSameAs(response);
        verify(accounting, times(1)).markReconciliationRequired(
                RESERVATION_ID,
                ReservationAccountingReason.ACTUAL_USAGE_UNAVAILABLE
        );
        verify(accounting, never()).commit(any());
    }

    @Test
    @DisplayName("usage extraction 오류는 provider 응답을 보존하고 정산 대기로 전환한다")
    void preservesResponseWhenUsageExtractionFails() {
        ChatClientRequest request = request();
        ChatClientResponse response = response();
        stubDispatch(request, response);
        when(usageExtractor.extract(response))
                .thenThrow(new IllegalStateException("extraction failed"));

        ChatClientResponse result = advisor.adviseCall(request, provider);

        assertThat(result).isSameAs(response);
        verify(accounting).markReconciliationRequired(
                RESERVATION_ID,
                ReservationAccountingReason.ACTUAL_USAGE_UNAVAILABLE
        );
        verify(accounting, never()).commit(any());
    }

    @Test
    @DisplayName("actual 비용 또는 통화 오류는 provider 응답을 보존하고 정산 대기로 전환한다")
    void preservesResponseWhenActualCommitFails() {
        ChatClientRequest request = request();
        ChatClientResponse response = response();
        stubDispatch(request, response);
        when(usageExtractor.extract(response)).thenReturn(TokenUsage.from(10, 5));
        when(accounting.commit(any())).thenThrow(new IllegalStateException(
                "calculated cost must use the pricing snapshot currency"
        ));

        ChatClientResponse result = advisor.adviseCall(request, provider);

        assertThat(result).isSameAs(response);
        verify(accounting).markReconciliationRequired(
                RESERVATION_ID,
                ReservationAccountingReason.ACTUAL_USAGE_UNAVAILABLE
        );
    }

    private PreflightCostResult.Bounded stubDispatch(
            ChatClientRequest request,
            ChatClientResponse response
    ) {
        PreflightCostResult.Bounded costBound = stubReservation(request);
        when(accounting.markInFlight(RESERVATION_ID)).thenReturn(inFlight());
        when(provider.nextCall(any())).thenReturn(response);
        return costBound;
    }

    private PreflightCostResult.Bounded stubReservation(ChatClientRequest request) {
        PreflightCostResult.Bounded costBound = costBound();
        Cost safeUpperBoundCost = costBound.safeUpperBoundCost();
        when(preflight.resolve(request)).thenReturn(costBound);
        when(budgetEvaluator.evaluate(any(), any()))
                .thenReturn(allowed(safeUpperBoundCost));
        BudgetReservation reservation = mock(BudgetReservation.class);
        BudgetReservationResult result = mock(BudgetReservationResult.class);
        when(reservation.id()).thenReturn(RESERVATION_ID);
        when(result.isAccepted()).thenReturn(true);
        when(result.reservation()).thenReturn(reservation);
        when(stateStore.checkAndReserve(any())).thenReturn(result);
        return costBound;
    }

    private PreflightCostResult.Bounded costBound() {
        PreflightCostResult.Bounded result = mock(PreflightCostResult.Bounded.class);
        PricingSnapshot pricingSnapshot = mock(PricingSnapshot.class);
        when(pricingSnapshot.currency()).thenReturn(USD);
        when(result.safeUpperBoundCost()).thenReturn(usd("0.01"));
        when(result.pricingSnapshot()).thenReturn(pricingSnapshot);
        when(result.inputEstimatedTokens()).thenReturn(10L);
        when(result.inputSafeUpperBoundTokens()).thenReturn(12L);
        when(result.reservedOutputTokens()).thenReturn(5L);
        return result;
    }

    private BudgetDecision allowed(Cost candidate) {
        return new BudgetDecision(
                new BudgetKey(
                        "monthly",
                        "tenant",
                        "tenant-1",
                        BudgetWindow.parse("2026-08")
                ),
                BudgetDecision.EvaluationType.ADMISSION,
                BudgetState.ALLOW,
                BudgetThreshold.NONE,
                "allowed",
                Cost.zero(USD),
                candidate,
                usd("10.00")
        );
    }

    private ChatClientRequest request() {
        Map<String, Object> context = new HashMap<>();
        context.put(RequestContextAccessor.REQUEST_ID_CONTEXT_KEY, "request-1");
        context.put(RequestContextAccessor.ATTEMPT_ID_CONTEXT_KEY, "attempt-1");
        context.put(
                RequestContextAccessor.IDEMPOTENCY_CONTEXT_KEY,
                new IdempotencyKey("idempotency-1")
        );
        context.put("tenant_id", "tenant-1");
        return new ChatClientRequest(new Prompt("question"), context);
    }

    private ChatClientResponse response() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(metadata.getModel()).thenReturn("model-v1");
        return new ChatClientResponse(chatResponse, Map.of());
    }

    private ReservationTransition inFlight() {
        return ReservationTransition.applied(
                ReservationState.RESERVED,
                ReservationState.IN_FLIGHT
        );
    }

    private Cost usd(String amount) {
        return Cost.of(new BigDecimal(amount), USD);
    }
}
