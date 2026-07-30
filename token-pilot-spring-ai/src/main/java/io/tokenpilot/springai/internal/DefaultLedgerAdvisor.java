package io.tokenpilot.springai.internal;

import io.tokenpilot.budget.BudgetDecision;
import io.tokenpilot.budget.BudgetEvaluator;
import io.tokenpilot.budget.BudgetStateStore;
import io.tokenpilot.core.*;
import io.tokenpilot.core.domain.*;
import io.tokenpilot.springai.LedgerAdvisor;
import io.tokenpilot.springai.UsageExtractor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;

import java.util.HashMap;
import java.util.Map;
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
    static final String PRICING_PLAN_CONTEXT = "tokenpilot.pricing.plan";
    static final String PRICING_RESOLUTION_CONTEXT = "tokenpilot.pricing.resolution";

    private final LedgerManager ledgerManager;
    private final UsageExtractor usageExtractor;
    private final BudgetEvaluator budgetEvaluator;
    private final BudgetStateStore budgetStateStore;
    private final PricingRegistry pricingRegistry;

    public DefaultLedgerAdvisor(LedgerManager ledgerManager, UsageExtractor usageExtractor) {
        this(ledgerManager, usageExtractor, null, null, null, null);
    }

    public DefaultLedgerAdvisor(LedgerManager ledgerManager, UsageExtractor usageExtractor,
                                BudgetEvaluator budgetEvaluator, BudgetStateStore budgetStateStore,
                                CostCalculator costCalculator, PricingRegistry pricingRegistry) {
        this.ledgerManager = ledgerManager;
        this.usageExtractor = usageExtractor;
        this.budgetEvaluator = budgetEvaluator;
        this.budgetStateStore = budgetStateStore;
        this.pricingRegistry = pricingRegistry;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        ChatClientRequest resolvedRequest = request;

        if (budgetEvaluator != null) {
            Map<String, String> tags = extractTagsFromRequest(request);
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
        Map<String, String> tags = extractTags(response);

        Cost cost = null;
        Optional<PricingPlan> plan = extractPricingPlan(response);
        if (plan.isPresent()) {
            cost = ledgerManager.record(plan.get(), usage, tags);
        } else if (!hasPricingResolution(response)) {
            cost = ledgerManager.record(modelId, usage, tags);
        }

        // 예산 누적 처리
        if (budgetStateStore != null && cost != null) {
            BudgetDecision decision = extractBudgetDecision(response);
            budgetStateStore.addCost(
                decision.key(),
                decision.limit(),
                cost
            );
        }

        return response;
    }

    private ChatClientRequest resolvePricing(ChatClientRequest request) {
        String modelId = extractModelId(request);
        if (modelId == null) {
            return request;
        }

        String pricingPolicyId = extractPricingPolicyId(request);
        Optional<PricingPlan> plan = pricingRegistry.getPlan(modelId, pricingPolicyId);
        PricingResolution resolution = plan.isPresent()
                ? PricingResolution.RESOLVED
                : PricingResolution.MISSING_PLAN;

        ChatClientRequest.Builder builder = request.mutate()
                .context(PRICING_POLICY_ID_CONTEXT, pricingPolicyId)
                .context(PRICING_RESOLUTION_CONTEXT, resolution);
        plan.ifPresent(value -> builder.context(PRICING_PLAN_CONTEXT, value));
        return builder.build();
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
        Object value = context == null ? null : context.get(MODEL_ID_CONTEXT);
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

    private String extractPricingPolicyId(ChatClientRequest request) {
        Object value = request.context().get(PRICING_POLICY_ID_CONTEXT);
        if (value instanceof String pricingPolicyId && !pricingPolicyId.isBlank()) {
            return pricingPolicyId;
        }
        return PricingPlan.DEFAULT_PRICING_POLICY_ID;
    }

    private Optional<PricingPlan> extractPricingPlan(ChatClientResponse response) {
        Map<String, Object> context = response.context();
        Object value = context == null ? null : context.get(PRICING_PLAN_CONTEXT);
        if (value instanceof PricingPlan plan) {
            return Optional.of(plan);
        }
        return Optional.empty();
    }

    private boolean hasPricingResolution(ChatClientResponse response) {
        Map<String, Object> context = response.context();
        return context != null && context.get(PRICING_RESOLUTION_CONTEXT) instanceof PricingResolution;
    }

    private Map<String, String> extractTags(ChatClientResponse response) {
        Map<String, String> tags = new HashMap<>();
        
        Map<String, Object> context = response.context();
        if (context != null) {
            context.forEach((k, v) -> {
                if (v instanceof String s) {
                    tags.put(k, s);
                }
            });
        }

        return tags;
    }

    private BudgetDecision extractBudgetDecision(ChatClientResponse response) {
        Map<String, Object> context = response.context();
        Object value = context == null ? null : context.get(BUDGET_DECISION_CONTEXT);
        if (value instanceof BudgetDecision decision) {
            return decision;
        }
        throw new IllegalStateException("Resolved budget decision is missing from response context");
    }

    private Map<String, String> extractTagsFromRequest(ChatClientRequest request) {
        Map<String, String> tags = new HashMap<>();
        Map<String, Object> context = request.context();
        if (context != null) {
            context.forEach((k, v) -> {
                if (v instanceof String s) {
                    tags.put(k, s);
                }
            });
        }
        return tags;
    }
}
