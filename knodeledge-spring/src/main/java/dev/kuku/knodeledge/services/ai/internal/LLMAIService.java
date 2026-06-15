package dev.kuku.knodeledge.services.ai.internal;

import dev.kuku.knodeledge.infra.topo_tracer.KnodeledgeImportanceLevel;
import dev.kuku.knodeledge.infra.topo_tracer.Traced;
import dev.kuku.knodeledge.services.ai.AIService;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.GraphResponse;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Two-call LLM workflow: normalize the note, then reconcile the complete graph.
 */
@Service
@RequiredArgsConstructor
public class LLMAIService implements AIService {

    private final Tracer tracer;
    private final ChatClient chatClient;
    private final ContextBoundaryService contextBoundaryService;
    private final GraphService graphService;

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

        // Stage 2: Reconcile the note with the existing graph and return the full graph snapshot.
        GraphResponse response = chatClient.prompt()
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
                .entity(GraphResponse.class);

        if (response == null || response.nodes() == null || response.edges() == null) {
            throw new RuntimeException("Failed to reconcile note into a complete graph");
        }

        tracer.log("Successfully reconciled note into graph", Map.of(
                "nodesCount", String.valueOf(response.nodes().size()),
                "edgesCount", String.valueOf(response.edges().size())
        ));

        graphService.saveGraph(contextBoundaryId, response.nodes(), response.edges());
        tracer.log("Saved updated graph to database for boundary: " + contextBoundaryId);
    }
}
