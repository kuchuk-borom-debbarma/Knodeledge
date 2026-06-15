package dev.kuku.knodeledge.services.graph;

import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.GraphResponse;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.NodeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.EdgeDto;
import dev.kuku.knodeledge.services.graph.dto.DebugGraphResponse;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public interface GraphService {
    GraphResponse getCompleteGraphByBoundaryId(String contextBoundaryId, String userId);
    DebugGraphResponse getAllGraphsDebug();
    void saveGraph(String contextBoundaryId, List<NodeDto> nodes, List<EdgeDto> edges);
}
