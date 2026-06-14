package dev.kuku.knodeledge.services.graph.internal;

import dev.kuku.knodeledge.repositories.GraphRepository;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.GraphResponse;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.NodeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.EdgeDto;
import dev.kuku.knodeledge.services.graph.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InMemoryGraphService implements GraphService {
    private final GraphRepository graphRepository;

    @Override
    public GraphResponse getCompleteGraphByBoundaryId(String contextBoundaryId, String userId) {
        var nodes = graphRepository.findNodesByBoundaryId(contextBoundaryId);
        var edges = graphRepository.findEdgesByBoundaryId(contextBoundaryId);
        return new GraphResponse(nodes, edges);
    }

    @Override
    public void saveGraph(String contextBoundaryId, List<NodeDto> nodes, List<EdgeDto> edges) {
        graphRepository.saveNodes(contextBoundaryId, nodes);
        graphRepository.saveEdges(contextBoundaryId, edges);
    }
}
