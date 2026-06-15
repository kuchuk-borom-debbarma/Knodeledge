package dev.kuku.knodeledge.services.community.internal;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class BoundaryLockManager {
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(String boundaryId, Supplier<T> operation) {
        var lock = locks.computeIfAbsent(boundaryId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }

    public void withLock(String boundaryId, Runnable operation) {
        withLock(boundaryId, () -> {
            operation.run();
            return null;
        });
    }
}
