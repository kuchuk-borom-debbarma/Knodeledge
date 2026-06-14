package dev.kuku.knodeledge.controllers;

import dev.kuku.knodeledge.infra.topo_tracer.KnodeledgeImportanceLevel;
import dev.kuku.knodeledge.infra.topo_tracer.Traced;

import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.GraphResponse;
import dev.kuku.knodeledge.services.graph.GraphService;
import dev.kuku.topotracer.sdk.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/graph")
@RequiredArgsConstructor
public class GraphController {
    private final GraphService graphService;
    private final dev.kuku.knodeledge.repositories.internal.InMemoryGraphRepository inMemoryGraphRepository;
    private final dev.kuku.knodeledge.repositories.internal.InMemoryContextBoundaryRepository inMemoryContextBoundaryRepository;
    private final Tracer tracer;

    @GetMapping("/debug-all")
    public ResponseEntity<Object> getDebugAll() {
        return ResponseEntity.ok(java.util.Map.of(
            "nodes", inMemoryGraphRepository.getAllNodesDebug(),
            "edges", inMemoryGraphRepository.getAllEdgesDebug(),
            "boundaries", inMemoryContextBoundaryRepository.getAllStoreDebug()
        ));
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
