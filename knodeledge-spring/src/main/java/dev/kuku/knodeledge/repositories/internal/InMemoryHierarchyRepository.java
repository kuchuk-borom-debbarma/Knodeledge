package dev.kuku.knodeledge.repositories.internal;

import dev.kuku.knodeledge.repositories.HierarchyRepository;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.BoundaryHierarchy;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryHierarchyRepository implements HierarchyRepository {
    private final ConcurrentHashMap<String, BoundaryHierarchy> hierarchies =
        new ConcurrentHashMap<>();

    @Override
    public Optional<BoundaryHierarchy> findByBoundaryId(String boundaryId) {
        return Optional.ofNullable(hierarchies.get(boundaryId));
    }

    @Override
    public void save(BoundaryHierarchy hierarchy) {
        hierarchies.put(hierarchy.boundaryId(), hierarchy);
    }
}
