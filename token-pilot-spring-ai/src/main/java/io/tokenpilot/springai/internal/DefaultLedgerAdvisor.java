package io.tokenpilot.springai.internal;

import io.tokenpilot.budget.BudgetDecision;
import io.tokenpilot.budget.BudgetEvaluator;
import io.tokenpilot.budget.BudgetStateStore;
import io.tokenpilot.core.*;
import io.tokenpilot.core.domain.*;
import io.tokenpilot.core.exception.MissingPricingException;
import io.tokenpilot.springai.LedgerAdvisor;
import io.tokenpilot.springai.UsageExtractor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 기본 {@link LedgerAdvisor} 구현체.
 * {@link UsageExtractor}를 사용하여 토큰 사용량을 추출하고,
 * 그 결과를 {@link LedgerManager}에 기록하는 핵심 비즈니스 로직을 수행합니다.
 * 또한 {@link BudgetEvaluator}를 통해 예산 초과 여부를 사전에 차단하고,
 * 호출 성공 시 {@link BudgetStateStore}에 비용을 누적합니다.
 */
public class DefaultLedgerAdvisor implements LedgerAdvisor {

    static final String BUDGET_DECISION_CONTEXT = "tokenpilot.budget.decision";
    static final String MODEL_ID_CONTEXT = "tokenpilot.model.id";
    static final String PRICING_POLICY_ID_CONTEXT = "tokenpilot.pricing.policy.id";
    static final String PRICING_SNAPSHOT_CONTEXT = "tokenpilot.pricing.snapshot";
    static final String PRICING_RESOLUTION_CONTEXT = "tokenpilot.pricing.resolution";
    static final String PRICING_RECONCILIATION_RESULT_CONTEXT = "tokenpilot.pricing.reconciliation.result";
    static final String REQUIRED_TOKEN_TYPE_CONTEXT = "tokenpilot.pricing.required.token.type";

    private final LedgerManager ledgerManager;
    private final UsageExtractor usageExtractor;
    private final BudgetEvaluator budgetEvaluator;
    private final BudgetStateStore budgetStateStore;
    private final CostCalculator costCalculator;
    private final PricingRegistry pricingRegistry;
    private final MissingPricingPolicy missingPricingPolicy;

    public DefaultLedgerAdvisor(LedgerManager ledgerManager, UsageExtractor usageExtractor) {
        this(ledgerManager, usageExtractor, null, null, null, null);
    }

    public DefaultLedgerAdvisor(LedgerManager ledgerManager, UsageExtractor usageExtractor,
                                BudgetEvaluator budgetEvaluator, BudgetStateStore budgetStateStore,
                                CostCalculator costCalculator, PricingRegistry pricingRegistry) {
        this(
                ledgerManager,
                usageExtractor,
                budgetEvaluator,
                budgetStateStore,
                costCalculator,
                pricingRegistry,
                MissingPricingPolicy.FAIL_OPEN
        );
    }

