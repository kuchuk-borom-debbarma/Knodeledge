package dev.kuku.knodeledge.services.hierarchy;

import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.BoundaryHierarchy;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyLevelResponse;

public interface HierarchyService {
    void ingest(String note, String boundaryId, String actorId);
    String answer(String prompt, String boundaryId, String actorId);
    HierarchyLevelResponse getLevel(String boundaryId, String actorId, String nodeId);
    BoundaryHierarchy getDebugHierarchy(String boundaryId, String actorId);
}
