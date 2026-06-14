package dev.kuku.knodeledge.services.ai.internal;

import dev.kuku.knodeledge.infra.topo_tracer.KnodeledgeImportanceLevel;
import dev.kuku.knodeledge.infra.topo_tracer.Traced;
import dev.kuku.knodeledge.services.ai.AIService;
import dev.kuku.knodeledge.services.context_boundary.ContextBoundaryService;
import dev.kuku.knodeledge.services.graph.GraphService;
import dev.kuku.topotracer.sdk.Tracer;
import io.netty.util.internal.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.IngestionResponse;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.NodeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.EdgeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.ExtractedEdgeDto;

/**
 * LLM focused AIService work-flow
 */
@Service
@RequiredArgsConstructor
public class LLMAIService implements AIService {

    private final Tracer tracer;
    private final ChatClient chatClient;
    private final ContextBoundaryService contextBoundaryService;
    private final GraphService graphService;

    @Value("classpath:/prompts/entity_context_node_edge_prompt.st")
    private Resource nodeEdgePromptResource;

    @Value("classpath:/prompts/entity_context_ingest_prompt.st")
    private Resource ingestNotePromptResource;

    @Value("classpath:/prompts/entity_context_clean_prompt.st")
    private Resource cleanNotePromptResource;

    private String getPrompt(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read prompt resource from classpath", e);
        }
    }

    /**
     * Phase 1:- Feed the entire context + existing graph(s) + note.
     */
    @Traced(
        value = "ai.ingest-note",
        type = KnodeledgeImportanceLevel.SERVICE,
        includeArguments = true,
        maxArgumentLength = 160)
    @Override
    public void ingestNote(String note, String contextBoundaryId, String actorId) {
        tracer.log("IngestNote " + note + " within " + contextBoundaryId + " for " + actorId);
        if (StringUtil.isNullOrEmpty(note)) {
            tracer.log("Note is null or empty!");
        }

        // Retrieve context and graph in parallel
        var parallel = tracer.parallel();
        var contextOp = CompletableFuture.supplyAsync(parallel.wrapSupplier(
            () -> contextBoundaryService.getContextBoundaryById(contextBoundaryId, actorId)));
        var graphOp = CompletableFuture.supplyAsync(parallel.wrapSupplier(
            () -> graphService.getCompleteGraphByBoundaryId(contextBoundaryId, actorId)));
        CompletableFuture.allOf(contextOp, graphOp).join();
        parallel.join();
        var context = contextOp.join();
        var graph = graphOp.join();

        // Stage 1: Preprocess and clean/simplify the note
        String cleanedNote = chatClient.prompt()
                .system(getPrompt(this.cleanNotePromptResource))
                .user(String.format("""
                        Subject Name: %s
                        Subject Context: %s
                        
                        Raw Note:
                        %s
                        """, context.name(), context.context(), note))
                .call()
                .content();

        tracer.log("Preprocessed Cleaned Note: \n" + cleanedNote);

        // Stage 2: Feed clean note into extraction and alignment LLM
        IngestionResponse response = chatClient.prompt()
                .system(getPrompt(this.ingestNotePromptResource))
                .user(String.format("""
                        Context Boundary:
                        Name: %s
                        Description: %s
                        
                        Existing Graph:
                        %s
                        
                        New Note:
                        %s
                        """, context.name(), context.context(), graph, cleanedNote))
                .call()
                .entity(IngestionResponse.class);

        if (response == null) {
            throw new RuntimeException("Failed to ingest note and generate graph updates");
        }

        tracer.log("Successfully processed note ingestion", Map.of(
                "extractedNodesCount", String.valueOf(response.nodes().size()),
                "extractedEdgesCount", String.valueOf(response.edges().size())
        ));

        for (var node : response.nodes()) {
            tracer.log("Extracted Candidate Node: " + node.label() + " (" + node.confidence() + ")");
        }
        for (var edge : response.edges()) {
            tracer.log("Extracted Candidate Edge: " + edge.source() + " -" + edge.predicate() + "-> " + edge.target() + " (" + edge.taxonomyType() + ", " + edge.confidence() + ")");
        }

        // 1. Merge Nodes
        Map<String, NodeDto> mergedNodes = new LinkedHashMap<>();
        for (var node : graph.nodes()) {
            mergedNodes.put(node.id(), node);
        }
        for (var extNode : response.nodes()) {
            mergedNodes.put(extNode.id(), new NodeDto(
                extNode.id(),
                extNode.label(),
                extNode.categories(),
                extNode.description()
            ));
        }

        // 2. Merge Edges (with Preference Supersession and Conditional Coexistence)
        List<EdgeDto> activeEdges = new ArrayList<>();
        List<ExtractedEdgeDto> newPreferences = new ArrayList<>();
        List<ExtractedEdgeDto> newEventsAndStates = new ArrayList<>();

        for (var edge : response.edges()) {
            if ("PREFERENCE".equalsIgnoreCase(edge.taxonomyType())) {
                newPreferences.add(edge);
            } else {
                newEventsAndStates.add(edge);
            }
        }

        // Process existing edges — keep unless superseded by a new edge with the same condition set
        for (var existingEdge : graph.edges()) {
            boolean superseded = false;
            for (var newEdge : response.edges()) {
                if (isSuperseded(existingEdge, newEdge)) {
                    superseded = true;
                    tracer.log("Edge superseded: " + existingEdge.source() + " -" + existingEdge.predicate() + "-> " + existingEdge.target() + " replaced by " + newEdge.predicate() + " -> " + newEdge.target());
                    break;
                }
            }
            if (!superseded) {
                activeEdges.add(existingEdge);
            }
        }

        // Add new preferences
        for (var newPref : newPreferences) {
            activeEdges.add(new EdgeDto(
                newPref.source(),
                newPref.target(),
                newPref.predicate(),
                newPref.context(),
                newPref.conditions() != null ? newPref.conditions() : List.of()
            ));
        }

        // Add new events and states
        for (var edge : newEventsAndStates) {
            activeEdges.add(new EdgeDto(
                edge.source(),
                edge.target(),
                edge.predicate(),
                edge.context(),
                edge.conditions() != null ? edge.conditions() : List.of()
            ));
        }

        // Auto-generate CONDITIONED_BY edges for conditional relationships.
        // For each extracted edge with non-empty conditions, create a traversal edge
        // from source -> condition_node so the chatbot/graph DB can traverse conditions
        // without string matching. See docs/conditional-preferences-schema.md.
        for (var edge : response.edges()) {
            if (edge.conditions() == null || edge.conditions().isEmpty()) continue;
            for (var conditionNodeId : edge.conditions()) {
                // Only create the CONDITIONED_BY edge if the condition node actually exists
                if (!mergedNodes.containsKey(conditionNodeId)) {
                    tracer.log("CONDITIONED_BY skipped — condition node not in graph: " + conditionNodeId);
                    continue;
                }
                var condByEdge = new EdgeDto(
                    edge.source(),
                    conditionNodeId,
                    "CONDITIONED_BY",
                    String.format("Condition for %s %s %s", edge.source(), edge.predicate(), edge.target()),
                    List.of()
                );
                // Avoid duplicates if the same CONDITIONED_BY edge already exists
                boolean alreadyPresent = activeEdges.stream().anyMatch(e ->
                    e.source().equals(condByEdge.source()) &&
                    e.target().equals(condByEdge.target()) &&
                    e.predicate().equals("CONDITIONED_BY")
                );
                if (!alreadyPresent) {
                    activeEdges.add(condByEdge);
                    tracer.log("Auto-generated CONDITIONED_BY: " + condByEdge.source() + " -> " + condByEdge.target());
                }
            }
        }

        // 3. Save to database
        graphService.saveGraph(contextBoundaryId, new ArrayList<>(mergedNodes.values()), activeEdges);
        tracer.log("Saved updated graph to database for boundary: " + contextBoundaryId);
    }

    /**
     * Determines whether an existing edge should be replaced by a newly extracted edge.
     *
     * <h3>Conditional Preference Rule (Hybrid Schema)</h3>
     * <p>An unconditional edge and a conditional edge targeting the same entity
     * ALWAYS coexist — they represent different semantic facts.
     * Supersession only applies when both edges share the same condition set.
     * See {@code docs/conditional-preferences-schema.md} for full rationale.</p>
     *
     * <p>Supersession cases:</p>
     * <ul>
     *   <li>Exact triple match (same source, target, predicate, same conditions) → update in-place.</li>
     *   <li>Preference flip (LIKES ↔ DISLIKES) on the same target with the same conditions → supersede.</li>
     *   <li>Singular predicate (FAVORITE*, CURRENT*) on same source with same conditions → supersede.</li>
     *   <li>Differing condition sets → ALWAYS coexist, never supersede.</li>
     * </ul>
     */
    private boolean isSuperseded(EdgeDto existing, ExtractedEdgeDto newEdge) {
        // Edges with different condition sets always coexist — never supersede each other.
        // This is the core rule of the hybrid conditional preferences schema.
        if (!sameConditionSet(existing.conditions(), newEdge.conditions())) {
            return false;
        }

        // 1. Exact triple: same source, target, predicate, same conditions → update in-place
        if (existing.source().equals(newEdge.source()) &&
            existing.target().equals(newEdge.target()) &&
            existing.predicate().equals(newEdge.predicate())) {
            return true;
        }

        // 2. Preference flip (LIKES <-> DISLIKES) on the same target, same condition set
        if (existing.source().equals(newEdge.source()) && existing.target().equals(newEdge.target())) {
            if (isPreferencePredicate(existing.predicate()) && isPreferencePredicate(newEdge.predicate())) {
                return true;
            }
        }

        // 3. Singular predicates (FAVORITE*, CURRENT*): a new value replaces the old one
        //    regardless of target, but only when conditions also match.
        if (existing.source().equals(newEdge.source()) && existing.predicate().equals(newEdge.predicate())) {
            String pred = existing.predicate().toUpperCase();
            if (pred.startsWith("FAVORITE") || pred.startsWith("CURRENT")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if both condition sets contain the same node IDs (order-insensitive).
     * Null and empty list are treated as equivalent (both mean "unconditional").
     */
    private boolean sameConditionSet(List<String> a, List<String> b) {
        var setA = a == null ? Set.of() : new java.util.HashSet<>(a);
        var setB = b == null ? Set.of() : new java.util.HashSet<>(b);
        return setA.equals(setB);
    }

    private static final Set<String> PREFERENCE_PREDICATES = Set.of("LIKES", "DISLIKES", "FAVORITE");

    private boolean isPreferencePredicate(String predicate) {
        if (predicate == null) {
            return false;
        }
        String p = predicate.toUpperCase();
        return PREFERENCE_PREDICATES.contains(p) || p.startsWith("FAVORITE_");
    }
}
