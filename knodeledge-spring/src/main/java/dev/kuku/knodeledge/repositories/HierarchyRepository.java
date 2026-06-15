package dev.kuku.knodeledge.repositories;

import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.BoundaryHierarchy;

import java.util.Optional;

public interface HierarchyRepository {
    Optional<BoundaryHierarchy> findByBoundaryId(String boundaryId);
    void save(BoundaryHierarchy hierarchy);
}
