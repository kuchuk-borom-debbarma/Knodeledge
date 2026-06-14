package dev.kuku.knodeledge.services.ai.internal;

import dev.kuku.knodeledge.services.ai.AIService;
import dev.kuku.knodeledge.services.ai.dto.GraphDto.GraphResponse;
import dev.kuku.topotracer.spring.Traced;
import dev.kuku.topotracer.sdk.TopoNodeType;
import dev.kuku.topotracer.sdk.Tracer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

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

    /**
     * LLM Focused solution for generating local graph
     */
    @Override
    @Traced(value = "ai.process-notes", type = TopoNodeType.METHOD)
    public void generateLocalGraphFromNotes(ArrayList<String> notes) {
        tracer.log("LLM focused Local Graph Generator");
        if (notes == null || notes.isEmpty()) {
            return;
        }

        // 1. Join all notes into a single context block
        String notesContent = String.join("\n---\n", notes);

        // 2. Define prompt template and extract structured graph
        String systemPrompt = """
            You are a highly precise Knowledge Graph Extraction Engine. Your task is to analyze raw text notes, extract static concepts (entities), and map the relationships (edges) between them.
            
            Follow these strict rules:
            1. ENTITY RESOLUTION & NORMALIZATION: Map synonyms and plurals to the same singular, lowercase ID (e.g. "mangoes" -> "mango").
            2. RELATIONSHIP EXTRACTION: Format predicates in UPPER_SNAKE_CASE (e.g. IS_A, BETTER_WITH).
            3. NOISE FILTERING: Ignore conversational or out-of-context statements.
            4. CONTEXT ANCHORING: For every edge, populate the 'context' field with the exact sentence from the notes.
            """;

        GraphResponse graph = chatClient.prompt()
                .system(systemPrompt)
                .user("Analyze the following notes:\n" + notesContent)
                .call()
                .entity(GraphResponse.class);

        // 3. Log the extracted graph (in a real app, this would be persisted to a DB)
        if (graph != null) {
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
        }
    }
}
