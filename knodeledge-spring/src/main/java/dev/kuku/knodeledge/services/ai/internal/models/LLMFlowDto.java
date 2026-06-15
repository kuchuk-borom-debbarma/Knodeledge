package dev.kuku.knodeledge.services.ai.internal.models;

import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.EdgeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.NodeDto;

import java.util.List;

public class LLMFlowDto {

    public record EntityMention(
        String mention,
        String kind,
        String context
    ) {}

    public record AtomicCondition(
        String subject,
        String predicate,
        String object
    ) {}

    public record ConditionExpression(
        String operator,
        AtomicCondition condition,
        List<ConditionExpression> children
    ) {}

    public record SemanticAssertion(
        String operation,
        String subject,
        String predicate,
        String object,
        String context,
        ConditionExpression condition
    ) {}

    public record SemanticExtraction(
        List<EntityMention> entities,
        List<SemanticAssertion> assertions
    ) {}

    public record CanonicalEntity(
        String mention,
        String nodeId
    ) {}

    public record OntologyResponse(
        List<CanonicalEntity> canonicalEntities,
        List<NodeDto> nodes,
        List<EdgeDto> edges
    ) {}

    public record EdgeRef(
        String source,
        String target,
        String predicate
    ) {}

    public record GraphPatch(
        List<NodeDto> upsertNodes,
        List<EdgeDto> upsertEdges,
        List<String> deleteNodes,
        List<EdgeRef> deleteEdges
    ) {}

    public record PatchValidationResponse(
        boolean valid,
        List<String> issues,
        GraphPatch correctedPatch
    ) {}
}
