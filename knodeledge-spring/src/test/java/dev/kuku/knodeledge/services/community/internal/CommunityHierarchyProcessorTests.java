package dev.kuku.knodeledge.services.community.internal;

import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.EdgeDto;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.GraphResponse;
import dev.kuku.knodeledge.services.ai.internal.models.GraphDto.NodeDto;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.EdgeRef;
import dev.kuku.knodeledge.services.ai.internal.models.LLMFlowDto.GraphPatch;
import dev.kuku.knodeledge.services.community.model.CommunityModels.Community;
import dev.kuku.knodeledge.services.community.model.CommunityModels.CommunityAssignment;
import dev.kuku.knodeledge.services.community.model.CommunityModels.CommunityHierarchy;
import dev.kuku.knodeledge.services.community.model.CommunityModels.CommunitySummaryUpdate;
import dev.kuku.knodeledge.services.community.model.CommunityModels.CommunityUpdateResponse;
import dev.kuku.knodeledge.services.community.model.CommunityModels.RouteChoice;
import dev.kuku.knodeledge.services.community.model.CommunityModels.RouteResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityHierarchyProcessorTests {
    private final CommunityHierarchyProcessor processor = new CommunityHierarchyProcessor();

    @Test
    void retrievesOneHopTaxonomyAndCompleteConditionalStructure() {
        var graph = conditionalGraph();
        var hierarchy = new CommunityHierarchy(List.of(
            community(
                "root", null,
                List.of(
                    "person", "statement", "condition_group", "condition", "npc",
                    "statement_1", "group_1", "condition_1"
                ),
                List.of(
                    ref("kuku", "person", "INSTANCE_OF"),
                    ref("statement_1", "statement", "GRAPH_ROLE"),
                    ref("group_1", "condition_group", "GRAPH_ROLE"),
                    ref("condition_1", "condition", "GRAPH_ROLE"),
                    ref("statement_1", "kuku", "STATEMENT_SUBJECT"),
                    ref("statement_1", "group_1", "WHEN"),
                    ref("group_1", "condition_1", "ALL_OF"),
                    ref("condition_1", "games", "CONDITION_SUBJECT"),
                    ref("condition_1", "npc", "HAS_FEATURE")
                )
            ),
            community(
                "gaming", "root",
                List.of("kuku", "games"),
                List.of(ref("statement_1", "games", "LIKES"))
            )
        ));
        processor.validate(hierarchy, graph);

        var result = processor.retrieve(hierarchy, graph, List.of("gaming"));

        assertTrue(result.nodes().stream().map(NodeDto::id).toList().containsAll(
            List.of(
                "kuku", "person", "games", "statement_1", "group_1", "condition_1",
                "statement", "condition_group", "condition", "npc"
            )
        ));
        assertEquals(graph.edges().size(), result.edges().size());
    }

    @Test
    void rejectsCycleAndExcessiveDepth() {
        var cycle = new CommunityHierarchy(List.of(
            community("a", "b", List.of(), List.of()),
            community("b", "a", List.of(), List.of())
        ));
        assertThrows(
            IllegalArgumentException.class,
            () -> processor.validate(cycle, new GraphResponse(List.of(), List.of()))
        );

        List<Community> deep = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            deep.add(community("c" + i, i == 0 ? null : "c" + (i - 1), List.of(), List.of()));
        }
        var error = assertThrows(
            IllegalArgumentException.class,
            () -> processor.validate(
                new CommunityHierarchy(deep),
                new GraphResponse(List.of(), List.of())
            )
        );
        assertTrue(error.getMessage().contains("max depth"));
    }

    @Test
    void rejectsUncoveredGraphElements() {
        var graph = new GraphResponse(
            List.of(node("kuku"), node("guitar")),
            List.of(edge("kuku", "guitar", "LIKES"))
        );
        var hierarchy = new CommunityHierarchy(List.of(
            community("root", null, List.of("kuku"), List.of())
        ));

        var error = assertThrows(
            IllegalArgumentException.class,
            () -> processor.validate(hierarchy, graph)
        );

        assertTrue(error.getMessage().contains("Every graph node"));
    }

    @Test
    void addsNewChildAndRefreshesAllAncestors() {
        var oldGraph = new GraphResponse(
            List.of(node("kuku"), node("r6s")),
            List.of(edge("kuku", "r6s", "PLAYS"))
        );
        var hierarchy = new CommunityHierarchy(List.of(
            community("root", null, List.of(), List.of()),
            community(
                "gaming", "root",
                List.of("kuku", "r6s"),
                List.of(ref("kuku", "r6s", "PLAYS"))
            )
        ));
        processor.validate(hierarchy, oldGraph);

        var patch = new GraphPatch(
            List.of(node("valorant")),
            List.of(edge("kuku", "valorant", "PLAYS")),
            List.of(),
            List.of()
        );
        var finalGraph = new GraphResponse(
            List.of(node("kuku"), node("r6s"), node("valorant")),
            List.of(
                edge("kuku", "r6s", "PLAYS"),
                edge("kuku", "valorant", "PLAYS")
            )
        );
        var update = new CommunityUpdateResponse(
            List.of(community("competitive_fps", "gaming", List.of(), List.of())),
            List.of(new CommunityAssignment(
                "competitive_fps",
                List.of("valorant"),
                List.of(ref("kuku", "valorant", "PLAYS"))
            )),
            List.of(
                new CommunitySummaryUpdate("competitive_fps", "Valorant facts."),
                new CommunitySummaryUpdate("gaming", "Kuku plays R6S and Valorant."),
                new CommunitySummaryUpdate("root", "Kuku knowledge including gaming.")
            )
        );

        var result = processor.applyUpdate(
            hierarchy,
            update,
            patch,
            finalGraph,
            List.of("gaming")
        );

        assertEquals(3, result.communities().size());
        assertEquals(
            "Kuku plays R6S and Valorant.",
            processor.communitiesById(result).get("gaming").summary()
        );
        assertEquals(
            List.of("valorant"),
            processor.communitiesById(result).get("competitive_fps").memberNodeIds()
        );
    }

    @Test
    void requiresAncestorSummaryRefresh() {
        var graph = new GraphResponse(List.of(node("kuku")), List.of());
        var hierarchy = new CommunityHierarchy(List.of(
            community("root", null, List.of(), List.of()),
            community("profile", "root", List.of("kuku"), List.of())
        ));
        var patch = new GraphPatch(List.of(node("kuku")), List.of(), List.of(), List.of());
        var update = new CommunityUpdateResponse(
            List.of(),
            List.of(new CommunityAssignment("profile", List.of("kuku"), List.of())),
            List.of(new CommunitySummaryUpdate("profile", "Updated profile."))
        );

        var error = assertThrows(
            IllegalArgumentException.class,
            () -> processor.applyUpdate(
                hierarchy,
                update,
                patch,
                graph,
                List.of("profile")
            )
        );

        assertTrue(error.getMessage().contains("all ancestors"));
    }

    @Test
    void routeSelectionKeepsHighestTwoValidCommunities() {
        var candidates = List.of(
            community("gaming", null, List.of(), List.of()),
            community("anime", null, List.of(), List.of()),
            community("music", null, List.of(), List.of())
        );
        var response = new RouteResponse(List.of(
            new RouteChoice("missing", 1.0, "Invalid hallucinated id"),
            new RouteChoice("anime", 0.7, "Anime"),
            new RouteChoice("gaming", 0.9, "Gaming"),
            new RouteChoice("music", 0.5, "Music")
        ));

        var selected = processor.selectRouteChoices(candidates, response, 2);

        assertEquals(List.of("gaming", "anime"), selected.stream().map(Community::id).toList());
    }

    private GraphResponse conditionalGraph() {
        var nodes = List.of(
            node("kuku"), node("person"), node("games"), node("npc"),
            node("statement"), node("condition_group"), node("condition"),
            node("statement_1"), node("group_1"), node("condition_1")
        );
        var edges = List.of(
            edge("kuku", "person", "INSTANCE_OF"),
            edge("statement_1", "statement", "GRAPH_ROLE"),
            edge("group_1", "condition_group", "GRAPH_ROLE"),
            edge("condition_1", "condition", "GRAPH_ROLE"),
            edge("statement_1", "kuku", "STATEMENT_SUBJECT"),
            edge("statement_1", "games", "LIKES"),
            edge("statement_1", "group_1", "WHEN"),
            edge("group_1", "condition_1", "ALL_OF"),
            edge("condition_1", "games", "CONDITION_SUBJECT"),
            edge("condition_1", "npc", "HAS_FEATURE")
        );
        return new GraphResponse(nodes, edges);
    }

    private Community community(
        String id,
        String parentId,
        List<String> nodeIds,
        List<EdgeRef> edges
    ) {
        return new Community(id, id, "Summary for " + id, parentId, nodeIds, edges);
    }

    private NodeDto node(String id) {
        List<String> categories = switch (id) {
            case "kuku" -> List.of("person");
            case "statement_1" -> List.of("statement");
            case "group_1" -> List.of("condition_group");
            case "condition_1" -> List.of("condition");
            default -> List.of();
        };
        return new NodeDto(id, id, categories, "Description for " + id);
    }

    private EdgeDto edge(String source, String target, String predicate) {
        return new EdgeDto(source, target, predicate, "Context");
    }

    private EdgeRef ref(String source, String target, String predicate) {
        return new EdgeRef(source, target, predicate);
    }
}
