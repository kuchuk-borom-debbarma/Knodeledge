package dev.kuku.knodeledge.services.ai.internal;

import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.EdgeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.GraphResponse;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.NodeDto;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.EdgeRef;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.GraphPatch;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.OntologyResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphPatchProcessorTests {

    private final GraphPatchProcessor processor = new GraphPatchProcessor();

    @Test
    void appliesValidConditionalStatementGraph() {
        var patch = new GraphPatch(
            List.of(
                node("amelia", List.of()),
                node("coffee", List.of()),
                node("sugar", List.of()),
                node("statement", List.of()),
                node("condition_group", List.of()),
                node("condition", List.of()),
                node("statement_1", List.of("statement")),
                node("group_1", List.of("condition_group")),
                node("condition_1", List.of("condition"))
            ),
            List.of(
                edge("statement_1", "statement", "GRAPH_ROLE"),
                edge("group_1", "condition_group", "GRAPH_ROLE"),
                edge("condition_1", "condition", "GRAPH_ROLE"),
                edge("statement_1", "amelia", "STATEMENT_SUBJECT"),
                edge("statement_1", "coffee", "DISLIKES"),
                edge("statement_1", "group_1", "WHEN"),
                edge("group_1", "condition_1", "ALL_OF"),
                edge("condition_1", "coffee", "CONDITION_SUBJECT"),
                edge("condition_1", "sugar", "CONTAINS")
            ),
            List.of(),
            List.of()
        );

        var result = processor.apply(new GraphResponse(List.of(), List.of()), patch);

        assertEquals(9, result.nodes().size());
        assertEquals(9, result.edges().size());
    }

    @Test
    void rejectsNewPlaceholderPredicate() {
        var patch = new GraphPatch(
            List.of(node("amelia", List.of()), node("coffee", List.of())),
            List.of(edge("amelia", "coffee", "SEMANTIC_PREDICATE")),
            List.of(),
            List.of()
        );

        var error = assertThrows(
            IllegalArgumentException.class,
            () -> processor.apply(new GraphResponse(List.of(), List.of()), patch)
        );

        assertTrue(error.getMessage().contains("Forbidden placeholder"));
    }

    @Test
    void rejectsDanglingEdge() {
        var patch = new GraphPatch(
            List.of(node("amelia", List.of())),
            List.of(edge("amelia", "missing", "LIKES")),
            List.of(),
            List.of()
        );

        var error = assertThrows(
            IllegalArgumentException.class,
            () -> processor.apply(new GraphResponse(List.of(), List.of()), patch)
        );

        assertTrue(error.getMessage().contains("missing node"));
    }

    @Test
    void restoresNodeDroppedByPatchValidator() {
        var existing = new GraphResponse(
            List.of(node("k", List.of())),
            List.of()
        );
        var ontology = new OntologyResponse(
            List.of(),
            List.of(
                node("yogurt", List.of("food")),
                node("food", List.of()),
                node("consumable", List.of())
            ),
            List.of(
                edge("yogurt", "food", "INSTANCE_OF"),
                edge("food", "consumable", "SUBCATEGORY_OF")
            )
        );
        var candidate = new GraphPatch(
            ontology.nodes(),
            List.of(
                edge("k", "yogurt", "LIKES"),
                edge("yogurt", "food", "INSTANCE_OF")
            ),
            List.of(),
            List.of()
        );
        var validatorPatch = new GraphPatch(
            List.of(),
            List.of(edge("k", "yogurt", "LIKES")),
            List.of(),
            List.of()
        );

        var completed = processor.completeReferences(
            existing,
            validatorPatch,
            candidate,
            ontology
        );
        var result = processor.apply(existing, completed);

        assertEquals(
            List.of("k", "yogurt", "food", "consumable"),
            result.nodes().stream().map(NodeDto::id).toList()
        );
        assertEquals(3, result.edges().size());
    }

    @Test
    void rejectsCategoryCacheWithoutTaxonomyEdge() {
        var patch = new GraphPatch(
            List.of(
                node("rainbow_six_siege", List.of("video_game")),
                node("video_game", List.of())
            ),
            List.of(edge("rainbow_six_siege", "video_game", "PLAYS")),
            List.of(),
            List.of()
        );

        var error = assertThrows(
            IllegalArgumentException.class,
            () -> processor.apply(new GraphResponse(List.of(), List.of()), patch)
        );

        assertTrue(error.getMessage().contains("Category cache lacks graph edge"));
    }

    @Test
    void rejectsStatementWhoseWhenTargetsEntity() {
        var patch = new GraphPatch(
            List.of(
                node("amelia", List.of()),
                node("coffee", List.of()),
                node("statement", List.of()),
                node("statement_1", List.of("statement"))
            ),
            List.of(
                edge("statement_1", "statement", "GRAPH_ROLE"),
                edge("statement_1", "amelia", "STATEMENT_SUBJECT"),
                edge("statement_1", "coffee", "DISLIKES"),
                edge("statement_1", "coffee", "WHEN")
            ),
            List.of(),
            List.of()
        );

        var error = assertThrows(
            IllegalArgumentException.class,
            () -> processor.apply(new GraphResponse(List.of(), List.of()), patch)
        );

        assertTrue(error.getMessage().contains("WHEN must target a condition group"));
    }

    @Test
    void deletesSupersededEdgeAndOrphanNode() {
        var existing = new GraphResponse(
            List.of(
                node("nina", List.of()),
                node("javascript", List.of())
            ),
            List.of(edge("nina", "javascript", "FAVORITE_LANGUAGE"))
        );
        var patch = new GraphPatch(
            List.of(node("typescript", List.of())),
            List.of(edge("nina", "typescript", "FAVORITE_LANGUAGE")),
            List.of("javascript"),
            List.of(new EdgeRef("nina", "javascript", "FAVORITE_LANGUAGE"))
        );

        var result = processor.apply(existing, patch);

        assertEquals(List.of("nina", "typescript"), result.nodes().stream().map(NodeDto::id).toList());
        assertEquals(1, result.edges().size());
        assertEquals("typescript", result.edges().getFirst().target());
    }

    @Test
    void localPatchPreservesUnrelatedCanonicalGraph() {
        var fullGraph = new GraphResponse(
            List.of(
                node("kuku", List.of()),
                node("r6s", List.of()),
                node("attack_on_titan", List.of())
            ),
            List.of(
                edge("kuku", "r6s", "PLAYS"),
                edge("kuku", "attack_on_titan", "WATCHES")
            )
        );
        var localPatch = new GraphPatch(
            List.of(node("valorant", List.of())),
            List.of(edge("kuku", "valorant", "PLAYS")),
            List.of(),
            List.of()
        );

        var result = processor.apply(fullGraph, localPatch);

        assertTrue(result.nodes().stream().anyMatch(node -> node.id().equals("attack_on_titan")));
        assertTrue(result.edges().stream().anyMatch(
            edge -> edge.target().equals("attack_on_titan")
        ));
    }

    @Test
    void rejectsDeleteOutsideRetrievedGraph() {
        var retrieval = new GraphResponse(
            List.of(node("kuku", List.of()), node("r6s", List.of())),
            List.of(edge("kuku", "r6s", "PLAYS"))
        );
        var patch = new GraphPatch(
            List.of(),
            List.of(),
            List.of("attack_on_titan"),
            List.of()
        );

        var error = assertThrows(
            IllegalArgumentException.class,
            () -> processor.validateDeletesWithinGraph(patch, retrieval)
        );

        assertTrue(error.getMessage().contains("outside retrieved graph"));
    }

    private NodeDto node(String id, List<String> categories) {
        return new NodeDto(id, id, categories, "Description for " + id);
    }

    private EdgeDto edge(String source, String target, String predicate) {
        return new EdgeDto(source, target, predicate, "Source context");
    }

}
