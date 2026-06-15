package dev.kuku.knodeledge.repositories;

import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.NodeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.EdgeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.GraphResponse;
import java.util.List;
import java.util.Map;

public interface GraphRepository {
    void saveNodes(String contextBoundaryId, List<NodeDto> nodes);
    void saveEdges(String contextBoundaryId, List<EdgeDto> edges);
    List<NodeDto> findNodesByBoundaryId(String contextBoundaryId);
    List<EdgeDto> findEdgesByBoundaryId(String contextBoundaryId);
    Map<String, GraphResponse> findAllGraphs();
}
