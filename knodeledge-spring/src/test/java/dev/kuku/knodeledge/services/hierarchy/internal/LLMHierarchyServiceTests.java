package dev.kuku.knodeledge.services.hierarchy.internal;

import dev.kuku.knodeledge.repositories.internal.InMemoryHierarchyRepository;
import dev.kuku.knodeledge.repositories.internal.InMemoryLegacyGraphRepository;
import dev.kuku.knodeledge.services.ai.cache.CachedPromptExecutor;
import dev.kuku.knodeledge.services.context_boundary.ContextBoundaryService;
import dev.kuku.knodeledge.services.context_boundary.dto.ContextBoundary;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.BoundaryHierarchy;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyNode;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.NodeKind;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.RouteChoice;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.RouteResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LLMHierarchyServiceTests {

    @Test
    void steadyStateAnswerNeverSerializesCompleteHierarchy() {
        var promptExecutor = mock(CachedPromptExecutor.class);
        var boundaries = mock(ContextBoundaryService.class);
        var repository = new InMemoryHierarchyRepository();
        var processor = new HierarchyProcessor();
        var boundary = new ContextBoundary(
            "boundary",
            "Kuku",
            "Knowledge about Kuku.",
            new Date(),
            new Date(),
            "user"
        );
        when(boundaries.getContextBoundaryById("boundary", "user")).thenReturn(boundary);
        repository.save(largeHierarchy());

        when(promptExecutor.entity(
            eq("route-hierarchy"),
            anyString(),
            anyString(),
            eq(RouteResponse.class)
        )).thenReturn(
            new RouteResponse(
                false,
                List.of(new RouteChoice("interests", 0.99, "Relevant interests branch"))
            ),
            new RouteResponse(true, List.of())
        );
        when(promptExecutor.text(
            eq("answer-hierarchy"),
            anyString(),
            anyString()
        )).thenReturn("Kuku has several interests.");

        var service = new LLMHierarchyService(
            promptExecutor,
            new ObjectMapper(),
            repository,
            new InMemoryLegacyGraphRepository(),
            boundaries,
            new HierarchyFactory(),
            processor,
            new BoundaryLockManager()
        );
        ReflectionTestUtils.setField(
            service,
            "routePrompt",
            new ByteArrayResource("route".getBytes())
        );
        ReflectionTestUtils.setField(
            service,
            "answerPrompt",
            new ByteArrayResource("answer".getBytes())
        );

        assertEquals(
            "Kuku has several interests.",
            service.answer("What interests does Kuku have?", "boundary", "user")
        );

        var routePromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(promptExecutor, times(2)).entity(
            eq("route-hierarchy"),
            anyString(),
            routePromptCaptor.capture(),
            eq(RouteResponse.class)
        );
        for (var routePrompt : routePromptCaptor.getAllValues()) {
            assertFalse(routePrompt.contains("Unrelated private detail 49"));
        }

        var answerPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(promptExecutor).text(
            eq("answer-hierarchy"),
            anyString(),
            answerPromptCaptor.capture()
        );
        assertFalse(answerPromptCaptor.getValue().contains("Unrelated private detail 49"));
    }

    private BoundaryHierarchy largeHierarchy() {
        List<HierarchyNode> nodes = new ArrayList<>();
        nodes.add(new HierarchyNode(
            "root",
            "root:boundary",
            null,
            NodeKind.ROOT,
            null,
            "Kuku",
            null,
            "Knowledge about Kuku."
        ));
        nodes.add(topic("profile", "root", "Profile", "Profile details."));
        nodes.add(topic("interests", "root", "Interests", "Kuku's interests."));
        nodes.add(topic("gaming", "interests", "Gaming", "Kuku's gaming interests."));
        nodes.add(topic("unrelated", "profile", "Private details", "Unrelated details."));
        for (int index = 0; index < 50; index++) {
            nodes.add(new HierarchyNode(
                "unrelated_" + index,
                "fact:unrelated:" + index,
                "unrelated",
                NodeKind.FACT,
                "CONTAINS",
                "Unrelated " + index,
                "Unrelated private detail " + index + ".",
                "Unrelated private detail " + index + "."
            ));
        }
        return new BoundaryHierarchy("boundary", "root", nodes, 1);
    }

    private HierarchyNode topic(String id, String parent, String name, String summary) {
        return new HierarchyNode(
            id,
            "topic:" + id,
            parent,
            NodeKind.TOPIC,
            "CONTAINS",
            name,
            null,
            summary
        );
    }
}
