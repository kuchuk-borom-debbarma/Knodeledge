package dev.kuku.knodeledge.services.hierarchy.model;

import java.util.List;

public final class HierarchyModels {
    private HierarchyModels() {}

    public enum NodeKind {
        ROOT,
        TOPIC,
        ENTITY,
        FACT,
        CONDITION
    }

    public record HierarchyNode(
        String id,
        String semanticKey,
        String parentId,
        NodeKind kind,
        String relationToParent,
        String name,
        String statement,
        String summary
    ) {}

    public record BoundaryHierarchy(
        String boundaryId,
        String rootNodeId,
        List<HierarchyNode> nodes,
        long version
    ) {}

    public record HierarchyNodeDraft(
        String tempId,
        String semanticKey,
        String parentRef,
        NodeKind kind,
        String relationToParent,
        String name,
        String statement,
        String summary
    ) {}

    public record SemanticNodeUpdate(
        String semanticKey,
        NodeKind kind,
        String name,
        String statement,
        String summary
    ) {}

    public record HierarchyMove(
        String nodeId,
        String newParentRef,
        String relationToParent
    ) {}

    public record HierarchySummaryUpdate(String nodeId, String summary) {}

    public record HierarchyPatch(
        List<HierarchyNodeDraft> addNodes,
        List<SemanticNodeUpdate> updateSemanticNodes,
        List<HierarchyMove> moveNodes,
        List<String> deleteSemanticKeys,
        List<HierarchySummaryUpdate> ancestorSummaries
    ) {}

    public record KnowledgeItem(
        String semanticKey,
        String name,
        String statement,
        String summary,
        NodeKind kind,
        String relationToParent,
        List<KnowledgeItem> children
    ) {}

    public record KnowledgeExtraction(List<KnowledgeItem> items) {}

    public record RouteChoice(
        String nodeId,
        double score,
        String reason
    ) {}

    public record RouteResponse(
        boolean stopAtCurrent,
        List<RouteChoice> choices
    ) {}

    public record RoutingStep(
        List<String> candidateNodeIds,
        List<String> selectedNodeIds
    ) {}

    public record RoutingResult(
        List<String> selectedNodeIds,
        List<RoutingStep> path
    ) {}

    public record HierarchySlice(List<HierarchyNode> nodes) {}

    public record RoutedKnowledge(
        KnowledgeItem item,
        List<String> selectedNodeIds,
        HierarchySlice slice
    ) {}

    public record RebalanceResponse(
        boolean changeNeeded,
        HierarchyPatch patch
    ) {}

    public record HierarchyNodeView(
        String id,
        String semanticKey,
        NodeKind kind,
        String relationToParent,
        String name,
        String statement,
        String summary,
        int childCount
    ) {}

    public record HierarchyLevelResponse(
        HierarchyNodeView current,
        List<HierarchyNodeView> children,
        List<HierarchyNodeView> breadcrumbs,
        boolean leaf,
        long hierarchyVersion
    ) {}

    public record AnswerContextNode(
        HierarchyNode node,
        List<HierarchyNode> path
    ) {}
}
