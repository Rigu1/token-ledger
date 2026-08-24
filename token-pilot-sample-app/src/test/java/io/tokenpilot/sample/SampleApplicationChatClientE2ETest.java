package io.tokenpilot.sample;

import io.tokenpilot.budget.ActualUsageCommand;
import io.tokenpilot.budget.BudgetDecision;
import io.tokenpilot.budget.BudgetEvaluator;
import io.tokenpilot.budget.BudgetReservationRequest;
import io.tokenpilot.budget.BudgetSnapshot;
import io.tokenpilot.budget.BudgetStateStore;
import io.tokenpilot.budget.ReservationAccounting;
import io.tokenpilot.budget.ReservationAccountingReason;
import io.tokenpilot.budget.ReservationId;
import io.tokenpilot.budget.internal.LedgerBudgetComponents;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

@SpringBootTest(properties = {
        "token-pilot.enabled=true",
        "token-pilot.pricing.plans[0].model-id=gpt-4o-2024-08-06",
        "token-pilot.pricing.plans[0].currency=USD",
        "token-pilot.pricing.plans[0].rates.PROMPT=0.00015",
        "token-pilot.pricing.plans[0].rates.COMPLETION=0.00060",
        "token-pilot.budget.enabled=true",
        "token-pilot.budget.monthly-limit=10.00",
        "token-pilot.budget.currency=USD",
        "token-pilot.budget.target-tag-key=tenant_id"
})
class SampleApplicationChatClientE2ETest {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private ProviderProbe providerProbe;

    @Autowired
    private BudgetStateStore stateStoreProbe;

    @Autowired
    private BudgetEvaluator budgetEvaluator;

    @Test
    void providerAndAccountingLifecycleRunOnce() {
        ChatClientResponse response = chatClientBuilder.clone()
                .build()
                .prompt()
                .user("Record this fake budget-aware Spring AI call.")
                .advisors(advisors -> advisors
                        .param("tenant_id", "budget-chat-tenant")
                        .param("tokenpilot.request.id", "request-1")
                        .param("tokenpilot.attempt.id", "attempt-1"))
                .call()
                .chatClientResponse();

        BudgetDecision decision = budgetEvaluator.evaluate(
                Map.of("tenant_id", "budget-chat-tenant")
        );
        BudgetSnapshot snapshot = stateStoreProbe.snapshot(
                decision.key(),
                decision.limit()
        );
        ReservationAccounting accountingProbe = (ReservationAccounting) stateStoreProbe;

        assertThat(response.chatResponse().getResult().getOutput().getText())
                .isEqualTo("fake chat response");
        assertThat(providerProbe.invocationCount()).isEqualTo(1);
        verify(stateStoreProbe).checkAndReserve(any(BudgetReservationRequest.class));
        verify(accountingProbe).markInFlight(any(ReservationId.class));
        verify(accountingProbe).commit(any(ActualUsageCommand.class));
        verify(accountingProbe, never()).release(
                any(ReservationId.class),
                any(ReservationAccountingReason.class)
        );
        verify(accountingProbe, never()).markReconciliationRequired(
                any(ReservationId.class),
                any(ReservationAccountingReason.class)
        );
        assertThat(snapshot.committedCost().value()).isEqualByComparingTo("0.00135");
        assertThat(snapshot.activeReservedCost().value()).isZero();
        assertThat(snapshot.pendingReconciliationLiability().value()).isZero();
    }

    static final class ProviderProbe implements ChatModel {
        private final AtomicInteger invocationCount = new AtomicInteger();

        @Override
        public ChatResponse call(Prompt prompt) {
            invocationCount.incrementAndGet();
            return new ChatResponse(
                    List.of(new Generation(new AssistantMessage("fake chat response"))),
                    ChatResponseMetadata.builder()
                            .model("gpt-4o-2024-08-06")
                            .usage(new DefaultUsage(1_000, 2_000))
                            .build()
            );
        }

        @Override
        public ChatOptions getOptions() {
            return ChatOptions.builder()
                    .model("gpt-4o-2024-08-06")
                    .maxTokens(100)
                    .build();
        }

        int invocationCount() {
            return invocationCount.get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeChatClientConfiguration {

        @Bean
        BudgetStateStore accountingProbe() {
            BudgetStateStore delegate = LedgerBudgetComponents.inMemoryBudgetStateStore();
            return mock(
                    BudgetStateStore.class,
                    withSettings()
                            .extraInterfaces(ReservationAccounting.class)
                            .defaultAnswer(delegatesTo(delegate))
            );
        }

        @Bean
        ProviderProbe fakeChatModel() {
            return new ProviderProbe();
        }

        @Bean
        ChatClient.Builder chatClientBuilder(
                ChatModel chatModel,
                ObjectProvider<ChatClientBuilderCustomizer> customizers
        ) {
            ChatClient.Builder builder = ChatClient.builder(chatModel);
            customizers.orderedStream()
                    .forEach(customizer -> customizer.customize(builder));
            return builder;
        }
    }
}
