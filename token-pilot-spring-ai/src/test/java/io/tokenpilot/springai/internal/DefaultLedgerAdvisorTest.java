package io.tokenpilot.springai.internal;

import io.tokenpilot.budget.BudgetDecision;
import io.tokenpilot.budget.BudgetEvaluator;
import io.tokenpilot.budget.BudgetKey;
import io.tokenpilot.budget.BudgetState;
import io.tokenpilot.budget.BudgetStateStore;
import io.tokenpilot.budget.BudgetThreshold;
import io.tokenpilot.budget.BudgetWindow;
import io.tokenpilot.core.*;
import io.tokenpilot.core.domain.*;
import io.tokenpilot.springai.UsageExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefaultLedgerAdvisorTest {

    @Test
    @DisplayName("AI 응답 후 사용량, 모델ID, 태그가 올바르게 LedgerManager에 기록되어야 한다")
    void recordAfterAIResponse() {
        LedgerManager ledgerManager = mock(LedgerManager.class);
        UsageExtractor extractor = mock(UsageExtractor.class);
        TokenUsage mockUsage = TokenUsage.from(100, 200);
        when(extractor.extract(any())).thenReturn(mockUsage);

        DefaultLedgerAdvisor advisor = new DefaultLedgerAdvisor(ledgerManager, extractor);

        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("gpt-4o")
                .build();
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage("test"))), metadata);
        Map<String, Object> context = Map.of("user_id", "user-123", "tenant_id", "tenant-abc");
        ChatClientResponse response = new ChatClientResponse(chatResponse, context);

        advisor.after(response, mock(AdvisorChain.class));

        verify(ledgerManager, times(1)).record(
                eq("gpt-4o"),
                eq(mockUsage),
                argThat(tags -> tags.get("user_id").equals("user-123") && 
                               tags.get("tenant_id").equals("tenant-abc"))
        );
    }

    @Test
    @DisplayName("AI 응답 후 비용이 계산되어 BudgetStateStore에 누적되어야 한다")
    void recordBudgetAfterAIResponse() {
        LedgerManager ledgerManager = mock(LedgerManager.class);
        UsageExtractor extractor = mock(UsageExtractor.class);
        BudgetEvaluator budgetEvaluator = mock(BudgetEvaluator.class);
        BudgetStateStore budgetStateStore = mock(BudgetStateStore.class);
        CostCalculator costCalculator = mock(CostCalculator.class);
        PricingRegistry pricingRegistry = mock(PricingRegistry.class);

        TokenUsage mockUsage = TokenUsage.from(100, 200);
        PricingPlan mockPlan = new PricingPlan("gpt-4o", new BigDecimal("0.01"), new BigDecimal("0.03"), Currency.getInstance("USD"));
        Cost mockCost = new Cost(new BigDecimal("0.5"), Currency.getInstance("USD"));
        BudgetDecision budgetDecision = decision();

        when(extractor.extract(any())).thenReturn(mockUsage);
        when(pricingRegistry.getPlan("gpt-4o", PricingPlan.DEFAULT_PRICING_POLICY_ID)).thenReturn(Optional.of(mockPlan));
        when(ledgerManager.record(same(mockPlan), same(mockUsage), anyMap())).thenReturn(mockCost);
        when(budgetEvaluator.evaluate(anyMap())).thenReturn(budgetDecision);

        DefaultLedgerAdvisor advisor = new DefaultLedgerAdvisor(ledgerManager, extractor, 
                budgetEvaluator, budgetStateStore, costCalculator, pricingRegistry);

        ChatClientRequest request = new ChatClientRequest(
                new Prompt("test"),
                Map.of(
                        DefaultLedgerAdvisor.MODEL_ID_CONTEXT, "gpt-4o",
                        "tenant_id", "tenant-abc"
                )
        );
        ChatClientRequest resolvedRequest = advisor.before(request, mock(AdvisorChain.class));

        ChatResponseMetadata metadata = ChatResponseMetadata.builder().model("gpt-4o").build();
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage("test"))), metadata);
        ChatClientResponse response = new ChatClientResponse(chatResponse, resolvedRequest.context());

        advisor.after(response, mock(AdvisorChain.class));

        verify(budgetStateStore, times(1)).addCost(
                same(budgetDecision.key()),
                same(budgetDecision.limit()),
                same(mockCost)
        );
        verify(pricingRegistry, times(1)).getPlan("gpt-4o", PricingPlan.DEFAULT_PRICING_POLICY_ID);
        verifyNoMoreInteractions(pricingRegistry);
    }

    @Test
    @DisplayName("AI 호출 전 pricing plan을 한 번 resolve하고 이후 정산에서는 registry를 다시 조회하지 않아야 한다")
    void resolvePricingPlanBeforeProviderCallAndReuseItAfterResponse() {
        LedgerManager ledgerManager = mock(LedgerManager.class);
        UsageExtractor extractor = mock(UsageExtractor.class);
        PricingRegistry pricingRegistry = mock(PricingRegistry.class);
        TokenUsage usage = TokenUsage.from(100, 200);
        PricingPlan plan = new PricingPlan(
                "gpt-4o",
                "standard",
                new BigDecimal("0.01"),
                new BigDecimal("0.03"),
                Currency.getInstance("USD")
        );

        when(extractor.extract(any())).thenReturn(usage);
        when(pricingRegistry.getPlan("gpt-4o", "standard")).thenReturn(Optional.of(plan));

        DefaultLedgerAdvisor advisor = new DefaultLedgerAdvisor(
                ledgerManager,
                extractor,
                null,
                null,
                mock(CostCalculator.class),
                pricingRegistry
        );
        ChatClientRequest request = new ChatClientRequest(
                new Prompt("test"),
                Map.of(
                        DefaultLedgerAdvisor.MODEL_ID_CONTEXT, "gpt-4o",
                        DefaultLedgerAdvisor.PRICING_POLICY_ID_CONTEXT, "standard"
                )
        );

        ChatClientRequest resolvedRequest = advisor.before(request, mock(AdvisorChain.class));

        assertThat(resolvedRequest.context().get(DefaultLedgerAdvisor.PRICING_RESOLUTION_CONTEXT))
                .isEqualTo(PricingResolution.RESOLVED);
        assertThat(resolvedRequest.context().get(DefaultLedgerAdvisor.PRICING_PLAN_CONTEXT))
                .isSameAs(plan);

        ChatClientResponse response = response("gpt-4o", resolvedRequest.context());

        advisor.after(response, mock(AdvisorChain.class));

        verify(pricingRegistry, times(1)).getPlan("gpt-4o", "standard");
        verifyNoMoreInteractions(pricingRegistry);
        verify(ledgerManager, times(1)).record(same(plan), same(usage), anyMap());
        verify(ledgerManager, never()).record(eq("gpt-4o"), same(usage), anyMap());
    }

    @Test
    @DisplayName("AI 호출 전 pricing resolution 실패는 provider 호출 여부 판단 값으로 전달되어야 한다")
    void exposeMissingPricingResolutionBeforeProviderCall() {
        LedgerManager ledgerManager = mock(LedgerManager.class);
        UsageExtractor extractor = mock(UsageExtractor.class);
        PricingRegistry pricingRegistry = mock(PricingRegistry.class);
        TokenUsage usage = TokenUsage.from(100, 200);

        when(extractor.extract(any())).thenReturn(usage);
        when(pricingRegistry.getPlan("missing-model", PricingPlan.DEFAULT_PRICING_POLICY_ID))
                .thenReturn(Optional.empty());

        DefaultLedgerAdvisor advisor = new DefaultLedgerAdvisor(
                ledgerManager,
                extractor,
                null,
                null,
                mock(CostCalculator.class),
                pricingRegistry
        );
        ChatClientRequest request = new ChatClientRequest(
                new Prompt("test"),
                Map.of(DefaultLedgerAdvisor.MODEL_ID_CONTEXT, "missing-model")
        );

        ChatClientRequest resolvedRequest = advisor.before(request, mock(AdvisorChain.class));

        assertThat(resolvedRequest.context().get(DefaultLedgerAdvisor.PRICING_RESOLUTION_CONTEXT))
                .isEqualTo(PricingResolution.MISSING_PLAN);

        advisor.after(response("missing-model", resolvedRequest.context()), mock(AdvisorChain.class));

        verify(pricingRegistry, times(1)).getPlan("missing-model", PricingPlan.DEFAULT_PRICING_POLICY_ID);
        verifyNoMoreInteractions(pricingRegistry);
        verifyNoInteractions(ledgerManager);
    }

    @Test
    @DisplayName("AI 호출 전 BudgetEvaluator를 통해 예산을 체크해야 한다")
    void checkBudgetBeforeAIRequest() {
        BudgetEvaluator budgetEvaluator = mock(BudgetEvaluator.class);
        BudgetDecision decision = decision();
        when(budgetEvaluator.evaluate(anyMap())).thenReturn(decision);
        DefaultLedgerAdvisor advisor = new DefaultLedgerAdvisor(mock(LedgerManager.class), mock(UsageExtractor.class),
                budgetEvaluator, null, null, null);

        ChatClientRequest request = new ChatClientRequest(
                new Prompt("test"),
                Map.of("tenant_id", "tenant-abc")
        );

        ChatClientRequest resolvedRequest = advisor.before(request, mock(AdvisorChain.class));

        verify(budgetEvaluator, times(1)).evaluate(
                argThat(tags -> tags.get("tenant_id").equals("tenant-abc"))
        );
        assertThat(resolvedRequest.context().get(DefaultLedgerAdvisor.BUDGET_DECISION_CONTEXT))
                .isSameAs(decision);
    }

    @Test
    @DisplayName("Advisor 이름과 순서가 기본값으로 설정되어야 한다")
    void checkAdvisorMetadata() {
        DefaultLedgerAdvisor advisor = new DefaultLedgerAdvisor(mock(LedgerManager.class), mock(UsageExtractor.class));

        assertThat(advisor.getName()).isEqualTo("LedgerAdvisor");
        assertThat(advisor.getOrder()).isEqualTo(0);
    }

    private static BudgetDecision decision() {
        return new BudgetDecision(
                new BudgetKey(
                        "policy-a",
                        "tenant",
                        "tenant-abc",
                        BudgetWindow.parse("2026-07")
                ),
                BudgetState.ALLOW,
                BudgetThreshold.NONE,
                "allowed",
                Cost.zero(Currency.getInstance("USD")),
                Cost.of(new BigDecimal("100.00"), Currency.getInstance("USD"))
        );
    }

    private static ChatClientResponse response(String modelId, Map<String, Object> context) {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder().model(modelId).build();
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage("test"))),
                metadata
        );
        return new ChatClientResponse(chatResponse, context);
    }
}
