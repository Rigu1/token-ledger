package io.tokenpilot.springai.internal;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.Objects;
import java.util.OptionalLong;

/** request maxTokens를 configured default 순서로 해석합니다. */
final class ReservedOutputResolver {

    private final OptionalLong defaultReservedOutputTokens;

    ReservedOutputResolver() {
        this.defaultReservedOutputTokens = OptionalLong.empty();
    }

    ReservedOutputResolver(long defaultReservedOutputTokens) {
        this.defaultReservedOutputTokens = positiveTokens(defaultReservedOutputTokens);
    }

    OptionalLong resolve(ChatClientRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        Integer requestMaxTokens = requestMaxTokens(request);
        if (requestMaxTokens != null) {
            return positiveTokens(requestMaxTokens);
        }
        return defaultReservedOutputTokens;
    }

    private OptionalLong positiveTokens(long tokens) {
        if (tokens <= 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(tokens);
    }

    private @Nullable Integer requestMaxTokens(ChatClientRequest request) {
        ChatOptions options = request.prompt().getOptions();
        if (options == null) {
            return null;
        }
        return options.getMaxTokens();
    }
}
