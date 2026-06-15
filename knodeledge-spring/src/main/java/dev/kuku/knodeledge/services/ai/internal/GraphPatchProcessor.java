package dev.kuku.knodeledge.services.ai.internal;

import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.EdgeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.GraphResponse;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.NodeDto;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.EdgeRef;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.GraphPatch;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.OntologyResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GraphPatchProcessor {

    private static final Set<String> CATEGORY_PREDICATES =
        Set.of("INSTANCE_OF", "HAS_GENRE", "GRAPH_ROLE");
    private static final Set<String> STRUCTURAL_PREDICATES =
        Set.of(
            "GRAPH_ROLE",
            "STATEMENT_SUBJECT",
            "WHEN",
            "ALL_OF",
            "ANY_OF",
            "NOT",
            "CONDITION_SUBJECT"
        );

    public GraphPatch completeReferences(
        GraphResponse existingGraph,
        GraphPatch correctedPatch,
        GraphPatch candidatePatch,
        OntologyResponse ontology
    ) {
        requireNonNullPatch(correctedPatch);

        Map<String, NodeDto> existingNodes = nodesById(existingGraph.nodes());
        Map<String, NodeDto> availableNodes = new LinkedHashMap<>();
        putNodes(availableNodes, ontology == null ? null : ontology.nodes());
        putNodes(availableNodes, candidatePatch == null ? null : candidatePatch.upsertNodes());
        putNodes(availableNodes, correctedPatch.upsertNodes());

        Map<String, EdgeDto> availableEdges = new LinkedHashMap<>();
        putEdges(availableEdges, ontology == null ? null : ontology.edges());
        putEdges(availableEdges, candidatePatch == null ? null : candidatePatch.upsertEdges());
        putEdges(availableEdges, correctedPatch.upsertEdges());

        Map<String, NodeDto> completedNodes = new LinkedHashMap<>();
        putNodes(completedNodes, correctedPatch.upsertNodes());
        Map<String, EdgeDto> completedEdges = new LinkedHashMap<>();
        putEdges(completedEdges, correctedPatch.upsertEdges());
        Set<String> deletedNodeIds = new HashSet<>(list(correctedPatch.deleteNodes()));

        List<String> requiredNodeIds = new ArrayList<>();
        for (var edge : completedEdges.values()) {
            requiredNodeIds.add(edge.source());
            requiredNodeIds.add(edge.target());
        }
        Set<String> processedNodeIds = new HashSet<>();

        for (int index = 0; index < requiredNodeIds.size(); index++) {
            String nodeId = requiredNodeIds.get(index);
            if (deletedNodeIds.contains(nodeId)
                || existingNodes.containsKey(nodeId)
                || !processedNodeIds.add(nodeId)) {
                continue;
            }

            NodeDto node = completedNodes.getOrDefault(nodeId, availableNodes.get(nodeId));
            if (node == null) {
                throw new IllegalArgumentException(
                    "Validated patch references node with no definition: " + nodeId
                );
            }
            completedNodes.putIfAbsent(nodeId, normalizeNode(node));

            for (var categoryId : list(node.categories())) {
                requiredNodeIds.add(categoryId);
                String categoryEdgeKey = findCategoryEdgeKey(
                    nodeId,
                    categoryId,
                    existingGraph.edges(),
                    completedEdges.values(),
                    availableEdges.values()
                );
                if (categoryEdgeKey == null) {
                    throw new IllegalArgumentException(
                        "No taxonomy edge available for category cache: "
                            + nodeId + " -> " + categoryId
                    );
                }
                EdgeDto categoryEdge = availableEdges.get(categoryEdgeKey);
                if (categoryEdge != null) {
                    completedEdges.putIfAbsent(categoryEdgeKey, categoryEdge);
                }
            }
        }

        return new GraphPatch(
            new ArrayList<>(completedNodes.values()),
            new ArrayList<>(completedEdges.values()),
            list(correctedPatch.deleteNodes()),
            list(correctedPatch.deleteEdges())
        );
    }

    public GraphResponse apply(GraphResponse existingGraph, GraphPatch patch) {
        requireNonNullPatch(patch);

        Map<String, NodeDto> nodes = new LinkedHashMap<>();
        for (var node : list(existingGraph.nodes())) {
            validateNode(node);
            nodes.put(node.id(), normalizeNode(node));
        }
        for (var node : list(patch.upsertNodes())) {
            validateNode(node);
            nodes.put(node.id(), normalizeNode(node));
        }

        Map<String, EdgeDto> edges = new LinkedHashMap<>();
        for (var edge : list(existingGraph.edges())) {
            validateEdgeFields(edge);
            edges.put(edgeKey(edge.source(), edge.target(), edge.predicate()), edge);
        }
        for (var edge : list(patch.upsertEdges())) {
            validateEdgeFields(edge);
            edges.put(edgeKey(edge.source(), edge.target(), edge.predicate()), edge);
        }
        for (var edge : list(patch.deleteEdges())) {
            validateEdgeRef(edge);
            edges.remove(edgeKey(edge.source(), edge.target(), edge.predicate()));
        }
        for (var nodeId : list(patch.deleteNodes())) {
            if (isBlank(nodeId)) {
                throw new IllegalArgumentException("Delete node id must be non-empty");
            }
            nodes.remove(nodeId);
        }

        var response = new GraphResponse(
            new ArrayList<>(nodes.values()),
            new ArrayList<>(edges.values())
        );
        validateFinalGraph(response);
        return response;
    }

    private void validateFinalGraph(GraphResponse graph) {
        Map<String, NodeDto> nodes = new LinkedHashMap<>();
        for (var node : graph.nodes()) {
            if (nodes.put(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate node id: " + node.id());
            }
        }

        Map<String, List<EdgeDto>> outgoing = new LinkedHashMap<>();
        Set<String> connectedNodeIds = new HashSet<>();
        for (var edge : graph.edges()) {
            if (!nodes.containsKey(edge.source()) || !nodes.containsKey(edge.target())) {
                throw new IllegalArgumentException(
                    "Edge references missing node: " + edge.source() + " -"
                        + edge.predicate() + "-> " + edge.target()
                );
            }
            outgoing.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge);
            connectedNodeIds.add(edge.source());
            connectedNodeIds.add(edge.target());
        }

        for (var node : graph.nodes()) {
            if (!connectedNodeIds.contains(node.id())) {
                throw new IllegalArgumentException("Isolated node: " + node.id());
            }
            for (var categoryId : list(node.categories())) {
                if (!nodes.containsKey(categoryId)) {
                    throw new IllegalArgumentException(
                        "Category references missing node: " + node.id() + " -> " + categoryId
                    );
                }
                boolean linked = outgoing.getOrDefault(node.id(), List.of()).stream()
                    .anyMatch(edge ->
                        edge.target().equals(categoryId)
                            && CATEGORY_PREDICATES.contains(edge.predicate())
                    );
                if (!linked) {
                    throw new IllegalArgumentException(
                        "Category cache lacks graph edge: " + node.id() + " -> " + categoryId
                    );
                }
            }
        }

        validateStructuralNodes(graph.edges(), outgoing);
    }

    private void validateStructuralNodes(
        List<EdgeDto> edges,
        Map<String, List<EdgeDto>> outgoing
    ) {
        Set<String> statements = roleNodes(edges, "statement");
        Set<String> groups = roleNodes(edges, "condition_group");
        Set<String> conditions = roleNodes(edges, "condition");
        Set<String> rootGroups = new HashSet<>();

        for (var statementId : statements) {
            var statementEdges = outgoing.getOrDefault(statementId, List.of());
            requireCount(statementId, statementEdges, "STATEMENT_SUBJECT", 1);
            requireCount(statementId, statementEdges, "WHEN", 1);
            requireSemanticEdge(statementId, statementEdges);
            String rootGroup = statementEdges.stream()
                .filter(edge -> edge.predicate().equals("WHEN"))
                .findFirst()
                .orElseThrow()
                .target();
            if (!groups.contains(rootGroup)) {
                throw new IllegalArgumentException(
                    "Statement WHEN must target a condition group: " + statementId
                );
            }
            rootGroups.add(rootGroup);
        }

        for (var groupId : groups) {
            var groupEdges = outgoing.getOrDefault(groupId, List.of());
            long allOf = count(groupEdges, "ALL_OF");
            long anyOf = count(groupEdges, "ANY_OF");
            long not = count(groupEdges, "NOT");
            int operatorKinds = (allOf > 0 ? 1 : 0) + (anyOf > 0 ? 1 : 0) + (not > 0 ? 1 : 0);
            if (operatorKinds != 1 || not > 1) {
                throw new IllegalArgumentException(
                    "Condition group must use exactly one valid boolean operator: " + groupId
                );
            }
            boolean invalidTarget = groupEdges.stream()
                .filter(edge ->
                    edge.predicate().equals("ALL_OF")
                        || edge.predicate().equals("ANY_OF")
                        || edge.predicate().equals("NOT")
                )
                .anyMatch(edge ->
                    !groups.contains(edge.target()) && !conditions.contains(edge.target())
                );
            if (invalidTarget) {
                throw new IllegalArgumentException(
                    "Condition group child must be a condition or nested group: " + groupId
                );
            }
        }

        for (var conditionId : conditions) {
            var conditionEdges = outgoing.getOrDefault(conditionId, List.of());
            requireCount(conditionId, conditionEdges, "CONDITION_SUBJECT", 1);
            requireSemanticEdge(conditionId, conditionEdges);
        }

        Set<String> reachable = new HashSet<>();
        for (var rootGroup : rootGroups) {
            validateConditionTree(
                rootGroup,
                groups,
                conditions,
                outgoing,
                new HashSet<>(),
                reachable
            );
        }
        Set<String> expectedReachable = new HashSet<>(groups);
        expectedReachable.addAll(conditions);
        if (!reachable.equals(expectedReachable)) {
            throw new IllegalArgumentException(
                "Every condition group and condition must belong to a statement"
            );
        }
    }

    private void validateConditionTree(
        String nodeId,
        Set<String> groups,
        Set<String> conditions,
        Map<String, List<EdgeDto>> outgoing,
        Set<String> visiting,
        Set<String> reachable
    ) {
        if (conditions.contains(nodeId)) {
            reachable.add(nodeId);
            return;
        }
        if (!groups.contains(nodeId)) {
            throw new IllegalArgumentException("Invalid condition-tree node: " + nodeId);
        }
        if (!visiting.add(nodeId)) {
            throw new IllegalArgumentException("Condition group cycle detected: " + nodeId);
        }

        reachable.add(nodeId);
        for (var edge : outgoing.getOrDefault(nodeId, List.of())) {
            if (edge.predicate().equals("ALL_OF")
                || edge.predicate().equals("ANY_OF")
                || edge.predicate().equals("NOT")) {
                validateConditionTree(
                    edge.target(),
                    groups,
                    conditions,
                    outgoing,
                    visiting,
                    reachable
                );
            }
        }
        visiting.remove(nodeId);
    }

    private Set<String> roleNodes(List<EdgeDto> edges, String role) {
        Set<String> result = new HashSet<>();
        for (var edge : edges) {
            if (edge.predicate().equals("GRAPH_ROLE") && edge.target().equals(role)) {
                result.add(edge.source());
            }
        }
        return result;
    }

    private void requireSemanticEdge(String nodeId, List<EdgeDto> edges) {
        long semanticEdges = edges.stream()
            .filter(edge -> !STRUCTURAL_PREDICATES.contains(edge.predicate()))
            .count();
        if (semanticEdges != 1) {
            throw new IllegalArgumentException(
                "Structural node must have exactly one semantic edge: " + nodeId
            );
        }
    }

    private void requireCount(
        String nodeId,
        List<EdgeDto> edges,
        String predicate,
        long expected
    ) {
        long actual = count(edges, predicate);
        if (actual != expected) {
            throw new IllegalArgumentException(
                nodeId + " must have " + expected + " " + predicate + " edge(s)"
            );
        }
    }

    private long count(List<EdgeDto> edges, String predicate) {
        return edges.stream().filter(edge -> edge.predicate().equals(predicate)).count();
    }

    private NodeDto normalizeNode(NodeDto node) {
        return new NodeDto(
            node.id(),
            node.label(),
            List.copyOf(list(node.categories())),
            node.description()
        );
    }

    private Map<String, NodeDto> nodesById(List<NodeDto> nodes) {
        Map<String, NodeDto> result = new LinkedHashMap<>();
        putNodes(result, nodes);
        return result;
    }

    private void putNodes(Map<String, NodeDto> target, List<NodeDto> nodes) {
        for (var node : list(nodes)) {
            if (node != null && !isBlank(node.id())) {
                target.put(node.id(), node);
            }
        }
    }

    private void putEdges(Map<String, EdgeDto> target, List<EdgeDto> edges) {
        for (var edge : list(edges)) {
            if (edge != null
                && !isBlank(edge.source())
                && !isBlank(edge.target())
                && !isBlank(edge.predicate())) {
                target.put(edgeKey(edge.source(), edge.target(), edge.predicate()), edge);
            }
        }
    }

    private String findCategoryEdgeKey(
        String source,
        String target,
        List<EdgeDto> existingEdges,
        java.util.Collection<EdgeDto> completedEdges,
        java.util.Collection<EdgeDto> availableEdges
    ) {
        for (var edges : List.of(existingEdges, completedEdges, availableEdges)) {
            for (var edge : edges) {
                if (edge.source().equals(source)
                    && edge.target().equals(target)
                    && CATEGORY_PREDICATES.contains(edge.predicate())) {
                    return edgeKey(edge.source(), edge.target(), edge.predicate());
                }
            }
        }
        return null;
    }

    private void validateNode(NodeDto node) {
        if (node == null || isBlank(node.id()) || isBlank(node.label()) || isBlank(node.description())) {
            throw new IllegalArgumentException("Node fields must be non-empty");
        }
    }

    private void validateEdgeFields(EdgeDto edge) {
        if (edge == null
            || isBlank(edge.source())
            || isBlank(edge.target())
            || isBlank(edge.predicate())
            || isBlank(edge.context())) {
            throw new IllegalArgumentException("Edge fields must be non-empty");
        }
    }

    private void validateEdgeRef(EdgeRef edge) {
        if (edge == null
            || isBlank(edge.source())
            || isBlank(edge.target())
            || isBlank(edge.predicate())) {
            throw new IllegalArgumentException("Delete edge fields must be non-empty");
        }
    }

    private void requireNonNullPatch(GraphPatch patch) {
        if (patch == null) {
            throw new IllegalArgumentException("Graph patch is required");
        }
    }

    private String edgeKey(String source, String target, String predicate) {
        return source + '\u0000' + predicate + '\u0000' + target;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }
}
