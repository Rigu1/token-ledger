package io.tokenpilot.springai.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReservedOutputResolverTest {

    @Test
    @DisplayName("request maxTokens를 configured default보다 우선한다")
    void prioritizesRequestMaxTokensOverConfiguredDefault() {
        ReservedOutputResolver resolver = new ReservedOutputResolver(4_096);

        assertThat(resolver.resolve(requestWithMaxTokens(1_024)))
                .hasValue(1_024);
    }

    @Test
    @DisplayName("request maxTokens가 없으면 configured default를 사용한다")
    void usesConfiguredDefaultWhenRequestMaxTokensIsAbsent() {
        ReservedOutputResolver resolver = new ReservedOutputResolver(4_096);

        assertThat(resolver.resolve(requestWithoutMaxTokens()))
                .hasValue(4_096);
    }

    @Test
    @DisplayName("request와 유효한 configured default가 모두 없으면 해석하지 못한다")
    void doesNotResolveWithoutRequestOrValidDefault() {
        assertThat(new ReservedOutputResolver().resolve(requestWithoutMaxTokens()))
                .isEmpty();
        assertThat(new ReservedOutputResolver(0).resolve(requestWithoutMaxTokens()))
                .isEmpty();
    }

    @Test
    @DisplayName("0 이하 request maxTokens는 configured default로 대체하지 않는다")
    void doesNotFallbackForInvalidRequestMaxTokens() {
        ReservedOutputResolver resolver = new ReservedOutputResolver(4_096);

        assertThat(resolver.resolve(requestWithMaxTokens(0))).isEmpty();
        assertThat(resolver.resolve(requestWithMaxTokens(-1))).isEmpty();
    }

    private ChatClientRequest requestWithMaxTokens(int maxTokens) {
        return new ChatClientRequest(
                new Prompt(
                        "user question",
                        ChatOptions.builder().maxTokens(maxTokens).build()
                ),
                Map.of()
        );
    }

    private ChatClientRequest requestWithoutMaxTokens() {
        return new ChatClientRequest(new Prompt("user question"), Map.of());
    }
}
