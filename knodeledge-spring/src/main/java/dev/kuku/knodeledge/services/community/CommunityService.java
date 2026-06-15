package dev.kuku.knodeledge.services.community;

import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.GraphResponse;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.GraphPatch;
import dev.kuku.knodeledge.services.community.model.CommunityModels.CommunityHierarchy;
import dev.kuku.knodeledge.services.community.model.CommunityModels.RetrievalResult;

public interface CommunityService {

    record RetrievalPackage(
        CommunityHierarchy hierarchy,
        RetrievalResult retrieval,
        GraphResponse fullGraph
    ) {}

    RetrievalPackage prepare(String query, String contextBoundaryId, String actorId);

    CommunityHierarchy getHierarchy(String contextBoundaryId, String actorId);

    CommunityHierarchy prepareUpdate(
        String contextBoundaryId,
        CommunityHierarchy hierarchy,
        RetrievalResult retrieval,
        GraphPatch patch,
        GraphResponse finalGraph
    );

    void saveHierarchy(String contextBoundaryId, CommunityHierarchy hierarchy);
}
