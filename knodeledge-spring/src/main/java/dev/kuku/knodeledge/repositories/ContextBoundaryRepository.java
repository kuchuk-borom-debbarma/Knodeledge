package dev.kuku.knodeledge.repositories;

import dev.kuku.knodeledge.services.context_boundary.dto.ContextBoundary;
import java.util.List;
import java.util.Optional;

public interface ContextBoundaryRepository {
    ContextBoundary save(ContextBoundary boundary);
    Optional<ContextBoundary> findById(String id);
    List<ContextBoundary> findByUserId(String userId);
    List<ContextBoundary> findAll();
}
