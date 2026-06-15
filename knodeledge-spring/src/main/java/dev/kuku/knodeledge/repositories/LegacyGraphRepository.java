package dev.kuku.knodeledge.repositories;

import dev.kuku.knodeledge.services.hierarchy.legacy.LegacyGraphModels.EdgeDto;
import dev.kuku.knodeledge.services.hierarchy.legacy.LegacyGraphModels.GraphResponse;
import dev.kuku.knodeledge.services.hierarchy.legacy.LegacyGraphModels.NodeDto;
import java.util.List;
import java.util.Map;

public interface LegacyGraphRepository {
    void saveNodes(String contextBoundaryId, List<NodeDto> nodes);
    void saveEdges(String contextBoundaryId, List<EdgeDto> edges);
    List<NodeDto> findNodesByBoundaryId(String contextBoundaryId);
    List<EdgeDto> findEdgesByBoundaryId(String contextBoundaryId);
    Map<String, GraphResponse> findAllGraphs();
}
