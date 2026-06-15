package dev.kuku.knodeledge.repositories.internal;

import dev.kuku.knodeledge.repositories.ContextBoundaryRepository;
import dev.kuku.knodeledge.services.context_boundary.dto.ContextBoundary;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryContextBoundaryRepository implements ContextBoundaryRepository {
    private final Map<String, ContextBoundary> store = new ConcurrentHashMap<>();

    @Override
    public ContextBoundary save(ContextBoundary boundary) {
        store.put(boundary.id(), boundary);
        return boundary;
    }

    @Override
    public Optional<ContextBoundary> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<ContextBoundary> findByUserId(String userId) {
        List<ContextBoundary> results = new ArrayList<>();
        for (ContextBoundary cb : store.values()) {
            if (cb.userId().equals(userId)) {
                results.add(cb);
            }
        }
        return results;
    }

    @Override
    public List<ContextBoundary> findAll() {
        return List.copyOf(store.values());
    }
}
