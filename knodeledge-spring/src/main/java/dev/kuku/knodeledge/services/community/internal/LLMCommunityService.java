package dev.kuku.knodeledge.services.community.internal;

import dev.kuku.knodeledge.repositories.CommunityRepository;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.GraphResponse;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.GraphPatch;
import dev.kuku.knodeledge.services.community.CommunityService;
import dev.kuku.knodeledge.services.community.model.CommunityModels.Community;
import dev.kuku.knodeledge.services.community.model.CommunityModels.CommunityHierarchy;
import dev.kuku.knodeledge.services.community.model.CommunityModels.CommunityUpdateResponse;
import dev.kuku.knodeledge.services.community.model.CommunityModels.RetrievalResult;
import dev.kuku.knodeledge.services.community.model.CommunityModels.RouteResponse;
import dev.kuku.knodeledge.services.community.model.CommunityModels.RoutingStep;
import dev.kuku.knodeledge.services.context_boundary.ContextBoundaryService;
import dev.kuku.knodeledge.services.graph.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LLMCommunityService implements CommunityService {
    private static final int BEAM_WIDTH = 2;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final CommunityRepository communityRepository;
    private final CommunityHierarchyProcessor processor;
    private final ContextBoundaryService contextBoundaryService;
    private final GraphService graphService;
    private final BoundaryLockManager lockManager;

    @Value("classpath:/prompts/LLM-flow/07_build_community_hierarchy.st")
    private Resource buildHierarchyPromptResource;

    @Value("classpath:/prompts/LLM-flow/08_route_communities.st")
    private Resource routePromptResource;

    @Value("classpath:/prompts/LLM-flow/09_update_community_hierarchy.st")
    private Resource updateHierarchyPromptResource;

    @Override
    public RetrievalPackage prepare(String query, String contextBoundaryId, String actorId) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Retrieval query must not be blank");
        }
        return lockManager.withLock(contextBoundaryId, () -> {
            var context = contextBoundaryService.getContextBoundaryById(
                contextBoundaryId,
                actorId
            );
            var graph = graphService.getCompleteGraphByBoundaryId(contextBoundaryId, actorId);
            var hierarchy = ensureHierarchy(
                contextBoundaryId,
                context.name(),
                context.context(),
                graph
            );
            var retrieval = routeAndRetrieve(query, hierarchy, graph);
            return new RetrievalPackage(hierarchy, retrieval, graph);
        });
    }

    @Override
    public CommunityHierarchy getHierarchy(String contextBoundaryId, String actorId) {
        return lockManager.withLock(contextBoundaryId, () -> {
            var context = contextBoundaryService.getContextBoundaryById(
                contextBoundaryId,
                actorId
            );
            var graph = graphService.getCompleteGraphByBoundaryId(contextBoundaryId, actorId);
            return ensureHierarchy(
                contextBoundaryId,
                context.name(),
                context.context(),
                graph
            );
        });
    }

    @Override
    public CommunityHierarchy prepareUpdate(
        String contextBoundaryId,
        CommunityHierarchy hierarchy,
        RetrievalResult retrieval,
        GraphPatch patch,
        GraphResponse finalGraph
    ) {
        Set<String> impactedIds = new LinkedHashSet<>(retrieval.selectedCommunityIds());
        impactedIds.addAll(deletionAffectedCommunityIds(hierarchy, patch));
        var impacted = hierarchySlice(hierarchy, new ArrayList<>(impactedIds));
        var update = callEntity(
            updateHierarchyPromptResource,
            String.format("""
                Selected leaf communities:
                %s

                Relevant hierarchy slice:
                %s

                Applied graph patch:
                %s
                """,
                toJson(retrieval.selectedCommunityIds()),
                toJson(impacted),
                toJson(patch)),
            CommunityUpdateResponse.class,
            "update community hierarchy"
        );
        return processor.applyUpdate(
            hierarchy,
            update,
            patch,
            finalGraph,
            retrieval.selectedCommunityIds()
        );
    }

    @Override
    public void saveHierarchy(String contextBoundaryId, CommunityHierarchy hierarchy) {
        communityRepository.save(contextBoundaryId, hierarchy);
    }

    private CommunityHierarchy ensureHierarchy(
        String boundaryId,
        String boundaryName,
        String boundaryContext,
        GraphResponse graph
    ) {
        var stored = communityRepository.findByBoundaryId(boundaryId);
        if (stored.isPresent()) {
            return processor.validate(stored.get(), graph);
        }

        CommunityHierarchy candidate;
        if (graph.nodes() == null || graph.nodes().isEmpty()) {
            candidate = new CommunityHierarchy(List.of(
                new Community(
                    "community_root",
                    boundaryName + " Knowledge",
                    "Root community for " + boundaryName + ".",
                    null,
                    List.of(),
                    List.of()
                )
            ));
        } else {
            candidate = callEntity(
                buildHierarchyPromptResource,
                String.format("""
                    Context Boundary:
                    Name: %s
                    Description: %s

                    Canonical Graph:
                    %s
                    """, boundaryName, boundaryContext, toJson(graph)),
                CommunityHierarchy.class,
                "build community hierarchy"
            );
        }
        var validated = processor.validate(candidate, graph);
        communityRepository.save(boundaryId, validated);
        return validated;
    }

    private RetrievalResult routeAndRetrieve(
        String query,
        CommunityHierarchy hierarchy,
        GraphResponse graph
    ) {
        List<Community> frontier = processor.roots(hierarchy);
        List<RoutingStep> path = new ArrayList<>();
        List<Community> selected = List.of();

        for (int depth = 0; depth < CommunityHierarchyProcessor.MAX_DEPTH; depth++) {
            if (frontier.isEmpty()) {
                break;
            }
            selected = select(query, frontier);
            path.add(new RoutingStep(
                frontier.stream().map(Community::id).toList(),
                selected.stream().map(Community::id).toList()
            ));

            Set<String> selectedIds = selected.stream()
                .map(Community::id)
                .collect(java.util.stream.Collectors.toSet());
            var children = processor.children(hierarchy, selectedIds);
            Set<String> parentsWithChildren = children.stream()
                .map(Community::parentId)
                .collect(java.util.stream.Collectors.toSet());
            var leaves = selected.stream()
                .filter(community -> !parentsWithChildren.contains(community.id()))
                .toList();

            Map<String, Community> next = new java.util.LinkedHashMap<>();
            for (var leaf : leaves) {
                next.put(leaf.id(), leaf);
            }
            for (var child : children) {
                next.put(child.id(), child);
            }
            if (next.keySet().equals(selectedIds)) {
                break;
            }
            frontier = new ArrayList<>(next.values());
        }

        var selectedIds = selected.stream().map(Community::id).toList();
        var retrievalGraph = processor.retrieve(hierarchy, graph, selectedIds);
        return new RetrievalResult(selectedIds, path, retrievalGraph);
    }

    private List<Community> select(String query, List<Community> candidates) {
        if (candidates.size() == 1) {
            return List.copyOf(candidates);
        }
        var candidateViews = candidates.stream()
            .map(community -> Map.of(
                "id", community.id(),
                "name", community.name(),
                "summary", community.summary()
            ))
            .toList();
        var response = callEntity(
            routePromptResource,
            String.format("""
                Query:
                %s

                Candidate communities:
                %s
                """, query, toJson(candidateViews)),
            RouteResponse.class,
            "route communities"
        );

        return processor.selectRouteChoices(candidates, response, BEAM_WIDTH);
    }

    private List<Community> hierarchySlice(
        CommunityHierarchy hierarchy,
        List<String> selectedCommunityIds
    ) {
        var byId = processor.communitiesById(hierarchy);
        Set<String> included = new LinkedHashSet<>(selectedCommunityIds);
        for (var id : new ArrayList<>(included)) {
            String current = id;
            while (current != null) {
                included.add(current);
                current = byId.get(current).parentId();
            }
        }
        included.addAll(
            processor.children(hierarchy, new HashSet<>(selectedCommunityIds)).stream()
                .map(Community::id)
                .toList()
        );
        return hierarchy.communities().stream()
            .filter(community -> included.contains(community.id()))
            .toList();
    }

    private Set<String> deletionAffectedCommunityIds(
        CommunityHierarchy hierarchy,
        GraphPatch patch
    ) {
        Set<String> deletedNodes = new HashSet<>(list(patch.deleteNodes()));
        Set<String> deletedEdges = list(patch.deleteEdges()).stream()
            .map(edge -> edge.source() + '\u0000' + edge.predicate() + '\u0000' + edge.target())
            .collect(java.util.stream.Collectors.toSet());
        Set<String> result = new LinkedHashSet<>();
        for (var community : hierarchy.communities()) {
            boolean nodeMatch = community.memberNodeIds().stream().anyMatch(deletedNodes::contains);
            boolean edgeMatch = community.memberEdges().stream()
                .map(edge ->
                    edge.source() + '\u0000' + edge.predicate() + '\u0000' + edge.target())
                .anyMatch(deletedEdges::contains);
            if (nodeMatch || edgeMatch) {
                result.add(community.id());
            }
        }
        return result;
    }

    private <T> T callEntity(
        Resource systemPrompt,
        String userPrompt,
        Class<T> responseType,
        String stage
    ) {
        T response = chatClient.prompt()
            .system(getPrompt(systemPrompt))
            .user(userPrompt)
            .call()
            .entity(responseType);
        if (response == null) {
            throw new IllegalStateException("LLM community stage failed: " + stage);
        }
        return response;
    }

    private String getPrompt(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read community prompt", e);
        }
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }
}
