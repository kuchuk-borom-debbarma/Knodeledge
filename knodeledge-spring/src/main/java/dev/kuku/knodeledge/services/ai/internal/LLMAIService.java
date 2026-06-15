package dev.kuku.knodeledge.services.ai.internal;

import dev.kuku.knodeledge.infra.topo_tracer.KnodeledgeImportanceLevel;
import dev.kuku.knodeledge.infra.topo_tracer.Traced;
import dev.kuku.knodeledge.services.ai.AIService;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.GraphPatch;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.OntologyResponse;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.PatchValidationResponse;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.SemanticExtraction;
import dev.kuku.knodeledge.services.context_boundary.ContextBoundaryService;
import dev.kuku.knodeledge.services.graph.GraphService;
import dev.kuku.topotracer.sdk.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Five-call LLM workflow with deterministic graph patch validation and application.
 */
@Service
@RequiredArgsConstructor
public class LLMAIService implements AIService {

    private final Tracer tracer;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ContextBoundaryService contextBoundaryService;
    private final GraphService graphService;
    private final GraphPatchProcessor graphPatchProcessor;

    @Value("classpath:/prompts/LLM-flow/01_normalize_note.st")
    private Resource normalizePromptResource;

    @Value("classpath:/prompts/LLM-flow/02_extract_assertions.st")
    private Resource extractAssertionsPromptResource;

    @Value("classpath:/prompts/LLM-flow/03_build_ontology.st")
    private Resource buildOntologyPromptResource;

    @Value("classpath:/prompts/LLM-flow/04_construct_graph_patch.st")
    private Resource constructPatchPromptResource;

    @Value("classpath:/prompts/LLM-flow/05_validate_graph_patch.st")
    private Resource validatePatchPromptResource;

    @Traced(
        value = "ai.ingest-note",
        type = KnodeledgeImportanceLevel.SERVICE,
        includeArguments = true,
        maxArgumentLength = 160)
    @Override
    public void ingestNote(String note, String contextBoundaryId, String actorId) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("Note must not be blank");
        }

        tracer.log("IngestNote " + note + " within " + contextBoundaryId + " for " + actorId);

        var parallel = tracer.parallel();
        var contextOp = CompletableFuture.supplyAsync(parallel.wrapSupplier(
            () -> contextBoundaryService.getContextBoundaryById(contextBoundaryId, actorId)));
        var graphOp = CompletableFuture.supplyAsync(parallel.wrapSupplier(
            () -> graphService.getCompleteGraphByBoundaryId(contextBoundaryId, actorId)));
        var context = contextOp.join();

        String normalizedNote = chatClient.prompt()
            .system(getPrompt(normalizePromptResource))
            .user(String.format("""
                Subject Name: %s
                Subject Context: %s

                Raw Note:
                %s
                """, context.name(), context.context(), note))
            .call()
            .content();
        if (normalizedNote == null || normalizedNote.isBlank()) {
            throw new IllegalStateException("Stage 1 returned an empty normalized note");
        }
        tracer.log("LLM flow stage 1 normalized note");

        SemanticExtraction extraction = callEntity(
            extractAssertionsPromptResource,
            String.format("""
                Context Boundary:
                Name: %s
                Description: %s

                Normalized Facts:
                %s
                """, context.name(), context.context(), normalizedNote),
            SemanticExtraction.class,
            "extract assertions"
        );
        tracer.log("LLM flow stage 2 extracted assertions", Map.of(
            "entities", String.valueOf(size(extraction.entities())),
            "assertions", String.valueOf(size(extraction.assertions()))
        ));

        var existingGraph = graphOp.join();
        parallel.join();

        OntologyResponse ontology = callEntity(
            buildOntologyPromptResource,
            String.format("""
                Context Boundary:
                Name: %s
                Description: %s

                Semantic Extraction:
                %s

                Existing Graph:
                %s
                """,
                context.name(),
                context.context(),
                toJson(extraction),
                toJson(existingGraph)),
            OntologyResponse.class,
            "build ontology"
        );
        tracer.log("LLM flow stage 3 built ontology", Map.of(
            "canonicalEntities", String.valueOf(size(ontology.canonicalEntities())),
            "nodes", String.valueOf(size(ontology.nodes())),
            "edges", String.valueOf(size(ontology.edges()))
        ));

        GraphPatch candidatePatch = callEntity(
            constructPatchPromptResource,
            String.format("""
                Context Boundary:
                Name: %s
                Description: %s

                Existing Graph:
                %s

                Semantic Extraction:
                %s

                Resolved Ontology:
                %s
                """,
                context.name(),
                context.context(),
                toJson(existingGraph),
                toJson(extraction),
                toJson(ontology)),
            GraphPatch.class,
            "construct graph patch"
        );
        tracer.log("LLM flow stage 4 constructed graph patch", patchCounts(candidatePatch));

        PatchValidationResponse validation = callEntity(
            validatePatchPromptResource,
            String.format("""
                Existing Graph:
                %s

                Semantic Extraction:
                %s

                Resolved Ontology:
                %s

                Candidate Patch:
                %s
                """,
                toJson(existingGraph),
                toJson(extraction),
                toJson(ontology),
                toJson(candidatePatch)),
            PatchValidationResponse.class,
            "validate graph patch"
        );
        if (!validation.valid() || validation.correctedPatch() == null) {
            throw new IllegalStateException(
                "Stage 5 rejected graph patch: " + String.join("; ", list(validation.issues()))
            );
        }
        tracer.log("LLM flow stage 5 validated graph patch", Map.of(
            "issuesRepaired", String.valueOf(size(validation.issues()))
        ));

        var finalGraph = graphPatchProcessor.apply(existingGraph, validation.correctedPatch());
        graphService.saveGraph(contextBoundaryId, finalGraph.nodes(), finalGraph.edges());
        tracer.log("Saved validated graph", Map.of(
            "boundaryId", contextBoundaryId,
            "nodes", String.valueOf(finalGraph.nodes().size()),
            "edges", String.valueOf(finalGraph.edges().size())
        ));
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
            throw new IllegalStateException("LLM flow stage failed: " + stage);
        }
        return response;
    }

    private Map<String, String> patchCounts(GraphPatch patch) {
        return Map.of(
            "upsertNodes", String.valueOf(size(patch.upsertNodes())),
            "upsertEdges", String.valueOf(size(patch.upsertEdges())),
            "deleteNodes", String.valueOf(size(patch.deleteNodes())),
            "deleteEdges", String.valueOf(size(patch.deleteEdges()))
        );
    }

    private String getPrompt(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt resource from classpath", e);
        }
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }
}
