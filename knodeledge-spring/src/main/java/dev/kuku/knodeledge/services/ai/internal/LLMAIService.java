package dev.kuku.knodeledge.services.ai.internal;

import dev.kuku.knodeledge.infra.topo_tracer.KnodeledgeImportanceLevel;
import dev.kuku.knodeledge.infra.Traced;

import dev.kuku.knodeledge.services.ai.AIService;
import dev.kuku.knodeledge.services.ai.dto.Kedge;
import dev.kuku.knodeledge.services.ai.dto.Kgraph;
import dev.kuku.knodeledge.services.ai.dto.Knode;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.GraphResponse;
import dev.kuku.topotracer.sdk.Tracer;
import io.netty.util.internal.StringUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM focused AIService work-flow
 */
@Service
public class LLMAIService implements AIService {

    private final Tracer tracer;
    private final ChatClient chatClient;

    public LLMAIService(Tracer tracer, ChatClient.Builder chatClientBuilder) {
        this.tracer = tracer;
        this.chatClient = chatClientBuilder.build();
    }

    private final String nodeEdgePrompt = """
            You are a highly precise Knowledge Graph Extraction Engine. Your task is to analyze raw text notes, extract static concepts (entities), and map the relationships (edges) between them.
            
            Follow these strict rules:
            1. ENTITY RESOLUTION & NORMALIZATION: Map synonyms and plurals to the same singular, lowercase ID (e.g. "mangoes" -> "mango").
            2. RELATIONSHIP EXTRACTION: Format predicates in UPPER_SNAKE_CASE (e.g. IS_A, BETTER_WITH).
            3. NOISE FILTERING: Ignore conversational or out-of-context statements.
            4. CONTEXT ANCHORING: For every edge, populate the 'context' field with the exact sentence from the notes.
            """;

    /**
     * LLM Focused solution for generating local graph. <br>
     * 1. Generate Local Graph
     * 2.
     */
    @Traced(value = "ai.process-notes", type = KnodeledgeImportanceLevel.SERVICE)
    public Kgraph generateLocalGraphFromNotes(ArrayList<String> notes) {
        tracer.log("LLM focused Local Graph Generator");
        if (notes == null || notes.isEmpty()) {
            return new Kgraph(List.of(), List.of());
        }

        // 1. Join all notes into a single context block
        String notesContent = String.join("\n---\n", notes);
        // 2. Define prompt template and extract structured graph
        GraphResponse graph = chatClient.prompt()
                .system(this.nodeEdgePrompt)
                .user("Analyze the following notes:\n" + notesContent)
                .call()
                .entity(GraphResponse.class);

        if (graph == null) {
            throw new RuntimeException("Failed to generate local graph");
        }
        tracer.log("Successfully extracted knowledge graph", java.util.Map.of(
                "nodesCount", String.valueOf(graph.nodes().size()),
                "edgesCount", String.valueOf(graph.edges().size())
        ));

        for (var node : graph.nodes()) {
            tracer.log("Extracted Node: " + node.label() + " [" + node.category() + "]");
        }
        for (var edge : graph.edges()) {
            tracer.log("Extracted Edge: " + edge.source() + " -" + edge.predicate() + "-> " + edge.target());
        }

        List<Knode> kNodes = graph.nodes().stream()
                .map(n -> new Knode(n.id(), n.label(), n.category(), n.description()))
                .toList();
        List<Kedge> kEdges = graph.edges().stream()
                .map(e -> new Kedge(e.source(), e.target(), e.predicate(), e.context()))
                .toList();

        return new Kgraph(kNodes, kEdges);
    }

    @Traced(value = "ai.ingest-note", type = KnodeledgeImportanceLevel.METHOD)
    @Override
    public void ingestNote(String note) {
        if (StringUtil.isNullOrEmpty(note)) {
            tracer.log("Note is null or empty!");
        }
    }
}