    public DefaultLedgerAdvisor(LedgerManager ledgerManager, UsageExtractor usageExtractor,
                                BudgetEvaluator budgetEvaluator, BudgetStateStore budgetStateStore,
                                CostCalculator costCalculator, PricingRegistry pricingRegistry,
                                MissingPricingPolicy missingPricingPolicy) {
        this.ledgerManager = ledgerManager;
        this.usageExtractor = usageExtractor;
        this.budgetEvaluator = budgetEvaluator;
        this.budgetStateStore = budgetStateStore;
        this.costCalculator = costCalculator;
        this.pricingRegistry = pricingRegistry;
        this.missingPricingPolicy = Objects.requireNonNull(
                missingPricingPolicy,
                "missingPricingPolicy must not be null"
        );
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        ChatClientRequest resolvedRequest = request;

        if (budgetEvaluator != null) {
            Map<String, String> tags = extractTags(request.context());
            BudgetDecision decision = budgetEvaluator.evaluate(tags);
            resolvedRequest = resolvedRequest.mutate()
                          .context(BUDGET_DECISION_CONTEXT, decision)
                          .build();
        }

        if (pricingRegistry != null) {
            resolvedRequest = resolvePricing(resolvedRequest);
        }

        return resolvedRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        TokenUsage usage = usageExtractor.extract(response);
        
        String modelId = extractModelId(response);
        String responseModelId = extractResponseModelId(response);
        Map<String, String> tags = extractTags(response);

        Optional<PricingSnapshot> snapshot = extractPricingSnapshot(response);
        boolean hasPricingResolution = hasPricingResolution(response);
        if (snapshot.isPresent()) {
            if (!snapshot.get().modelId().equals(responseModelId)) {
                return withReconciliationResult(response, PricingReconciliationResult.RECONCILIATION_REQUIRED);
            }

            Cost cost = ledgerManager.record(snapshot.get(), usage, tags);
            ChatClientResponse reconciledResponse = withReconciliationResult(
                    response,
                    PricingReconciliationResult.RECONCILED
            );
            if (budgetStateStore != null) {
                BudgetDecision decision = extractBudgetDecision(reconciledResponse);
                budgetStateStore.addCost(
                    decision.key(),
                    decision.limit(),
                    cost
                );
            }
            return reconciledResponse;
        } else if (!hasPricingResolution) {
            ledgerManager.record(modelId, usage, tags);
            recordLegacyBudgetCost(modelId, usage, response);
        } else {
            return withReconciliationResult(response, PricingReconciliationResult.UNPRICED);
        }

        return response;
    }

    private ChatClientResponse withReconciliationResult(
            ChatClientResponse response,
            PricingReconciliationResult result
    ) {
        Map<String, Object> context = new HashMap<>();
        if (response.context() != null) {
            context.putAll(response.context());
        }
        context.put(PRICING_RECONCILIATION_RESULT_CONTEXT, result);
        return new ChatClientResponse(response.chatResponse(), context);
    }

    private void recordLegacyBudgetCost(String modelId, TokenUsage usage, ChatClientResponse response) {
        if (budgetStateStore == null || costCalculator == null || pricingRegistry == null) {
            return;
        }

        Optional<PricingPlan> plan = pricingRegistry.getPlan(modelId);
        if (plan.isEmpty()) {
            return;
        }

        Cost cost = costCalculator.calculate(usage, plan.get());
        BudgetDecision decision = extractBudgetDecision(response);
        budgetStateStore.addCost(
            decision.key(),
            decision.limit(),
            cost
        );
    }

    private ChatClientRequest resolvePricing(ChatClientRequest request) {
        String modelId = extractModelId(request);
        if (modelId == null) {
            rejectMissingPricingIfFailClosed(PricingResolution.MISSING_PLAN);
            return request;
        }

        String pricingPolicyId = extractPricingPolicyId(request);
        Optional<PricingSnapshot> snapshot = pricingRegistry.resolveSnapshot(modelId, pricingPolicyId);
        PricingResolution resolution = resolvePricingResolution(request, snapshot);
        rejectMissingPricingIfFailClosed(resolution);

        return withPricingContext(request, pricingPolicyId, resolution, snapshot);
    }

    private PricingResolution resolvePricingResolution(
            ChatClientRequest request,
            Optional<PricingSnapshot> snapshot
    ) {
        if (snapshot.isEmpty()) {
            return PricingResolution.MISSING_PLAN;
        }

        Optional<TokenType> requiredTokenType = extractRequiredTokenType(request);
        if (requiredTokenType.isEmpty()) {
            return PricingResolution.RESOLVED;
        }

        return resolveSnapshotRate(snapshot.get(), requiredTokenType.get());
    }

    private ChatClientRequest withPricingContext(
            ChatClientRequest request,
            String pricingPolicyId,
            PricingResolution resolution,
            Optional<PricingSnapshot> snapshot
    ) {
        ChatClientRequest.Builder builder = request.mutate()
                .context(PRICING_POLICY_ID_CONTEXT, pricingPolicyId)
                .context(PRICING_RESOLUTION_CONTEXT, resolution);
        if (resolution.isResolved()) {
            snapshot.ifPresent(value -> builder.context(PRICING_SNAPSHOT_CONTEXT, value));
        }
        return builder.build();
    }

