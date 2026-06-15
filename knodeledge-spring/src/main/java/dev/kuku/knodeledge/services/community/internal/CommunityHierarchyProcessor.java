package dev.kuku.knodeledge.services.community.internal;

import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.EdgeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.GraphResponse;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.NodeDto;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.EdgeRef;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.GraphPatch;
import dev.kuku.knodeledge.services.community.model.CommunityModels.Community;
import dev.kuku.knodeledge.services.community.model.CommunityModels.CommunityAssignment;
import dev.kuku.knodeledge.services.community.model.CommunityModels.CommunityHierarchy;
import dev.kuku.knodeledge.services.community.model.CommunityModels.CommunityUpdateResponse;
import dev.kuku.knodeledge.services.community.model.CommunityModels.RouteChoice;
import dev.kuku.knodeledge.services.community.model.CommunityModels.RouteResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

@Component
public class CommunityHierarchyProcessor {
    public static final int MAX_DEPTH = 6;
    private static final Set<String> TAXONOMY_PREDICATES =
        Set.of("INSTANCE_OF", "HAS_GENRE", "SUBCATEGORY_OF", "GRAPH_ROLE");
    private static final Set<String> STRUCTURAL_PREDICATES =
        Set.of(
            "GRAPH_ROLE", "STATEMENT_SUBJECT", "WHEN", "ALL_OF", "ANY_OF", "NOT",
            "CONDITION_SUBJECT"
        );
    private static final Set<String> STRUCTURAL_ROLES =
        Set.of("statement", "condition_group", "condition");

    public CommunityHierarchy validate(CommunityHierarchy hierarchy, GraphResponse graph) {
        if (hierarchy == null || hierarchy.communities() == null
            || hierarchy.communities().isEmpty()) {
            throw new IllegalArgumentException("Community hierarchy must not be empty");
        }

        Map<String, Community> byId = new LinkedHashMap<>();
        for (var community : hierarchy.communities()) {
            validateCommunityFields(community);
            if (byId.put(community.id(), normalize(community)) != null) {
                throw new IllegalArgumentException("Duplicate community id: " + community.id());
            }
        }

        if (byId.values().stream().noneMatch(c -> c.parentId() == null)) {
            throw new IllegalArgumentException("Community hierarchy requires a root");
        }
        for (var community : byId.values()) {
            if (community.parentId() != null && !byId.containsKey(community.parentId())) {
                throw new IllegalArgumentException(
                    "Missing parent community: " + community.parentId()
                );
            }
            validateDepthAndCycles(community.id(), byId);
        }

        Set<String> nodeIds = new HashSet<>();
        for (var node : list(graph.nodes())) {
            nodeIds.add(node.id());
        }
        Set<String> edgeKeys = new HashSet<>();
        for (var edge : list(graph.edges())) {
            edgeKeys.add(edgeKey(edge.source(), edge.target(), edge.predicate()));
        }
        for (var community : byId.values()) {
            for (var nodeId : community.memberNodeIds()) {
                if (!nodeIds.contains(nodeId)) {
                    throw new IllegalArgumentException(
                        "Community references missing node: " + community.id() + " -> " + nodeId
                    );
                }
            }
            for (var edge : community.memberEdges()) {
                validateEdgeRef(edge);
                if (!edgeKeys.contains(edgeKey(edge.source(), edge.target(), edge.predicate()))) {
                    throw new IllegalArgumentException(
                        "Community references missing edge: " + community.id()
                    );
                }
            }
        }
        Set<String> coveredNodes = new HashSet<>();
        Set<String> coveredEdges = new HashSet<>();
        for (var community : byId.values()) {
            coveredNodes.addAll(community.memberNodeIds());
            for (var edge : community.memberEdges()) {
                coveredEdges.add(edgeKey(edge.source(), edge.target(), edge.predicate()));
            }
        }
        if (!coveredNodes.containsAll(nodeIds)) {
            throw new IllegalArgumentException("Every graph node must belong to a community");
        }
        if (!coveredEdges.containsAll(edgeKeys)) {
            throw new IllegalArgumentException("Every graph edge must belong to a community");
        }
        return new CommunityHierarchy(new ArrayList<>(byId.values()));
    }

