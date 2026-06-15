package dev.kuku.knodeledge.repositories.internal;

import dev.kuku.knodeledge.repositories.LegacyGraphRepository;
import dev.kuku.knodeledge.services.hierarchy.legacy.LegacyGraphModels.EdgeDto;
import dev.kuku.knodeledge.services.hierarchy.legacy.LegacyGraphModels.GraphResponse;
import dev.kuku.knodeledge.services.hierarchy.legacy.LegacyGraphModels.NodeDto;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class InMemoryLegacyGraphRepository implements LegacyGraphRepository {
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

    @Override
    public Map<String, GraphResponse> findAllGraphs() {
        var boundaryIds = new HashSet<>(boundaryNodes.keySet());
        boundaryIds.addAll(boundaryEdges.keySet());
        return boundaryIds.stream().collect(Collectors.toUnmodifiableMap(
            boundaryId -> boundaryId,
            boundaryId -> new GraphResponse(
                List.copyOf(findNodesByBoundaryId(boundaryId)),
                List.copyOf(findEdgesByBoundaryId(boundaryId))
            )
        ));
    }
}
