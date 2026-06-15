package dev.kuku.knodeledge.services.ai.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPromptResponseCacheTests {

    @Test
    void reusesSuccessfulResponse() {
        var cache = new InMemoryPromptResponseCache(true, 10);
        var calls = new AtomicInteger();

        String first = cache.getOrCompute("prompt", () -> "response-" + calls.incrementAndGet());
        String second = cache.getOrCompute("prompt", () -> "response-" + calls.incrementAndGet());

        assertEquals("response-1", first);
        assertEquals(first, second);
        assertEquals(1, calls.get());
        assertEquals(1, cache.size());
    }

    @Test
    void coalescesConcurrentLoadsForSamePrompt() throws Exception {
        var cache = new InMemoryPromptResponseCache(true, 10);
        var calls = new AtomicInteger();
        var loaderStarted = new CountDownLatch(1);
        var releaseLoader = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> cache.getOrCompute("prompt", () -> {
                calls.incrementAndGet();
                loaderStarted.countDown();
                await(releaseLoader);
                return "response";
            }));
            assertTrue(loaderStarted.await(1, TimeUnit.SECONDS));

            var second = executor.submit(
                () -> cache.getOrCompute("prompt", () -> "unexpected")
            );
            releaseLoader.countDown();

            assertEquals("response", first.get(1, TimeUnit.SECONDS));
            assertEquals("response", second.get(1, TimeUnit.SECONDS));
        }
        assertEquals(1, calls.get());
    }

    @Test
    void doesNotCacheFailures() {
        var cache = new InMemoryPromptResponseCache(true, 10);
        var calls = new AtomicInteger();

        assertThrows(IllegalStateException.class, () ->
            cache.getOrCompute("prompt", () -> {
                calls.incrementAndGet();
                throw new IllegalStateException("provider failed");
            })
        );
        String response = cache.getOrCompute("prompt", () -> {
            calls.incrementAndGet();
            return "recovered";
        });

        assertEquals("recovered", response);
        assertEquals(2, calls.get());
    }

    @Test
    void evictsOldestResponseWhenBoundExceeded() {
        var cache = new InMemoryPromptResponseCache(true, 2);
        var calls = new AtomicInteger();

        cache.getOrCompute("first", () -> "first-" + calls.incrementAndGet());
        cache.getOrCompute("second", () -> "second-" + calls.incrementAndGet());
        cache.getOrCompute("third", () -> "third-" + calls.incrementAndGet());
        String reloaded = cache.getOrCompute("first", () -> "first-" + calls.incrementAndGet());

        assertEquals("first-4", reloaded);
        assertEquals(2, cache.size());
    }

    @Test
    void bypassesCacheWhenDisabled() {
        var cache = new InMemoryPromptResponseCache(false, 10);
        var calls = new AtomicInteger();

        cache.getOrCompute("prompt", calls::incrementAndGet);
        cache.getOrCompute("prompt", calls::incrementAndGet);

        assertEquals(2, calls.get());
        assertEquals(0, cache.size());
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test latch");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test latch", error);
        }
    }
}
