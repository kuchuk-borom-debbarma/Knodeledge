package dev.kuku.knodeledge.repositories;

import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.NodeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.EdgeDto;
import java.util.List;

public interface GraphRepository {
    void saveNodes(String contextBoundaryId, List<NodeDto> nodes);
    void saveEdges(String contextBoundaryId, List<EdgeDto> edges);
    List<NodeDto> findNodesByBoundaryId(String contextBoundaryId);
    List<EdgeDto> findEdgesByBoundaryId(String contextBoundaryId);
}
