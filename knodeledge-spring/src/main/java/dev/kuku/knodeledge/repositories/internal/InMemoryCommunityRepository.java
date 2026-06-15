package dev.kuku.knodeledge.repositories.internal;

import dev.kuku.knodeledge.repositories.CommunityRepository;
import dev.kuku.knodeledge.services.community.model.CommunityModels.CommunityHierarchy;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryCommunityRepository implements CommunityRepository {
    private final ConcurrentHashMap<String, CommunityHierarchy> hierarchies =
        new ConcurrentHashMap<>();

    @Override
    public Optional<CommunityHierarchy> findByBoundaryId(String contextBoundaryId) {
        return Optional.ofNullable(hierarchies.get(contextBoundaryId));
    }

    @Override
    public void save(String contextBoundaryId, CommunityHierarchy hierarchy) {
        hierarchies.put(contextBoundaryId, hierarchy);
    }
}
