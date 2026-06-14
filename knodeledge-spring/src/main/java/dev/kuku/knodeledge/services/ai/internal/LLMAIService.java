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
import java.util.concurrent.CompletableFuture;

import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.IngestionResponse;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.NodeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.EdgeDto;

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

    @Value("classpath:/prompts/node_edge_prompt.st")
    private Resource nodeEdgePromptResource;

    @Value("classpath:/prompts/ingest_note_prompt.st")
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
        //TODO how to use topo tracer nicely here? I want to show parent having 2 children to contextOp trace and one to graphOp trace but challenge is how to represent join? next trace log will have 2 parent?

        // Retrieve context and graph in parallel
        var contextOp = CompletableFuture.supplyAsync(() -> contextBoundaryService.getContextBoundaryById(contextBoundaryId, actorId));
        var graphOp = CompletableFuture.supplyAsync(() -> graphService.getCompleteGraphByBoundaryId(contextBoundaryId, actorId));
        CompletableFuture.allOf(contextOp, graphOp).join();
        var context = contextOp.join();
        var graph = graphOp.join();

        //Feed into LLM
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

        tracer.log("Successfully processed note ingestion", java.util.Map.of(
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
        java.util.Map<String, NodeDto> mergedNodes = new java.util.LinkedHashMap<>();
        for (var node : graph.nodes()) {
            mergedNodes.put(node.id(), node);
        }
        for (var extNode : response.nodes()) {
            mergedNodes.put(extNode.id(), new NodeDto(
                extNode.id(),
                extNode.label(),
                extNode.category(),
                extNode.description()
            ));
        }

        // 2. Merge Edges (with Preference Supersession)
        java.util.List<EdgeDto> activeEdges = new java.util.ArrayList<>();
        java.util.List<dev.kuku.knodeledge.services.ai.internal.models.GraphDto.ExtractedEdgeDto> newPreferences = new java.util.ArrayList<>();
        java.util.List<dev.kuku.knodeledge.services.ai.internal.models.GraphDto.ExtractedEdgeDto> newEventsAndStates = new java.util.ArrayList<>();

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
            for (var newPref : newPreferences) {
                if (existingEdge.source().equals(newPref.source()) && existingEdge.predicate().equals(newPref.predicate())) {
                    superseded = true;
                    tracer.log("Edge superseded (Preference change): " + existingEdge.source() + " -" + existingEdge.predicate() + "-> " + existingEdge.target() + " replaced by " + newPref.target());
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
        graphService.saveGraph(contextBoundaryId, new java.util.ArrayList<>(mergedNodes.values()), activeEdges);
        tracer.log("Saved updated graph to database for boundary: " + contextBoundaryId);
    }
}
