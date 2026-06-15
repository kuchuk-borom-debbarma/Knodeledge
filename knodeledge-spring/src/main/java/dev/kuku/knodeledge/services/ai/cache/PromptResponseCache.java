package dev.kuku.knodeledge.services.ai.cache;

import java.util.function.Supplier;

public interface PromptResponseCache {
    <T> T getOrCompute(String key, Supplier<T> loader);
    int size();
    void clear();
}
