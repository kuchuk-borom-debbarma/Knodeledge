package dev.kuku.knodeledge.repositories;

import dev.kuku.knodeledge.services.community.model.CommunityModels.CommunityHierarchy;

import java.util.Optional;

public interface CommunityRepository {
    Optional<CommunityHierarchy> findByBoundaryId(String contextBoundaryId);
    void save(String contextBoundaryId, CommunityHierarchy hierarchy);
}
