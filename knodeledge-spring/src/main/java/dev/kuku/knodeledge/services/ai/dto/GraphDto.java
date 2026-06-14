package dev.kuku.knodeledge.services.ai.dto;

import java.util.List;

/**
 * Data Transfer Objects for the Knowledge Graph extraction.
 */
public class GraphDto {

    public record NodeDto(
        String id,
        String label,
        String category,
        String description
    ) {}

    public record EdgeDto(
        String source,
        String target,
        String predicate,
        String context
    ) {}

    public record GraphResponse(
        List<NodeDto> nodes,
        List<EdgeDto> edges
    ) {}
}
