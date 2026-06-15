package dev.kuku.knodeledge.services.ai.internal.models;

import java.util.List;

/**
 * Data Transfer Objects for graph extraction and reconciliation.
 *
 * <p>Qualified facts are represented as traversable statement, condition-group, and condition
 * nodes. Edges therefore contain only graph relation data; conditions are not edge metadata.</p>
 */
public class GraphDto {

    public record NodeDto(
        String id,
        String label,
        List<String> categories,
        String description
    ) {}

    /**
     * A persisted graph relation.
     */
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