    private PricingResolution resolveSnapshotRate(PricingSnapshot snapshot, TokenType tokenType) {
        PricingPlan plan = new PricingPlan(
                snapshot.modelId(),
                snapshot.pricingPolicyId(),
                snapshot.rates(),
                snapshot.currency()
        );
        return plan.resolveRate(tokenType);
    }

    private void rejectMissingPricingIfFailClosed(PricingResolution resolution) {
        if (missingPricingPolicy != MissingPricingPolicy.FAIL_CLOSED) {
            return;
        }
        if (resolution.isResolved()) {
            return;
        }
        throw new MissingPricingException(resolution);
    }

    private String extractModelId(ChatClientRequest request) {
        Object value = request.context().get(MODEL_ID_CONTEXT);
        if (value instanceof String modelId && !modelId.isBlank()) {
            return modelId;
        }
        return null;
    }

    private String extractModelId(ChatClientResponse response) {
        Map<String, Object> context = response.context();
        Object value = null;
        if (context != null) {
            value = context.get(MODEL_ID_CONTEXT);
        }

        if (value instanceof String modelId && !modelId.isBlank()) {
            return modelId;
        }

        if (response.chatResponse() != null && response.chatResponse().getMetadata() != null) {
            String model = response.chatResponse().getMetadata().getModel();
            if (model != null && !model.isBlank()) {
                return model;
            }
        }
        return "unknown-model";
    }

    private String extractResponseModelId(ChatClientResponse response) {
        if (response.chatResponse() != null && response.chatResponse().getMetadata() != null) {
            String model = response.chatResponse().getMetadata().getModel();
            if (model != null && !model.isBlank()) {
                return model;
            }
        }
        return extractModelId(response);
    }

    private String extractPricingPolicyId(ChatClientRequest request) {
        Object value = request.context().get(PRICING_POLICY_ID_CONTEXT);
        if (value instanceof String pricingPolicyId && !pricingPolicyId.isBlank()) {
            return pricingPolicyId;
        }
        return PricingPlan.DEFAULT_PRICING_POLICY_ID;
    }

    private Optional<TokenType> extractRequiredTokenType(ChatClientRequest request) {
        Object value = request.context().get(REQUIRED_TOKEN_TYPE_CONTEXT);
        if (value instanceof TokenType tokenType) {
            return Optional.of(tokenType);
        }
        return Optional.empty();
    }

    private Optional<PricingSnapshot> extractPricingSnapshot(ChatClientResponse response) {
        Map<String, Object> context = response.context();
        Object value = null;
        if (context != null) {
            value = context.get(PRICING_SNAPSHOT_CONTEXT);
        }

        if (value instanceof PricingSnapshot snapshot) {
            return Optional.of(snapshot);
        }
        return Optional.empty();
    }

    private boolean hasPricingResolution(ChatClientResponse response) {
        Map<String, Object> context = response.context();
        if (context == null) {
            return false;
        }
        return context.get(PRICING_RESOLUTION_CONTEXT) instanceof PricingResolution;
    }

    private Map<String, String> extractTags(ChatClientResponse response) {
        return extractTags(response.context());
    }

    private BudgetDecision extractBudgetDecision(ChatClientResponse response) {
        Map<String, Object> context = response.context();
        Object value = null;
        if (context != null) {
            value = context.get(BUDGET_DECISION_CONTEXT);
        }

        if (value instanceof BudgetDecision decision) {
            return decision;
        }
        throw new IllegalStateException("Resolved budget decision is missing from response context");
    }

    private Map<String, String> extractTags(Map<String, Object> context) {
        Map<String, String> tags = new HashMap<>();
        if (context == null) {
            return tags;
        }

        context.forEach((k, v) -> {
            if (v instanceof String s) {
                tags.put(k, s);
            }
        });
        return tags;
    }
}
