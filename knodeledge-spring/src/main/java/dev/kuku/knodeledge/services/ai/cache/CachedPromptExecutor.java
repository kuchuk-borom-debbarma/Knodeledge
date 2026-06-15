package dev.kuku.knodeledge.services.ai.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@RequiredArgsConstructor
public class CachedPromptExecutor {
    private final ChatClient chatClient;
    private final PromptResponseCache cache;

    @Value("${spring.ai.openai.chat.options.model:unknown-model}")
    private String model;

    public String text(String stage, String systemPrompt, String userPrompt) {
        String key = cacheKey(stage, systemPrompt, userPrompt, String.class);
        return cache.getOrCompute(
            key,
            () -> {
                String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
                if (response == null || response.isBlank()) {
                    throw new IllegalStateException(
                        "Prompt stage returned an empty response: " + stage
                    );
                }
                return response;
            }
        );
    }

    public <T> T entity(
        String stage,
        String systemPrompt,
        String userPrompt,
        Class<T> responseType
    ) {
        String key = cacheKey(stage, systemPrompt, userPrompt, responseType);
        return cache.getOrCompute(
            key,
            () -> chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(responseType)
        );
    }

    private String cacheKey(
        String stage,
        String systemPrompt,
        String userPrompt,
        Class<?> responseType
    ) {
        String input = String.join(
            "\u0000",
            model,
            stage,
            responseType.getName(),
            systemPrompt,
            userPrompt
        );
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
