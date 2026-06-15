package dev.kuku.knodeledge.services.graph.internal;

import dev.kuku.knodeledge.repositories.GraphRepository;
import dev.kuku.knodeledge.repositories.ContextBoundaryRepository;
import dev.kuku.knodeledge.infra.topo_tracer.KnodeledgeImportanceLevel;
import dev.kuku.knodeledge.infra.topo_tracer.Traced;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.GraphResponse;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.NodeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.EdgeDto;
import dev.kuku.knodeledge.services.graph.GraphService;
import dev.kuku.knodeledge.services.graph.dto.DebugGraphResponse;
import dev.kuku.knodeledge.services.graph.dto.DebugGraphResponse.BoundaryGraph;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class InMemoryGraphService implements GraphService {
    private final GraphRepository graphRepository;
    private final ContextBoundaryRepository contextBoundaryRepository;

    @Override
    @Traced(
        value = "graph.load-complete",
        type = KnodeledgeImportanceLevel.SERVICE)
    public GraphResponse getCompleteGraphByBoundaryId(String contextBoundaryId, String userId) {
        var nodes = graphRepository.findNodesByBoundaryId(contextBoundaryId);
        var edges = graphRepository.findEdgesByBoundaryId(contextBoundaryId);
        return new GraphResponse(nodes, edges);
    }

    @Override
    public DebugGraphResponse getAllGraphsDebug() {
        var storedGraphs = graphRepository.findAllGraphs();
        var graphs = contextBoundaryRepository.findAll().stream()
            .map(boundary -> {
                var graph = storedGraphs.getOrDefault(
                    boundary.id(),
                    new GraphResponse(List.of(), List.of())
                );
                return new BoundaryGraph(
                    boundary.id(),
                    boundary.name(),
                    boundary.context(),
                    boundary.userId(),
                    graph.nodes(),
                    graph.edges()
                );
            })
            .sorted(Comparator.comparing(BoundaryGraph::name))
            .toList();

        return new DebugGraphResponse(
            graphs.size(),
            graphs.stream().mapToInt(graph -> graph.nodes().size()).sum(),
            graphs.stream().mapToInt(graph -> graph.edges().size()).sum(),
            graphs
        );
    }

    @Override
    public void saveGraph(String contextBoundaryId, List<NodeDto> nodes, List<EdgeDto> edges) {
        graphRepository.saveNodes(contextBoundaryId, nodes);
        graphRepository.saveEdges(contextBoundaryId, edges);
    }
}
