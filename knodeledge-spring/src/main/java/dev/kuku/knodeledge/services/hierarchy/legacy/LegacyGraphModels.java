package dev.kuku.knodeledge.services.hierarchy.legacy;

import java.util.List;

/**
 * Read-only DTOs retained solely for one-time migration from the retired graph store.
 */
public final class LegacyGraphModels {
    private LegacyGraphModels() {}

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
