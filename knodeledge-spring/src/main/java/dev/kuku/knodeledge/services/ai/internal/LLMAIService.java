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
import org.springframework.stereotype.Service;

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

    private final String nodeEdgePrompt = """
            You are a highly precise Knowledge Graph Extraction Engine. Your task is to analyze raw text notes, extract static concepts (entities), and map the relationships (edges) between them.
            
            Follow these strict rules:
            1. ENTITY RESOLUTION & NORMALIZATION: Map synonyms and plurals to the same singular, lowercase ID (e.g. "mangoes" -> "mango").
            2. RELATIONSHIP EXTRACTION: Format predicates in UPPER_SNAKE_CASE (e.g. IS_A, BETTER_WITH).
            3. NOISE FILTERING: Ignore conversational or out-of-context statements.
            4. CONTEXT ANCHORING: For every edge, populate the 'context' field with the exact sentence from the notes.
            5. NO HANGING NODES: Every node in your output must be connected by at least one edge to another node.
            """;

    private final String ingestNotePrompt = """
            You are a State-Aware Knowledge Graph Extraction Engine.
            Your task is to analyze a new raw text note and extract new nodes (entities) and edges (relationships), aligning them with the existing knowledge graph in this context.
            
            Rules:
            1. FUZZY SEMANTIC ALIGNING: Compare extracted entities with existing nodes in the provided graph. Reuse existing node IDs if they refer to the same entity (e.g., if the note says "Apple fruit" and "apple" exists, reuse ID "apple").
            2. DEEP CATEGORIZATION & HIERARCHY: For every extracted entity (especially foods, ingredients, activities, colors, concepts, etc.), you MUST deeply infer its categories/parent concepts using "IS_A" relationship edges (e.g. "onion" -> IS_A -> "vegetable", "onion" -> IS_A -> "ingredient", "roses" -> IS_A -> "flower").
            3. COMPOUND ENTITY DECOMPOSITION: If the note contains compound subjects (e.g., "dry fruit in ice cream" or "roses light colour"), break them down. Extract the base entities (e.g., "dry fruit", "ice cream", "roses", "light color") and relate them (e.g., "dry_fruit_in_ice_cream" -> CONTAINS -> "dry_fruit", "roses_light_color" -> HAS_COLOR -> "light_color"). Then extract categories for the base entities (e.g., "dry_fruit" -> IS_A -> "ingredient", "ice_cream" -> IS_A -> "food", "roses" -> IS_A -> "flower").
            4. RELATIONSHIP TAXONOMY: Categorize every relationship (edge) into one of these types:
               - EVENT: Transient or point-in-time actions (e.g., WENT_HIKING, BOUGHT_BOOTS).
               - PREFERENCE: Likes, favorites, dislikes, or opinions that change over time (e.g., FAVORITE_FRUIT, LIKES, DISLIKES).
               - STATE: Semi-permanent attributes or properties that assert truth values (e.g., IS_VEGETARIAN, ALLERGIC_TO).
            5. CONFIDENCE SCORING: Assign a confidence level (HIGH, MEDIUM, LOW) to each extracted node and edge based on how clearly and directly it is stated in the note.
            6. CONTEXT ANCHORING: Populate the 'context' field of every edge with the exact sentence from the note supporting that edge.
            7. NO HANGING/ISOLATED NODES: Every node in your output MUST be connected by at least one edge to another node in the graph. Never extract a node without connecting it.
            """;

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
                .system(this.ingestNotePrompt)
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
