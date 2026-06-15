package dev.kuku.knodeledge.services.hierarchy.internal;

import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.BoundaryHierarchy;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyMove;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyNode;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyNodeDraft;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyPatch;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchySummaryUpdate;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.NodeKind;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.SemanticNodeUpdate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HierarchyProcessorTests {
    private final HierarchyProcessor processor = new HierarchyProcessor();

    @Test
    void validatesDetailedKukuHierarchyAndReturnsOneLevel() {
        var hierarchy = kukuHierarchy();

        var validated = processor.validate(hierarchy);
        var interests = processor.level(validated, "interests");

        assertEquals("Interests", interests.current().name());
        assertEquals(List.of("Kuku", "Interests"),
            interests.breadcrumbs().stream().map(node -> node.name()).toList());
        assertEquals(
            Set.of("Music", "Anime", "Gaming"),
            interests.children().stream()
                .map(node -> node.name())
                .collect(java.util.stream.Collectors.toSet())
        );
        assertFalse(interests.leaf());
        assertTrue(interests.children().stream()
            .noneMatch(node -> node.name().equals("Guitar")));

        var alcohol = processor.level(validated, "drinking_alcohol");
        assertTrue(alcohol.leaf());
        assertEquals("Kuku gets tipsy when drinking alcohol.", alcohol.current().statement());
    }

    @Test
    void synchronizesSemanticCopiesAndPreservesUnrelatedBranches() {
        var nodes = new ArrayList<>(kukuHierarchy().nodes());
        nodes.add(fact(
            "guitar_profile_copy",
            "fact:kuku:plays_guitar",
            "profile",
            "HAS_SKILL",
            "Plays guitar",
            "Kuku plays guitar.",
            "Kuku plays guitar."
        ));
        var hierarchy = processor.validate(new BoundaryHierarchy(
            "boundary",
            "root",
            nodes,
            1
        ));

        var patch = new HierarchyPatch(
            List.of(),
            List.of(new SemanticNodeUpdate(
                "fact:kuku:plays_guitar",
                NodeKind.FACT,
                "Plays electric guitar",
                "Kuku plays electric guitar.",
                "Kuku plays electric guitar."
            )),
            List.of(),
            List.of(),
            List.of(
                summary("music", "Kuku's music interests include electric guitar."),
                summary("interests", "Kuku's interests include music, anime, and gaming."),
                summary("profile", "Kuku's profile includes programming and guitar skill."),
                summary("root", "Knowledge about Kuku.")
            )
        );

        var updated = processor.applyPatch(
            hierarchy,
            patch,
            Set.of("root", "interests", "music", "guitar", "profile", "guitar_profile_copy")
        );

        var copies = updated.nodes().stream()
            .filter(node -> node.semanticKey().equals("fact:kuku:plays_guitar"))
            .toList();
        assertEquals(2, copies.size());
        assertTrue(copies.stream()
            .allMatch(node -> node.statement().equals("Kuku plays electric guitar.")));
        assertEquals(
            "Kuku watches Attack on Titan.",
            processor.nodesById(updated).get("attack_on_titan").statement()
        );
    }

    @Test
    void addsNestedConditionalKnowledgeAndRefreshesAncestors() {
        var hierarchy = kukuHierarchy();
        var patch = new HierarchyPatch(
            List.of(
                draft(
                    "preference",
                    "fact:kuku:prefers_manageable_npc_village_games",
                    "gaming",
                    NodeKind.FACT,
                    "HAS_PREFERENCE",
                    "Conditional game preference",
                    "Kuku likes games with a manageable NPC village.",
                    "Preference for games with manageable NPC villages."
                ),
                draft(
                    "manageable",
                    "condition:manageable_by_kuku",
                    "preference",
                    NodeKind.CONDITION,
                    "ALL_OF",
                    "Manageable by Kuku",
                    "The village can be managed by Kuku.",
                    "Village management is controlled by Kuku."
                ),
                draft(
                    "npc_village",
                    "condition:has_npc_village",
                    "preference",
                    NodeKind.CONDITION,
                    "ALL_OF",
                    "Has NPC village",
                    "The game contains an NPC village.",
                    "Game contains an NPC village."
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(
                summary("gaming", "Kuku's games and game preferences."),
                summary("interests", "Kuku's interests include music, anime, and gaming."),
                summary("root", "Knowledge about Kuku.")
            )
        );

        var updated = processor.applyPatch(
            hierarchy,
            patch,
            Set.of("root", "interests", "gaming")
        );

        var preference = updated.nodes().stream()
            .filter(node -> node.semanticKey()
                .equals("fact:kuku:prefers_manageable_npc_village_games"))
            .findFirst()
            .orElseThrow();
        assertEquals(2, processor.children(updated, preference.id()).size());
        assertEquals(2, updated.version());
    }

    @Test
    void rejectsRootMutationUnrelatedChangesCyclesAndRunawayDepth() {
        var hierarchy = kukuHierarchy();
        var rootMove = new HierarchyPatch(
            List.of(),
            List.of(),
            List.of(new HierarchyMove("root", "profile", "CONTAINS")),
            List.of(),
            List.of()
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> processor.applyPatch(hierarchy, rootMove, Set.of("root", "profile"))
        );

        var unrelatedDelete = new HierarchyPatch(
            List.of(),
            List.of(),
            List.of(),
            List.of("fact:kuku:watches_attack_on_titan"),
            List.of()
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> processor.applyPatch(
                hierarchy,
                unrelatedDelete,
                Set.of("root", "profile")
            )
        );

        var cycleNodes = List.of(
            root(),
            topic("a", "b", "A", "A."),
            topic("b", "a", "B", "B.")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> processor.validate(new BoundaryHierarchy("boundary", "root", cycleNodes, 1))
        );

        List<HierarchyNode> deep = new ArrayList<>();
        deep.add(root());
        String parent = "root";
        for (int index = 0; index <= HierarchyProcessor.MAX_DEPTH; index++) {
            String id = "depth_" + index;
            deep.add(topic(id, parent, "Depth " + index, "Depth."));
            parent = id;
        }
        assertThrows(
            IllegalArgumentException.class,
            () -> processor.validate(new BoundaryHierarchy("boundary", "root", deep, 1))
        );
    }

    @Test
    void detectsOverloadedLevelsWithoutRejectingThem() {
        List<HierarchyNode> nodes = new ArrayList<>();
        nodes.add(root());
        for (int index = 0; index < 13; index++) {
            nodes.add(fact(
                "fact_" + index,
                "fact:" + index,
                "root",
                "CONTAINS",
                "Fact " + index,
                "Statement " + index + ".",
                "Summary " + index + "."
            ));
        }
        var hierarchy = processor.validate(new BoundaryHierarchy(
            "boundary",
            "root",
            nodes,
            1
        ));

        assertEquals(List.of("root"),
            processor.overloadedParents(hierarchy).stream().map(HierarchyNode::id).toList());
    }

    @Test
    void rebalancesByMovingExistingNodeUnderNewTemporaryTopic() {
        var patch = new HierarchyPatch(
            List.of(draft(
                "shooters",
                "topic:kuku:gaming:shooters",
                "gaming",
                NodeKind.TOPIC,
                "CONTAINS",
                "Shooters",
                null,
                "Shooter games Kuku plays."
            )),
            List.of(),
            List.of(new HierarchyMove("r6s", "shooters", "CONTAINS")),
            List.of(),
            List.of(
                summary("gaming", "Kuku's gaming interests grouped by type."),
                summary("interests", "Kuku's interests include music, anime, and gaming."),
                summary("root", "Knowledge about Kuku.")
            )
        );

        var updated = processor.applyPatch(
            kukuHierarchy(),
            patch,
            Set.of("root", "interests", "gaming", "r6s")
        );
        var shooters = updated.nodes().stream()
            .filter(node -> node.semanticKey().equals("topic:kuku:gaming:shooters"))
            .findFirst()
            .orElseThrow();

        assertEquals(shooters.id(), processor.nodesById(updated).get("r6s").parentId());
    }

    private BoundaryHierarchy kukuHierarchy() {
        return new BoundaryHierarchy(
            "boundary",
            "root",
            List.of(
                root(),
                topic("profile", "root", "Profile", "Kuku's identity and work."),
                entity("person", "profile", "IS_A", "Person", "Kuku is a person."),
                fact(
                    "programmer",
                    "fact:kuku:is_programmer",
                    "profile",
                    "HAS_ROLE",
                    "Programmer",
                    "Kuku is a programmer.",
                    "Kuku works as a programmer."
                ),
                topic("interests", "root", "Interests", "Kuku's interests."),
                topic("music", "interests", "Music", "Kuku's music interests."),
                fact(
                    "guitar",
                    "fact:kuku:plays_guitar",
                    "music",
                    "PLAYS",
                    "Plays guitar",
                    "Kuku plays guitar.",
                    "Kuku plays guitar."
                ),
                topic("anime", "interests", "Anime", "Anime watched by Kuku."),
                fact(
                    "attack_on_titan",
                    "fact:kuku:watches_attack_on_titan",
                    "anime",
                    "WATCHES",
                    "Attack on Titan",
                    "Kuku watches Attack on Titan.",
                    "Kuku watches Attack on Titan."
                ),
                topic("gaming", "interests", "Gaming", "Kuku's games and game development."),
                fact(
                    "r6s",
                    "fact:kuku:plays_r6s",
                    "gaming",
                    "PLAYS",
                    "Rainbow Six Siege",
                    "Kuku plays Rainbow Six Siege.",
                    "Kuku plays Rainbow Six Siege."
                ),
                topic(
                    "behaviour",
                    "root",
                    "Behaviour & State",
                    "Kuku's behaviour and physical states."
                ),
                fact(
                    "tipsy",
                    "fact:kuku:gets_tipsy_fast",
                    "behaviour",
                    "HAS_STATE",
                    "Gets tipsy quickly",
                    "Kuku gets tipsy quickly.",
                    "Kuku becomes tipsy quickly when drinking."
                ),
                condition(
                    "drinking_alcohol",
                    "condition:kuku:drinking_alcohol",
                    "tipsy",
                    "WHEN",
                    "When drinking alcohol",
                    "Kuku gets tipsy when drinking alcohol.",
                    "This state applies when Kuku drinks alcohol."
                )
            ),
            1
        );
    }

    private HierarchyNode root() {
        return new HierarchyNode(
            "root",
            "root:boundary",
            null,
            NodeKind.ROOT,
            null,
            "Kuku",
            null,
            "Knowledge about Kuku."
        );
    }

    private HierarchyNode topic(String id, String parentId, String name, String summary) {
        return new HierarchyNode(
            id,
            "topic:" + id,
            parentId,
            NodeKind.TOPIC,
            "CONTAINS",
            name,
            null,
            summary
        );
    }

    private HierarchyNode entity(
        String id,
        String parentId,
        String relation,
        String name,
        String summary
    ) {
        return new HierarchyNode(
            id,
            "entity:" + id,
            parentId,
            NodeKind.ENTITY,
            relation,
            name,
            null,
            summary
        );
    }

    private HierarchyNode fact(
        String id,
        String semanticKey,
        String parentId,
        String relation,
        String name,
        String statement,
        String summary
    ) {
        return new HierarchyNode(
            id,
            semanticKey,
            parentId,
            NodeKind.FACT,
            relation,
            name,
            statement,
            summary
        );
    }

    private HierarchyNode condition(
        String id,
        String semanticKey,
        String parentId,
        String relation,
        String name,
        String statement,
        String summary
    ) {
        return new HierarchyNode(
            id,
            semanticKey,
            parentId,
            NodeKind.CONDITION,
            relation,
            name,
            statement,
            summary
        );
    }

    private HierarchyNodeDraft draft(
        String tempId,
        String semanticKey,
        String parentRef,
        NodeKind kind,
        String relation,
        String name,
        String statement,
        String summary
    ) {
        return new HierarchyNodeDraft(
            tempId,
            semanticKey,
            parentRef,
            kind,
            relation,
            name,
            statement,
            summary
        );
    }

    private HierarchySummaryUpdate summary(String nodeId, String summary) {
        return new HierarchySummaryUpdate(nodeId, summary);
    }
}
