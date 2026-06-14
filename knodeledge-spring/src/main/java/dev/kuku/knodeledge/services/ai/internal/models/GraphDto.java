package dev.kuku.knodeledge.services.ai.internal.models;

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

    public record ExtractedNodeDto(
        String id,
        String label,
        String category,
        String description,
        String confidence // HIGH, MEDIUM, LOW
    ) {}

    public record ExtractedEdgeDto(
        String source,
        String target,
        String predicate,
        String taxonomyType, // EVENT, PREFERENCE, STATE
        String confidence,   // HIGH, MEDIUM, LOW
        String context       // The raw sentence context
    ) {}

    public record IngestionResponse(
        List<ExtractedNodeDto> nodes,
        List<ExtractedEdgeDto> edges
    ) {}

    public record VerificationResponse(
        boolean linterApproved,
        List<String> hallucinatedTriples,
        List<String> missingFacts,
        String reasoning
    ) {}
}
