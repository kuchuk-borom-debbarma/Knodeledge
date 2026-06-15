package dev.kuku.knodeledge.controllers;

import dev.kuku.knodeledge.infra.topo_tracer.KnodeledgeImportanceLevel;
import dev.kuku.knodeledge.infra.topo_tracer.Traced;

import dev.kuku.knodeledge.controllers.models.RetrieveGraphBody;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.GraphResponse;
import dev.kuku.knodeledge.services.community.CommunityService;
import dev.kuku.knodeledge.services.community.model.CommunityModels.CommunityHierarchy;
import dev.kuku.knodeledge.services.community.model.CommunityModels.RetrievalResult;
import dev.kuku.knodeledge.services.graph.GraphService;
import dev.kuku.knodeledge.services.graph.dto.DebugGraphResponse;
import dev.kuku.topotracer.sdk.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/graph")
@RequiredArgsConstructor
public class GraphController {
    private final GraphService graphService;
    private final CommunityService communityService;
    private final Tracer tracer;

    @GetMapping("/debug/all")
    @Traced(value = "graph-controller.debug-all", type = KnodeledgeImportanceLevel.CONTROLLER)
    public ResponseEntity<DebugGraphResponse> getDebugAll() {
        return ResponseEntity.ok(graphService.getAllGraphsDebug());
    }

    @GetMapping("/debug/{boundaryId}/hierarchy")
    public ResponseEntity<CommunityHierarchy> getHierarchy(
            @PathVariable String boundaryId,
            @RequestParam String userId) {
        return ResponseEntity.ok(communityService.getHierarchy(boundaryId, userId));
    }

    @PostMapping("/debug/{boundaryId}/retrieve")
    public ResponseEntity<RetrievalResult> retrieve(
            @PathVariable String boundaryId,
            @RequestBody RetrieveGraphBody body) {
        return ResponseEntity.ok(
            communityService.prepare(body.query(), boundaryId, body.userId()).retrieval()
        );
    }

    @GetMapping("/{contextBoundaryId}")
    @Traced(value = "graph-controller.get-graph", type = KnodeledgeImportanceLevel.CONTROLLER)
    public ResponseEntity<GraphResponse> getGraph(
            @PathVariable String contextBoundaryId,
            @RequestParam String userId) {
        tracer.log("Fetching complete graph for boundary " + contextBoundaryId + " and user " + userId);
        GraphResponse graph = graphService.getCompleteGraphByBoundaryId(contextBoundaryId, userId);
        return ResponseEntity.ok(graph);
    }
}
