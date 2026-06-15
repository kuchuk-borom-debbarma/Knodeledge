package dev.kuku.knodeledge.services.hierarchy.internal;

import dev.kuku.knodeledge.repositories.HierarchyRepository;
import dev.kuku.knodeledge.repositories.LegacyGraphRepository;
import dev.kuku.knodeledge.services.ai.cache.CachedPromptExecutor;
import dev.kuku.knodeledge.services.context_boundary.ContextBoundaryService;
import dev.kuku.knodeledge.services.context_boundary.dto.ContextBoundary;
import dev.kuku.knodeledge.services.hierarchy.HierarchyService;
import dev.kuku.knodeledge.services.hierarchy.legacy.LegacyGraphModels.GraphResponse;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.AnswerContextNode;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.BoundaryHierarchy;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyLevelResponse;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyNode;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyNodeView;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyPatch;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.KnowledgeExtraction;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.KnowledgeItem;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.RebalanceResponse;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.RouteResponse;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.RoutedKnowledge;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.RoutingResult;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.RoutingStep;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LLMHierarchyService implements HierarchyService {
    private static final int BEAM_WIDTH = 2;

    private final CachedPromptExecutor promptExecutor;
    private final ObjectMapper objectMapper;
    private final HierarchyRepository hierarchyRepository;
    private final LegacyGraphRepository legacyGraphRepository;
    private final ContextBoundaryService contextBoundaryService;
    private final HierarchyFactory hierarchyFactory;
    private final HierarchyProcessor processor;
    private final BoundaryLockManager lockManager;

    @Value("classpath:/prompts/hierarchy/01_extract_knowledge.st")
    private Resource extractPrompt;

    @Value("classpath:/prompts/hierarchy/02_route.st")
    private Resource routePrompt;

    @Value("classpath:/prompts/hierarchy/03_patch.st")
    private Resource patchPrompt;

    @Value("classpath:/prompts/hierarchy/04_rebalance.st")
    private Resource rebalancePrompt;

    @Value("classpath:/prompts/hierarchy/05_answer.st")
    private Resource answerPrompt;

    @Value("classpath:/prompts/hierarchy/06_migrate_legacy_graph.st")
    private Resource migrationPrompt;

    @Override
    public void ingest(String note, String boundaryId, String actorId) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("Note must not be blank");
        }
        lockManager.withLock(boundaryId, () -> ingestLocked(note, boundaryId, actorId));
    }

    @Override
    public String answer(String prompt, String boundaryId, String actorId) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be blank");
        }
        return lockManager.withLock(boundaryId, () -> {
            ContextBoundary boundary = contextBoundaryService.getContextBoundaryById(
                boundaryId,
                actorId
            );
            BoundaryHierarchy hierarchy = ensureHierarchy(boundary);
            RoutingResult routing = route(prompt, hierarchy);
            Map<String, HierarchyNode> byId = processor.nodesById(hierarchy);
            List<AnswerContextNode> context = routing.selectedNodeIds().stream()
                .map(byId::get)
                .map(node -> new AnswerContextNode(
                    node,
                    processor.path(hierarchy, node.id())
                ))
                .toList();
            String answer = promptExecutor.text(
                "answer-hierarchy",
                prompt(answerPrompt),
                String.format("""
                    Boundary:
                    Name: %s
                    Description: %s

                    Selected hierarchy context:
                    %s

                    User question:
                    %s
                    """,
                    boundary.name(),
                    boundary.context(),
                    json(context),
                    prompt)
            );
            if (answer == null || answer.isBlank()) {
                throw new IllegalStateException("Hierarchy answer returned empty content");
            }
            return answer;
        });
    }

    @Override
    public HierarchyLevelResponse getLevel(
        String boundaryId,
        String actorId,
        String nodeId
    ) {
        return lockManager.withLock(boundaryId, () -> {
            var boundary = contextBoundaryService.getContextBoundaryById(boundaryId, actorId);
            return processor.level(ensureHierarchy(boundary), nodeId);
        });
    }

    @Override
    public BoundaryHierarchy getDebugHierarchy(String boundaryId, String actorId) {
        return lockManager.withLock(boundaryId, () -> {
            var boundary = contextBoundaryService.getContextBoundaryById(boundaryId, actorId);
            return ensureHierarchy(boundary);
        });
    }

    private void ingestLocked(String note, String boundaryId, String actorId) {
        ContextBoundary boundary = contextBoundaryService.getContextBoundaryById(
            boundaryId,
            actorId
        );
        BoundaryHierarchy hierarchy = ensureHierarchy(boundary);
        KnowledgeExtraction extraction = entity(
            extractPrompt,
            String.format("""
                Boundary:
                Name: %s
                Description: %s

                Raw note:
                %s
                """, boundary.name(), boundary.context(), note),
            KnowledgeExtraction.class,
            "extract-hierarchy-knowledge"
        );
        List<KnowledgeItem> items = list(extraction.items()).stream()
            .filter(java.util.Objects::nonNull)
            .toList();
        if (items.isEmpty()) {
            throw new IllegalStateException("Knowledge extraction returned no items");
        }

        Set<String> semanticKeys = new LinkedHashSet<>();
        items.forEach(item -> collectSemanticKeys(item, semanticKeys));
        List<RoutedKnowledge> routed = new ArrayList<>();
        Set<String> allowedNodeIds = new LinkedHashSet<>();
        for (var item : items) {
            RoutingResult routing = route(routeQuery(item), hierarchy);
            var slice = processor.sliceWithSemanticCopies(
                hierarchy,
                routing.selectedNodeIds(),
                semanticKeys
            );
            slice.nodes().forEach(node -> allowedNodeIds.add(node.id()));
            routed.add(new RoutedKnowledge(item, routing.selectedNodeIds(), slice));
        }

        HierarchyPatch patch = entity(
            patchPrompt,
            String.format("""
                Boundary:
                Name: %s
                Description: %s

                Extracted knowledge:
                %s

                Routed local hierarchy slices:
                %s
                """,
                boundary.name(),
                boundary.context(),
                json(extraction),
                json(routed)),
            HierarchyPatch.class,
            "patch-hierarchy"
        );
        Set<String> previousNodeIds = new LinkedHashSet<>(
            processor.nodesById(hierarchy).keySet()
        );
        BoundaryHierarchy updated = processor.applyPatch(hierarchy, patch, allowedNodeIds);
        updated = rebalanceAffected(updated, allowedNodeIds, previousNodeIds);
        hierarchyRepository.save(updated);
    }

    private BoundaryHierarchy rebalanceAffected(
        BoundaryHierarchy hierarchy,
        Set<String> routedNodeIds,
        Set<String> previousNodeIds
    ) {
        BoundaryHierarchy result = hierarchy;
        for (var parent : processor.overloadedParents(result)) {
            boolean newlyAffected = !previousNodeIds.contains(parent.id())
                || processor.children(result, parent.id()).stream()
                    .anyMatch(child -> !previousNodeIds.contains(child.id()));
            if (!routedNodeIds.contains(parent.id()) && !newlyAffected) {
                continue;
            }
            var level = processor.level(result, parent.id());
            RebalanceResponse response = entity(
                rebalancePrompt,
                String.format("""
                    Overloaded hierarchy level:
                    %s
                    """, json(level)),
                RebalanceResponse.class,
                "rebalance-hierarchy"
            );
            if (!response.changeNeeded() || response.patch() == null) {
                continue;
            }
            Set<String> allowed = new LinkedHashSet<>();
            level.breadcrumbs().forEach(node -> allowed.add(node.id()));
            level.children().forEach(node -> allowed.add(node.id()));
            allowed.add(parent.id());
            result = processor.applyPatch(result, response.patch(), allowed);
        }
        return result;
    }

    private RoutingResult route(String query, BoundaryHierarchy hierarchy) {
        Map<String, HierarchyNode> byId = processor.nodesById(hierarchy);
        List<HierarchyNode> active = List.of(byId.get(hierarchy.rootNodeId()));
        Map<String, HierarchyNode> terminal = new LinkedHashMap<>();
        List<RoutingStep> path = new ArrayList<>();

        for (int depth = 0; depth < HierarchyProcessor.MAX_DEPTH; depth++) {
            Map<String, HierarchyNode> candidateMap = new LinkedHashMap<>();
            for (var node : active) {
                for (var child : processor.children(hierarchy, node.id())) {
                    candidateMap.put(child.id(), child);
                }
            }
            List<HierarchyNode> candidates = new ArrayList<>(candidateMap.values());
            if (candidates.isEmpty()) {
                active.forEach(node -> terminal.put(node.id(), node));
                break;
            }

            RouteResponse response = entity(
                routePrompt,
                String.format("""
                    Query:
                    %s

                    Current nodes:
                    %s

                    Candidate children:
                    %s
                    """,
                    query,
                    json(active.stream().map(this::routeView).toList()),
                    json(candidates.stream().map(this::routeView).toList())),
                RouteResponse.class,
                "route-hierarchy"
            );
            if (response.stopAtCurrent()) {
                active.forEach(node -> terminal.put(node.id(), node));
                break;
            }

            List<HierarchyNode> selected = processor.selectRouteChoices(
                candidates,
                response,
                BEAM_WIDTH
            );
            path.add(new RoutingStep(
                candidates.stream().map(HierarchyNode::id).toList(),
                selected.stream().map(HierarchyNode::id).toList()
            ));
            List<HierarchyNode> next = new ArrayList<>();
            for (var node : selected) {
                if (processor.children(hierarchy, node.id()).isEmpty()) {
                    terminal.put(node.id(), node);
                } else {
                    next.add(node);
                }
            }
            if (next.isEmpty()) {
                break;
            }
            active = next;
        }

        if (terminal.isEmpty()) {
            active.forEach(node -> terminal.put(node.id(), node));
        }
        return new RoutingResult(new ArrayList<>(terminal.keySet()), path);
    }

    private BoundaryHierarchy ensureHierarchy(ContextBoundary boundary) {
        var stored = hierarchyRepository.findByBoundaryId(boundary.id());
        if (stored.isPresent()) {
            return processor.validate(stored.get());
        }

        GraphResponse legacy = new GraphResponse(
            legacyGraphRepository.findNodesByBoundaryId(boundary.id()),
            legacyGraphRepository.findEdgesByBoundaryId(boundary.id())
        );
        BoundaryHierarchy hierarchy;
        if (!legacy.nodes().isEmpty() || !legacy.edges().isEmpty()) {
            hierarchy = entity(
                migrationPrompt,
                String.format("""
                    Boundary:
                    ID: %s
                    Name: %s
                    Description: %s

                    Legacy canonical graph:
                    %s
                    """,
                    boundary.id(),
                    boundary.name(),
                    boundary.context(),
                    json(legacy)),
                BoundaryHierarchy.class,
                "migrate-legacy-graph"
            );
        } else {
            hierarchy = hierarchyFactory.create(boundary);
        }
        hierarchy = processor.validate(hierarchy);
        hierarchyRepository.save(hierarchy);
        return hierarchy;
    }

    private HierarchyNodeView routeView(HierarchyNode node) {
        return new HierarchyNodeView(
            node.id(),
            node.semanticKey(),
            node.kind(),
            node.relationToParent(),
            node.name(),
            null,
            node.summary(),
            0
        );
    }

    private void collectSemanticKeys(KnowledgeItem item, Set<String> result) {
        if (item == null) {
            return;
        }
        if (item.semanticKey() != null && !item.semanticKey().isBlank()) {
            result.add(item.semanticKey());
        }
        list(item.children()).forEach(child -> collectSemanticKeys(child, result));
    }

    private String routeQuery(KnowledgeItem item) {
        if (item.statement() != null && !item.statement().isBlank()) {
            return item.statement();
        }
        return item.name() + ": " + item.summary();
    }

    private <T> T entity(
        Resource systemPrompt,
        String userPrompt,
        Class<T> responseType,
        String stage
    ) {
        T result = promptExecutor.entity(
            stage,
            prompt(systemPrompt),
            userPrompt,
            responseType
        );
        if (result == null) {
            throw new IllegalStateException("LLM hierarchy stage failed: " + stage);
        }
        return result;
    }

    private String prompt(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read hierarchy prompt", error);
        }
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }
}
