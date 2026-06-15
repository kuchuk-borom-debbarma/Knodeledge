package dev.kuku.knodeledge.services.ai.internal;

import dev.kuku.knodeledge.infra.topo_tracer.KnodeledgeImportanceLevel;
import dev.kuku.knodeledge.infra.topo_tracer.Traced;
import dev.kuku.knodeledge.services.ai.AIService;
import dev.kuku.knodeledge.services.hierarchy.HierarchyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LLMAIService implements AIService {
    private final HierarchyService hierarchyService;

    @Override
    @Traced(
        value = "ai.ingest-note",
        type = KnodeledgeImportanceLevel.SERVICE,
        includeArguments = true,
        maxArgumentLength = 160)
    public void ingestNote(String note, String contextBoundaryId, String actorId) {
        hierarchyService.ingest(note, contextBoundaryId, actorId);
    }

    @Override
    @Traced(
        value = "ai.prompt-hierarchy",
        type = KnodeledgeImportanceLevel.SERVICE,
        includeArguments = true,
        maxArgumentLength = 160)
    public String promptGraph(String prompt, String contextBoundaryId, String actorId) {
        return hierarchyService.answer(prompt, contextBoundaryId, actorId);
    }
}
