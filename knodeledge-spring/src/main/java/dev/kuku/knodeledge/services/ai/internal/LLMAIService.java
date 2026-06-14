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
    @Traced(value = "ai.ingest-note", type = KnodeledgeImportanceLevel.SERVICE)
    @Override
    public void ingestNote(String note, String contextBoundaryId, String actorId) {
        tracer.log("IngestNote " + note + " within " + contextBoundaryId + " for " + actorId);
        if (StringUtil.isNullOrEmpty(note)) {
            tracer.log("Note is null or empty!");
        }

        // Retrieve context and graph in parallel
        var contextOp = CompletableFuture.supplyAsync(() -> contextBoundaryService.getContextBoundaryById(contextBoundaryId, actorId));
        var graphOp = CompletableFuture.supplyAsync(() -> graphService.getCompleteGraphByBoundaryId(contextBoundaryId, actorId));
        CompletableFuture.allOf(contextOp, graphOp).join();
        var context = contextOp.join();
        var graph = graphOp.join();

        // Feed into LLM
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
                        """, context.name(), context.context(), graph, note))
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

        // 2. Merge Edges (with Preference Supersession)
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

        // Process existing edges
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
                newPref.context()
            ));
        }

        // Add new events and states
        for (var edge : newEventsAndStates) {
            activeEdges.add(new EdgeDto(
                edge.source(),
                edge.target(),
                edge.predicate(),
                edge.context()
            ));
        }

        // 3. Save to database
        graphService.saveGraph(contextBoundaryId, new ArrayList<>(mergedNodes.values()), activeEdges);
        tracer.log("Saved updated graph to database for boundary: " + contextBoundaryId);
    }

    private boolean isSuperseded(EdgeDto existing, ExtractedEdgeDto newEdge) {
        // 1. If source, target, and predicate are identical, the new one always overrides the old one (updates context/metadata)
        if (existing.source().equals(newEdge.source()) && 
            existing.target().equals(newEdge.target()) && 
            existing.predicate().equals(newEdge.predicate())) {
            return true;
        }

        // 2. Preference mutation (e.g. LIKES -> DISLIKES or vice versa for the same target)
        if (existing.source().equals(newEdge.source()) && existing.target().equals(newEdge.target())) {
            if (isPreferencePredicate(existing.predicate()) && isPreferencePredicate(newEdge.predicate())) {
                return true;
            }
        }

        // 3. Singular preferences: if the predicate is singular (starts with FAVORITE or CURRENT), a new value replaces the old value regardless of target.
        if (existing.source().equals(newEdge.source()) && existing.predicate().equals(newEdge.predicate())) {
            String pred = existing.predicate().toUpperCase();
            if (pred.startsWith("FAVORITE") || pred.startsWith("CURRENT") || pred.equals("FAVORITE")) {
                return true;
            }
        }

        return false;
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
