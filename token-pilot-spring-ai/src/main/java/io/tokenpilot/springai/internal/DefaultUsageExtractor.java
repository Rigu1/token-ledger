package io.tokenpilot.springai.internal;

import io.tokenpilot.core.domain.TokenUsage;
import io.tokenpilot.core.domain.TokenUsageDetails;
import io.tokenpilot.springai.UsageExtractor;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * 기본 {@link UsageExtractor} 구현체.
 * Spring AI의 {@link Usage} 정보를 {@link TokenUsage}로 변환하며,
 * 메타데이터에 포함된 추론(Reasoning) 토큰 등을 식별하여 세분화된 사용량을 추출합니다.
 */
public class DefaultUsageExtractor implements UsageExtractor {

    @Override
    public TokenUsage extract(ChatClientResponse response) {
        if (response == null) {
            return TokenUsage.from(0, 0);
        }

        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return TokenUsage.from(0, 0);
        }

        ChatResponseMetadata metadata = chatResponse.getMetadata();
        Usage usage = metadata.getUsage();
        if (usage == null) {
            return new TokenUsage(0, 0, new TokenUsageDetails(0, 0, 0), copyMetadata(metadata, null));
        }

        long prompt = (usage.getPromptTokens() != null) ? usage.getPromptTokens() : 0L;
        long completion = (usage.getCompletionTokens() != null) ? usage.getCompletionTokens() : 0L;

        Long reasoning = extractReasoningTokens(metadata);

        return new TokenUsage(
                prompt,
                completion,
                new TokenUsageDetails(0, reasoning, 0),
                copyMetadata(metadata, usage.getNativeUsage())
        );
    }

    private Long extractReasoningTokens(ChatResponseMetadata metadata) {
        // metadata는 직접 Map이 아닐 수 있으므로 get 메서드로 개별 접근
        Object details = metadata.get("completion_tokens_details");
        if (details instanceof Map<?, ?> detailsMap) {
            Object reasoning = detailsMap.get("reasoning_tokens");
            if (reasoning instanceof Number num) {
                return num.longValue();
            }
        }
        
        Object directReasoning = metadata.get("reasoning_tokens");
        if (directReasoning instanceof Number num) {
            return num.longValue();
        }

        return 0L;
    }

    private Map<String, Object> copyMetadata(ChatResponseMetadata metadata, Object nativeUsage) {
        Map<String, Object> metadataMap = new HashMap<>();
        metadata.keySet().forEach(key -> metadataMap.put(key, metadata.get(key)));
        if (nativeUsage != null) {
            metadataMap.put("nativeUsage", nativeUsage);
        }
        return metadataMap;
    }
}
