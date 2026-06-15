package dev.kuku.knodeledge.services.ai.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

@Component
public class InMemoryPromptResponseCache implements PromptResponseCache {
    private final ConcurrentHashMap<String, CompletableFuture<Object>> entries =
        new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> insertionOrder = new ConcurrentLinkedQueue<>();
    private final boolean enabled;
    private final int maxEntries;

    public InMemoryPromptResponseCache(
        @Value("${knodeledge.ai.prompt-cache.enabled:true}") boolean enabled,
        @Value("${knodeledge.ai.prompt-cache.max-entries:1000}") int maxEntries
    ) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("Prompt cache max entries must be positive");
        }
        this.enabled = enabled;
        this.maxEntries = maxEntries;
    }

    @Override
    public <T> T getOrCompute(String key, Supplier<T> loader) {
        if (!enabled) {
            return requireValue(loader.get());
        }

        var created = new CompletableFuture<Object>();
        var existing = entries.putIfAbsent(key, created);
        if (existing != null) {
            return await(existing);
        }

        try {
            T value = requireValue(loader.get());
            created.complete(value);
            insertionOrder.add(key);
            evictOverflow();
            return value;
        } catch (RuntimeException | Error error) {
            created.completeExceptionally(error);
            entries.remove(key, created);
            throw error;
        }
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public void clear() {
        entries.clear();
        insertionOrder.clear();
    }

    private void evictOverflow() {
        while (entries.size() > maxEntries) {
            String oldest = insertionOrder.poll();
            if (oldest == null) {
                return;
            }
            entries.remove(oldest);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T await(CompletableFuture<Object> future) {
        try {
            return (T) future.join();
        } catch (CompletionException error) {
            if (error.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (error.getCause() instanceof Error cause) {
                throw cause;
            }
            throw error;
        }
    }

    private <T> T requireValue(T value) {
        if (value == null) {
            throw new IllegalStateException("Prompt response must not be null");
        }
        return value;
    }
}
