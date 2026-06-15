package dev.kuku.knodeledge.services.graph.dto;

import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.EdgeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.NodeDto;

import java.util.List;

public record DebugGraphResponse(
    int boundaryCount,
    int totalNodes,
    int totalEdges,
    List<BoundaryGraph> graphs
) {
    public record BoundaryGraph(
        String contextBoundaryId,
        String name,
        String context,
        String userId,
        List<NodeDto> nodes,
        List<EdgeDto> edges
    ) {}
}
