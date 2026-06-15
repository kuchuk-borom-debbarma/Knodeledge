package dev.kuku.knodeledge.services.community.model;

import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.GraphResponse;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.EdgeRef;

import java.util.List;

public class CommunityModels {

    public record Community(
        String id,
        String name,
        String summary,
        String parentId,
        List<String> memberNodeIds,
        List<EdgeRef> memberEdges
    ) {}

    public record CommunityHierarchy(List<Community> communities) {}

    public record RouteChoice(String communityId, double score, String reason) {}

    public record RouteResponse(List<RouteChoice> choices) {}

    public record RoutingStep(
        List<String> candidateCommunityIds,
        List<String> selectedCommunityIds
    ) {}

    public record RetrievalResult(
        List<String> selectedCommunityIds,
        List<RoutingStep> routingPath,
        GraphResponse graph
    ) {}

    public record CommunityAssignment(
        String communityId,
        List<String> nodeIds,
        List<EdgeRef> edges
    ) {}

    public record CommunitySummaryUpdate(String communityId, String summary) {}

    public record CommunityUpdateResponse(
        List<Community> newCommunities,
        List<CommunityAssignment> assignments,
        List<CommunitySummaryUpdate> summaries
    ) {}
}
