package dev.kuku.knodeledge.services.hierarchy.internal;

import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.BoundaryHierarchy;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyLevelResponse;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyNode;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyNodeDraft;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyNodeView;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyPatch;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchySlice;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.NodeKind;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.RouteChoice;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.RouteResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class HierarchyProcessor {
    public static final int MAX_DEPTH = 32;
    public static final int REBALANCE_CHILD_THRESHOLD = 12;
    private static final Pattern RELATION_PATTERN =
        Pattern.compile("[A-Z][A-Z0-9_]*");

    public BoundaryHierarchy validate(BoundaryHierarchy hierarchy) {
        if (hierarchy == null || blank(hierarchy.boundaryId())) {
            throw new IllegalArgumentException("Hierarchy boundaryId is required");
        }
        if (blank(hierarchy.rootNodeId())) {
            throw new IllegalArgumentException("Hierarchy rootNodeId is required");
        }
        if (hierarchy.nodes() == null || hierarchy.nodes().isEmpty()) {
            throw new IllegalArgumentException("Hierarchy must contain a root node");
        }
        if (hierarchy.version() < 1) {
            throw new IllegalArgumentException("Hierarchy version must be positive");
        }

        Map<String, HierarchyNode> byId = new LinkedHashMap<>();
        for (var raw : hierarchy.nodes()) {
            var node = normalize(raw);
            validateNodeFields(node);
            if (byId.put(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate hierarchy node id: " + node.id());
            }
        }

        var root = byId.get(hierarchy.rootNodeId());
        if (root == null) {
            throw new IllegalArgumentException("Hierarchy rootNodeId references missing node");
        }
        long rootCount = byId.values().stream()
            .filter(node -> node.parentId() == null)
            .count();
        if (rootCount != 1 || root.kind() != NodeKind.ROOT || root.parentId() != null
            || root.relationToParent() != null) {
            throw new IllegalArgumentException("Hierarchy requires exactly one valid root");
        }

        for (var node : byId.values()) {
            if (node.id().equals(root.id())) {
                continue;
            }
            if (node.kind() == NodeKind.ROOT) {
                throw new IllegalArgumentException("Only rootNodeId may use ROOT kind");
            }
            if (blank(node.parentId()) || !byId.containsKey(node.parentId())) {
                throw new IllegalArgumentException("Missing parent for node: " + node.id());
            }
            validateDepthAndCycles(node.id(), byId);
        }
        validateSemanticCopies(byId.values());

        return new BoundaryHierarchy(
            hierarchy.boundaryId(),
            hierarchy.rootNodeId(),
            List.copyOf(byId.values()),
            hierarchy.version()
        );
    }

    public HierarchyLevelResponse level(BoundaryHierarchy hierarchy, String requestedNodeId) {
        var validated = validate(hierarchy);
        var byId = nodesById(validated);
        String nodeId = blank(requestedNodeId) ? validated.rootNodeId() : requestedNodeId;
        var current = byId.get(nodeId);
        if (current == null) {
            throw new IllegalArgumentException("Hierarchy node not found: " + nodeId);
        }
        var children = children(validated, nodeId);
        return new HierarchyLevelResponse(
            view(current, children.size()),
            children.stream()
                .map(child -> view(child, children(validated, child.id()).size()))
                .toList(),
            path(validated, nodeId).stream()
                .map(node -> view(node, children(validated, node.id()).size()))
                .toList(),
            children.isEmpty(),
            validated.version()
        );
    }

    public HierarchySlice slice(BoundaryHierarchy hierarchy, List<String> selectedNodeIds) {
        Map<String, HierarchyNode> byId = nodesById(hierarchy);
        Set<String> included = new LinkedHashSet<>();
        for (var selectedId : list(selectedNodeIds)) {
            if (!byId.containsKey(selectedId)) {
                throw new IllegalArgumentException("Unknown selected hierarchy node: " + selectedId);
            }
            for (var pathNode : path(hierarchy, selectedId)) {
                included.add(pathNode.id());
            }
            for (var child : children(hierarchy, selectedId)) {
                included.add(child.id());
            }
        }
        return new HierarchySlice(
            hierarchy.nodes().stream()
                .filter(node -> included.contains(node.id()))
                .toList()
        );
    }

    public HierarchySlice sliceWithSemanticCopies(
        BoundaryHierarchy hierarchy,
        List<String> selectedNodeIds,
        Set<String> semanticKeys
    ) {
        Set<String> ids = new LinkedHashSet<>(selectedNodeIds);
        for (var node : hierarchy.nodes()) {
            if (semanticKeys.contains(node.semanticKey())) {
                ids.add(node.id());
            }
        }
        return slice(hierarchy, new ArrayList<>(ids));
    }

    public BoundaryHierarchy applyPatch(
        BoundaryHierarchy current,
        HierarchyPatch patch,
        Set<String> allowedNodeIds
    ) {
        var validated = validate(current);
        if (patch == null) {
            throw new IllegalArgumentException("Hierarchy patch is required");
        }
        Map<String, HierarchyNode> nodes = nodesById(validated);
        Set<String> allowed = new HashSet<>(allowedNodeIds);
        Set<String> requiredSummaryIds = new LinkedHashSet<>();
        Set<String> touchedParents = new LinkedHashSet<>();
        Set<String> addedNodeIds = new LinkedHashSet<>();

        for (var semanticKey : list(patch.deleteSemanticKeys())) {
            requireAllowedSemantic(nodes, semanticKey, allowed);
            if (nodes.get(validated.rootNodeId()).semanticKey().equals(semanticKey)) {
                throw new IllegalArgumentException("Hierarchy root cannot be deleted");
            }
            Set<String> deleteIds = new LinkedHashSet<>();
            for (var node : nodes.values()) {
                if (node.semanticKey().equals(semanticKey)) {
                    deleteIds.add(node.id());
                    if (node.parentId() != null) {
                        touchedParents.add(node.parentId());
                    }
                }
            }
            collectDescendants(nodes, deleteIds);
            deleteIds.forEach(nodes::remove);
        }

        for (var update : list(patch.updateSemanticNodes())) {
            if (update == null || blank(update.semanticKey())) {
                throw new IllegalArgumentException("Semantic update key is required");
            }
            requireAllowedSemantic(nodes, update.semanticKey(), allowed);
            for (var entry : new ArrayList<>(nodes.entrySet())) {
                var old = entry.getValue();
                if (!old.semanticKey().equals(update.semanticKey())) {
                    continue;
                }
                if (old.kind() == NodeKind.ROOT) {
                    throw new IllegalArgumentException("Hierarchy root cannot be semantically replaced");
                }
                var changed = new HierarchyNode(
                    old.id(),
                    old.semanticKey(),
                    old.parentId(),
                    update.kind(),
                    old.relationToParent(),
                    update.name(),
                    update.statement(),
                    update.summary()
                );
                nodes.put(old.id(), normalize(changed));
                if (old.parentId() != null) {
                    touchedParents.add(old.parentId());
                }
            }
        }

        Map<String, String> generatedIds = new LinkedHashMap<>();
        for (var draft : list(patch.addNodes())) {
            if (draft == null || blank(draft.tempId())) {
                throw new IllegalArgumentException("Added hierarchy node tempId is required");
            }
            if (generatedIds.put(draft.tempId(), "node_" + UUID.randomUUID()) != null) {
                throw new IllegalArgumentException("Duplicate hierarchy tempId: " + draft.tempId());
            }
        }
        for (var draft : list(patch.addNodes())) {
            String parentId = generatedIds.getOrDefault(draft.parentRef(), draft.parentRef());
            if (blank(parentId) || !nodes.containsKey(parentId)
                && !generatedIds.containsValue(parentId)) {
                throw new IllegalArgumentException("Added node references missing parent");
            }
            if (!generatedIds.containsValue(parentId) && !allowed.contains(parentId)) {
                throw new IllegalArgumentException("Cannot add node under unrelated branch");
            }
            var node = nodeFromDraft(draft, generatedIds.get(draft.tempId()), parentId);
            nodes.put(node.id(), node);
            addedNodeIds.add(node.id());
            touchedParents.add(parentId);
        }

        for (var move : list(patch.moveNodes())) {
            if (move == null || !allowed.contains(move.nodeId())) {
                throw new IllegalArgumentException("Cannot move node outside routed hierarchy slice");
            }
            var old = nodes.get(move.nodeId());
            if (old == null) {
                throw new IllegalArgumentException("Move references missing node");
            }
            if (old.id().equals(validated.rootNodeId())) {
                throw new IllegalArgumentException("Hierarchy root cannot be moved");
            }
            String newParentId = generatedIds.getOrDefault(
                move.newParentRef(),
                move.newParentRef()
            );
            boolean newParent = addedNodeIds.contains(newParentId);
            if (!nodes.containsKey(newParentId) || !newParent && !allowed.contains(newParentId)) {
                throw new IllegalArgumentException("Move references unrelated parent");
            }
            touchedParents.add(old.parentId());
            touchedParents.add(newParentId);
            nodes.put(
                old.id(),
                new HierarchyNode(
                    old.id(), old.semanticKey(), newParentId, old.kind(),
                    move.relationToParent(), old.name(), old.statement(), old.summary()
                )
            );
        }

        for (var parentId : touchedParents) {
            addAncestors(parentId, nodes, requiredSummaryIds);
        }
        requiredSummaryIds.removeAll(addedNodeIds);
        Map<String, String> suppliedSummaries = new HashMap<>();
        for (var update : list(patch.ancestorSummaries())) {
            if (update != null && !blank(update.nodeId()) && !blank(update.summary())) {
                suppliedSummaries.put(update.nodeId(), update.summary().trim());
            }
        }
        if (!suppliedSummaries.keySet().containsAll(requiredSummaryIds)) {
            var missing = new LinkedHashSet<>(requiredSummaryIds);
            missing.removeAll(suppliedSummaries.keySet());
            throw new IllegalArgumentException(
                "Hierarchy patch must refresh all affected ancestors: " + missing
            );
        }
        for (var entry : suppliedSummaries.entrySet()) {
            var old = nodes.get(entry.getKey());
            if (old == null) {
                throw new IllegalArgumentException("Summary update references missing node");
            }
            nodes.put(
                old.id(),
                new HierarchyNode(
                    old.id(), old.semanticKey(), old.parentId(), old.kind(),
                    old.relationToParent(), old.name(), old.statement(), entry.getValue()
                )
            );
        }

        synchronizeSemanticCopies(nodes);
        return validate(new BoundaryHierarchy(
            validated.boundaryId(),
            validated.rootNodeId(),
            new ArrayList<>(nodes.values()),
            validated.version() + 1
        ));
    }

    public List<HierarchyNode> children(BoundaryHierarchy hierarchy, String parentId) {
        return hierarchy.nodes().stream()
            .filter(node -> parentId.equals(node.parentId()))
            .sorted(Comparator.comparing(HierarchyNode::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public List<HierarchyNode> path(BoundaryHierarchy hierarchy, String nodeId) {
        Map<String, HierarchyNode> byId = nodesById(hierarchy);
        ArrayDeque<HierarchyNode> result = new ArrayDeque<>();
        String cursor = nodeId;
        while (cursor != null) {
            var node = byId.get(cursor);
            if (node == null) {
                throw new IllegalArgumentException("Broken hierarchy path at: " + cursor);
            }
            result.addFirst(node);
            cursor = node.parentId();
        }
        return List.copyOf(result);
    }

    public List<HierarchyNode> overloadedParents(BoundaryHierarchy hierarchy) {
        Map<String, Integer> counts = new HashMap<>();
        for (var node : hierarchy.nodes()) {
            if (node.parentId() != null) {
                counts.merge(node.parentId(), 1, Integer::sum);
            }
        }
        Map<String, HierarchyNode> byId = nodesById(hierarchy);
        return counts.entrySet().stream()
            .filter(entry -> entry.getValue() > REBALANCE_CHILD_THRESHOLD)
            .map(entry -> byId.get(entry.getKey()))
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    public List<HierarchyNode> selectRouteChoices(
        List<HierarchyNode> candidates,
        RouteResponse response,
        int beamWidth
    ) {
        Map<String, HierarchyNode> byId = new LinkedHashMap<>();
        candidates.forEach(candidate -> byId.put(candidate.id(), candidate));
        var selected = list(response == null ? null : response.choices()).stream()
            .filter(choice -> choice != null && byId.containsKey(choice.nodeId()))
            .sorted(Comparator.comparingDouble(RouteChoice::score).reversed())
            .map(RouteChoice::nodeId)
            .distinct()
            .limit(beamWidth)
            .map(byId::get)
            .toList();
        if (selected.isEmpty()) {
            throw new IllegalStateException("Hierarchy router selected no valid child");
        }
        return selected;
    }

    public Map<String, HierarchyNode> nodesById(BoundaryHierarchy hierarchy) {
        Map<String, HierarchyNode> result = new LinkedHashMap<>();
        for (var node : hierarchy.nodes()) {
            result.put(node.id(), node);
        }
        return result;
    }

    private void validateNodeFields(HierarchyNode node) {
        if (node == null || blank(node.id()) || blank(node.semanticKey())
            || node.kind() == null || blank(node.name()) || blank(node.summary())) {
            throw new IllegalArgumentException("Hierarchy node has missing required fields");
        }
        if ((node.kind() == NodeKind.FACT || node.kind() == NodeKind.CONDITION)
            && blank(node.statement())) {
            throw new IllegalArgumentException(
                "FACT and CONDITION nodes require precise statements: " + node.id()
            );
        }
        if (node.parentId() != null
            && (blank(node.relationToParent())
                || !RELATION_PATTERN.matcher(node.relationToParent()).matches())) {
            throw new IllegalArgumentException(
                "relationToParent must be UPPER_SNAKE_CASE: " + node.id()
            );
        }
    }

    private void validateDepthAndCycles(
        String nodeId,
        Map<String, HierarchyNode> byId
    ) {
        Set<String> visited = new HashSet<>();
        String cursor = nodeId;
        int depth = 0;
        while (cursor != null) {
            if (!visited.add(cursor)) {
                throw new IllegalArgumentException("Hierarchy contains a cycle at: " + cursor);
            }
            var node = byId.get(cursor);
            if (node == null) {
                throw new IllegalArgumentException("Hierarchy contains missing parent");
            }
            cursor = node.parentId();
            depth++;
            if (depth > MAX_DEPTH) {
                throw new IllegalArgumentException(
                    "Hierarchy exceeds maximum depth " + MAX_DEPTH
                );
            }
        }
    }

    private void validateSemanticCopies(Iterable<HierarchyNode> nodes) {
        Map<String, HierarchyNode> firstByKey = new HashMap<>();
        for (var node : nodes) {
            var first = firstByKey.putIfAbsent(node.semanticKey(), node);
            if (first == null) {
                continue;
            }
            if (first.kind() != node.kind()
                || !first.name().equals(node.name())
                || !equal(first.statement(), node.statement())
                || !first.summary().equals(node.summary())) {
                throw new IllegalArgumentException(
                    "Semantic copies disagree: " + node.semanticKey()
                );
            }
        }
    }

    private void synchronizeSemanticCopies(Map<String, HierarchyNode> nodes) {
        Map<String, HierarchyNode> canonical = new LinkedHashMap<>();
        for (var node : nodes.values()) {
            canonical.putIfAbsent(node.semanticKey(), node);
        }
        for (var entry : new ArrayList<>(nodes.entrySet())) {
            var old = entry.getValue();
            var source = canonical.get(old.semanticKey());
            nodes.put(
                old.id(),
                new HierarchyNode(
                    old.id(), old.semanticKey(), old.parentId(), source.kind(),
                    old.relationToParent(), source.name(), source.statement(), source.summary()
                )
            );
        }
    }

    private HierarchyNode nodeFromDraft(
        HierarchyNodeDraft draft,
        String id,
        String parentId
    ) {
        if (draft.kind() == NodeKind.ROOT) {
            throw new IllegalArgumentException("Patch cannot add another root");
        }
        return normalize(new HierarchyNode(
            id,
            draft.semanticKey(),
            parentId,
            draft.kind(),
            draft.relationToParent(),
            draft.name(),
            draft.statement(),
            draft.summary()
        ));
    }

    private void collectDescendants(
        Map<String, HierarchyNode> nodes,
        Set<String> deleteIds
    ) {
        boolean changed;
        do {
            changed = false;
            for (var node : nodes.values()) {
                if (node.parentId() != null && deleteIds.contains(node.parentId())) {
                    changed |= deleteIds.add(node.id());
                }
            }
        } while (changed);
    }

    private void requireAllowedSemantic(
        Map<String, HierarchyNode> nodes,
        String semanticKey,
        Set<String> allowed
    ) {
        boolean found = nodes.values().stream()
            .anyMatch(node ->
                node.semanticKey().equals(semanticKey) && allowed.contains(node.id()));
        if (!found) {
            throw new IllegalArgumentException(
                "Cannot change semantic node outside routed hierarchy slice"
            );
        }
    }

    private void addAncestors(
        String nodeId,
        Map<String, HierarchyNode> nodes,
        Set<String> result
    ) {
        String cursor = nodeId;
        while (cursor != null) {
            var node = nodes.get(cursor);
            if (node == null) {
                return;
            }
            result.add(node.id());
            cursor = node.parentId();
        }
    }

    private HierarchyNode normalize(HierarchyNode node) {
        if (node == null) {
            return null;
        }
        return new HierarchyNode(
            trim(node.id()),
            trim(node.semanticKey()),
            nullableTrim(node.parentId()),
            node.kind(),
            nullableTrim(node.relationToParent()),
            trim(node.name()),
            nullableTrim(node.statement()),
            trim(node.summary())
        );
    }

    private HierarchyNodeView view(HierarchyNode node, int childCount) {
        return new HierarchyNodeView(
            node.id(),
            node.semanticKey(),
            node.kind(),
            node.relationToParent(),
            node.name(),
            node.statement(),
            node.summary(),
            childCount
        );
    }

    private boolean equal(String left, String right) {
        return java.util.Objects.equals(left, right);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String nullableTrim(String value) {
        return blank(value) ? null : value.trim();
    }

    private <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }
}