    public GraphResponse retrieve(
        CommunityHierarchy hierarchy,
        GraphResponse fullGraph,
        List<String> selectedCommunityIds
    ) {
        Map<String, Community> communities = communitiesById(hierarchy);
        Set<String> includedNodes = new LinkedHashSet<>();
        Set<String> includedEdges = new LinkedHashSet<>();

        for (var communityId : selectedCommunityIds) {
            var community = communities.get(communityId);
            if (community == null) {
                throw new IllegalArgumentException("Unknown selected community: " + communityId);
            }
            includedNodes.addAll(community.memberNodeIds());
            for (var edge : community.memberEdges()) {
                includedEdges.add(edgeKey(edge.source(), edge.target(), edge.predicate()));
            }
        }

        Map<String, NodeDto> nodesById = new LinkedHashMap<>();
        for (var node : list(fullGraph.nodes())) {
            nodesById.put(node.id(), node);
        }
        Map<String, EdgeDto> edgesByKey = new LinkedHashMap<>();
        for (var edge : list(fullGraph.edges())) {
            edgesByKey.put(edgeKey(edge.source(), edge.target(), edge.predicate()), edge);
        }

        // One-hop expansion preserves direct cross-community relationships.
        Set<String> seedNodes = new HashSet<>(includedNodes);
        for (var edge : edgesByKey.values()) {
            String key = edgeKey(edge.source(), edge.target(), edge.predicate());
            if (includedEdges.contains(key)
                || seedNodes.contains(edge.source())
                || seedNodes.contains(edge.target())) {
                includedEdges.add(key);
                includedNodes.add(edge.source());
                includedNodes.add(edge.target());
            }
        }

        expandTaxonomy(includedNodes, includedEdges, nodesById, edgesByKey);
        expandStructuralGraphs(includedNodes, includedEdges, edgesByKey);
        expandTaxonomy(includedNodes, includedEdges, nodesById, edgesByKey);

        var nodes = nodesById.values().stream()
            .filter(node -> includedNodes.contains(node.id()))
            .toList();
        var edges = edgesByKey.entrySet().stream()
            .filter(entry -> includedEdges.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .filter(edge ->
                includedNodes.contains(edge.source()) && includedNodes.contains(edge.target()))
            .toList();
        return new GraphResponse(nodes, edges);
    }

    public void validateDeletesWithinRetrieval(GraphPatch patch, GraphResponse retrieval) {
        Set<String> allowedNodes = new HashSet<>();
        for (var node : list(retrieval.nodes())) {
            allowedNodes.add(node.id());
        }
        Set<String> allowedEdges = new HashSet<>();
        for (var edge : list(retrieval.edges())) {
            allowedEdges.add(edgeKey(edge.source(), edge.target(), edge.predicate()));
        }
        for (var nodeId : list(patch.deleteNodes())) {
            if (!allowedNodes.contains(nodeId)) {
                throw new IllegalArgumentException(
                    "Patch cannot delete node outside retrieved graph: " + nodeId
                );
            }
        }
        for (var edge : list(patch.deleteEdges())) {
            if (!allowedEdges.contains(edgeKey(edge.source(), edge.target(), edge.predicate()))) {
                throw new IllegalArgumentException(
                    "Patch cannot delete edge outside retrieved graph"
                );
            }
        }
    }

    public CommunityHierarchy applyUpdate(
        CommunityHierarchy current,
        CommunityUpdateResponse update,
        GraphPatch patch,
        GraphResponse finalGraph,
        List<String> selectedCommunityIds
    ) {
        Map<String, Community> communities = communitiesById(current);
        Set<String> deletedNodeIds = new HashSet<>(list(patch.deleteNodes()));
        Set<String> deletedEdgeKeys = new HashSet<>();
        for (var edge : list(patch.deleteEdges())) {
            deletedEdgeKeys.add(edgeKey(edge.source(), edge.target(), edge.predicate()));
        }
        Set<String> deletionAffectedCommunities = new LinkedHashSet<>();
        for (var entry : new ArrayList<>(communities.entrySet())) {
            var community = entry.getValue();
            var retainedNodes = community.memberNodeIds().stream()
                .filter(nodeId -> !deletedNodeIds.contains(nodeId))
                .toList();
            var retainedEdges = community.memberEdges().stream()
                .filter(edge ->
                    !deletedNodeIds.contains(edge.source())
                        && !deletedNodeIds.contains(edge.target())
                        && !deletedEdgeKeys.contains(
                            edgeKey(edge.source(), edge.target(), edge.predicate())
                        ))
                .toList();
            if (retainedNodes.size() != community.memberNodeIds().size()
                || retainedEdges.size() != community.memberEdges().size()) {
                deletionAffectedCommunities.add(community.id());
                communities.put(
                    community.id(),
                    new Community(
                        community.id(), community.name(), community.summary(),
                        community.parentId(), retainedNodes, retainedEdges
                    )
                );
            }
        }
        for (var community : list(update == null ? null : update.newCommunities())) {
            if (communities.containsKey(community.id())) {
                throw new IllegalArgumentException("New community id already exists: " + community.id());
            }
            communities.put(community.id(), normalize(community));
        }

        Map<String, CommunityAssignment> assignments = new HashMap<>();
        for (var assignment : list(update == null ? null : update.assignments())) {
            if (!communities.containsKey(assignment.communityId())) {
                throw new IllegalArgumentException(
                    "Assignment references missing community: " + assignment.communityId()
                );
            }
            assignments.put(assignment.communityId(), assignment);
        }

        List<String> changedNodeIds = list(patch.upsertNodes()).stream()
            .map(NodeDto::id)
            .toList();
        List<EdgeRef> changedEdges = list(patch.upsertEdges()).stream()
            .map(edge -> new EdgeRef(edge.source(), edge.target(), edge.predicate()))
            .toList();
        if ((!changedNodeIds.isEmpty() || !changedEdges.isEmpty()) && assignments.isEmpty()) {
            for (var communityId : selectedCommunityIds) {
                assignments.put(
                    communityId,
                    new CommunityAssignment(communityId, changedNodeIds, changedEdges)
                );
            }
        }

        Set<String> assignedNodes = new HashSet<>();
        Set<String> assignedEdges = new HashSet<>();
        for (var entry : assignments.entrySet()) {
            var old = communities.get(entry.getKey());
            var assignment = entry.getValue();
            Set<String> nodeIds = new LinkedHashSet<>(old.memberNodeIds());
            nodeIds.addAll(list(assignment.nodeIds()));
            Set<EdgeRef> edges = new LinkedHashSet<>(old.memberEdges());
            edges.addAll(list(assignment.edges()));
            assignedNodes.addAll(list(assignment.nodeIds()));
            for (var edge : list(assignment.edges())) {
                assignedEdges.add(edgeKey(edge.source(), edge.target(), edge.predicate()));
            }
            communities.put(
                old.id(),
                new Community(
                    old.id(), old.name(), old.summary(), old.parentId(),
                    new ArrayList<>(nodeIds), new ArrayList<>(edges)
                )
            );
        }
        if (!assignedNodes.containsAll(changedNodeIds)) {
            throw new IllegalArgumentException("Every changed node must be assigned to a community");
        }
        for (var edge : changedEdges) {
            if (!assignedEdges.contains(edgeKey(edge.source(), edge.target(), edge.predicate()))) {
                throw new IllegalArgumentException("Every changed edge must be assigned to a community");
            }
        }

        Set<String> requiredSummaries = new LinkedHashSet<>(assignments.keySet());
        requiredSummaries.addAll(deletionAffectedCommunities);
        for (var communityId : new ArrayList<>(requiredSummaries)) {
            addAncestors(communityId, communities, requiredSummaries);
        }
        Map<String, String> summaries = new HashMap<>();
        for (var summary : list(update == null ? null : update.summaries())) {
            if (summary != null && communities.containsKey(summary.communityId())
                && summary.summary() != null && !summary.summary().isBlank()) {
                summaries.put(summary.communityId(), summary.summary());
            }
        }
        if (!summaries.keySet().containsAll(requiredSummaries)) {
            throw new IllegalArgumentException(
                "Hierarchy update must refresh assigned communities and all ancestors"
            );
        }
        for (var entry : summaries.entrySet()) {
            var old = communities.get(entry.getKey());
            communities.put(
                old.id(),
                new Community(
                    old.id(), old.name(), entry.getValue(), old.parentId(),
                    old.memberNodeIds(), old.memberEdges()
                )
            );
        }

        var hierarchy = new CommunityHierarchy(new ArrayList<>(communities.values()));
        return validate(hierarchy, finalGraph);
    }

    public Map<String, Community> communitiesById(CommunityHierarchy hierarchy) {
        Map<String, Community> result = new LinkedHashMap<>();
        for (var community : list(hierarchy.communities())) {
            result.put(community.id(), community);
        }
        return result;
    }

    public List<Community> roots(CommunityHierarchy hierarchy) {
        return list(hierarchy.communities()).stream()
            .filter(community -> community.parentId() == null)
            .toList();
    }

    public List<Community> children(
        CommunityHierarchy hierarchy,
        Set<String> parentIds
    ) {
        return list(hierarchy.communities()).stream()
            .filter(community ->
                community.parentId() != null && parentIds.contains(community.parentId()))
            .toList();
    }

    public List<Community> selectRouteChoices(
        List<Community> candidates,
        RouteResponse response,
        int beamWidth
    ) {
        Map<String, Community> candidatesById = new LinkedHashMap<>();
        for (var candidate : candidates) {
            candidatesById.put(candidate.id(), candidate);
        }
        var selected = list(response == null ? null : response.choices()).stream()
            .filter(choice ->
                choice != null && candidatesById.containsKey(choice.communityId()))
            .sorted(Comparator.comparingDouble(RouteChoice::score).reversed())
            .map(RouteChoice::communityId)
            .distinct()
            .limit(beamWidth)
            .map(candidatesById::get)
            .toList();
        if (selected.isEmpty()) {
            throw new IllegalStateException("Community router selected no valid candidates");
        }
        return selected;
    }

    private void expandTaxonomy(
        Set<String> includedNodes,
        Set<String> includedEdges,
        Map<String, NodeDto> nodesById,
        Map<String, EdgeDto> edgesByKey
    ) {
        boolean changed;
        do {
            changed = false;
            for (var nodeId : new ArrayList<>(includedNodes)) {
                var node = nodesById.get(nodeId);
                if (node != null) {
                    for (var categoryId : list(node.categories())) {
                        changed |= includedNodes.add(categoryId);
                    }
                }
            }
            for (var edge : edgesByKey.values()) {
                if (includedNodes.contains(edge.source())
                    && TAXONOMY_PREDICATES.contains(edge.predicate())) {
                    changed |= includedNodes.add(edge.target());
                    changed |= includedEdges.add(
                        edgeKey(edge.source(), edge.target(), edge.predicate())
                    );
                }
            }
        } while (changed);
    }

    private void expandStructuralGraphs(
        Set<String> includedNodes,
        Set<String> includedEdges,
        Map<String, EdgeDto> edgesByKey
    ) {
        Set<String> structuralNodes = new HashSet<>();
        for (var edge : edgesByKey.values()) {
            if (edge.predicate().equals("GRAPH_ROLE") && STRUCTURAL_ROLES.contains(edge.target())) {
                structuralNodes.add(edge.source());
            }
        }
        boolean changed;
        do {
            changed = false;
            for (var edge : edgesByKey.values()) {
                boolean touchesIncludedStructural =
                    structuralNodes.contains(edge.source()) && includedNodes.contains(edge.source());
                boolean discoversStructural =
                    structuralNodes.contains(edge.source())
                        && includedNodes.contains(edge.target())
                        && !STRUCTURAL_PREDICATES.contains(edge.predicate());
                if (touchesIncludedStructural || discoversStructural) {
                    changed |= includedNodes.add(edge.source());
                    changed |= includedNodes.add(edge.target());
                    changed |= includedEdges.add(
                        edgeKey(edge.source(), edge.target(), edge.predicate())
                    );
                }
            }
        } while (changed);
    }

    private void validateDepthAndCycles(String startId, Map<String, Community> communities) {
        Set<String> visited = new HashSet<>();
        String current = startId;
        int depth = 0;
        while (current != null) {
            if (!visited.add(current)) {
                throw new IllegalArgumentException("Community hierarchy cycle at: " + current);
            }
            if (depth++ >= MAX_DEPTH) {
                throw new IllegalArgumentException(
                    "Community hierarchy exceeds max depth " + MAX_DEPTH
                );
            }
            current = communities.get(current).parentId();
        }
    }

    private void addAncestors(
        String communityId,
        Map<String, Community> communities,
        Set<String> result
    ) {
        String parentId = communities.get(communityId).parentId();
        while (parentId != null) {
            result.add(parentId);
            parentId = communities.get(parentId).parentId();
        }
    }

    private void validateCommunityFields(Community community) {
        if (community == null || blank(community.id()) || blank(community.name())
            || blank(community.summary())) {
            throw new IllegalArgumentException("Community id, name, and summary are required");
        }
    }

    private void validateEdgeRef(EdgeRef edge) {
        if (edge == null || blank(edge.source()) || blank(edge.target())
            || blank(edge.predicate())) {
            throw new IllegalArgumentException("Community edge reference fields are required");
        }
    }

    private Community normalize(Community community) {
        return new Community(
            community.id(),
            community.name(),
            community.summary(),
            blank(community.parentId()) ? null : community.parentId(),
            List.copyOf(list(community.memberNodeIds())),
            List.copyOf(list(community.memberEdges()))
        );
    }

    private String edgeKey(String source, String target, String predicate) {
        return source + '\u0000' + predicate + '\u0000' + target;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }
}
