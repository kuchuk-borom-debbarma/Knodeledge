package dev.kuku.knodeledge.repositories.internal;

import dev.kuku.knodeledge.repositories.GraphRepository;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.NodeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.EdgeDto;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryGraphRepository implements GraphRepository {
    private final Map<String, List<NodeDto>> boundaryNodes = new ConcurrentHashMap<>();
    private final Map<String, List<EdgeDto>> boundaryEdges = new ConcurrentHashMap<>();

    @Override
    public void saveNodes(String contextBoundaryId, List<NodeDto> nodes) {
        boundaryNodes.put(contextBoundaryId, new ArrayList<>(nodes));
    }

    @Override
    public void saveEdges(String contextBoundaryId, List<EdgeDto> edges) {
        boundaryEdges.put(contextBoundaryId, new ArrayList<>(edges));
    }

    @Override
    public List<NodeDto> findNodesByBoundaryId(String contextBoundaryId) {
        return boundaryNodes.getOrDefault(contextBoundaryId, List.of());
    }

    @Override
    public List<EdgeDto> findEdgesByBoundaryId(String contextBoundaryId) {
        return boundaryEdges.getOrDefault(contextBoundaryId, List.of());
    }

    public Map<String, List<NodeDto>> getAllNodesDebug() {
        return boundaryNodes;
    }

    public Map<String, List<EdgeDto>> getAllEdgesDebug() {
        return boundaryEdges;
    }
}
