package dev.kuku.knodeledge.services.rag;

import dev.kuku.knodeledge.services.rag.model.GeneratedAnswer;
import dev.kuku.knodeledge.services.rag.model.RetrievalCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatServiceTests {
    @Test
    void returnsNotEnoughInfoWhenNoCandidatesExist() {
        RetrievalService retrieval = mock(RetrievalService.class);
        ChatService service = new ChatService(
            retrieval,
            (question, candidates) -> candidates,
            (question, context) -> GeneratedAnswer.notEnough(),
            50,
            10,
            0.15
        );
        UUID userId = UUID.randomUUID();
        when(retrieval.retrieve(userId, "unknown")).thenReturn(List.of());

        var result = service.ask(userId, "unknown");

        assertEquals(true, result.notEnoughInfo());
    }

    @Test
    void rejectsAnswersWithCitationsOutsideProvidedContext() {
        RetrievalService retrieval = mock(RetrievalService.class);
        RetrievalCandidate candidate = candidate(0.9);
        ChatService service = new ChatService(
            retrieval,
            (question, candidates) -> candidates.stream()
                .map(item -> item.withRerankScore(0.9))
                .toList(),
            (question, context) -> new GeneratedAnswer(
                "A made up answer.",
                false,
                false,
                List.of(UUID.randomUUID().toString())
            ),
            50,
            10,
            0.15
        );
        UUID userId = UUID.randomUUID();
        when(retrieval.retrieve(userId, "question")).thenReturn(List.of(candidate));

        var result = service.ask(userId, "question");

        assertEquals(true, result.notEnoughInfo());
    }

    @Test
    void mapsValidCitations() {
        RetrievalService retrieval = mock(RetrievalService.class);
        RetrievalCandidate candidate = candidate(0.9);
        ChatService service = new ChatService(
            retrieval,
            (question, candidates) -> candidates.stream()
                .map(item -> item.withRerankScore(0.9))
                .toList(),
            (question, context) -> new GeneratedAnswer(
                "Saved notes say yes.",
                false,
                false,
                List.of(candidate.chunkId().toString())
            ),
            50,
            10,
            0.15
        );
        UUID userId = UUID.randomUUID();
        when(retrieval.retrieve(userId, "question")).thenReturn(List.of(candidate));

        var result = service.ask(userId, "question");

        assertEquals(false, result.notEnoughInfo());
        assertEquals(candidate.chunkId().toString(), result.citations().getFirst().chunkId());
    }

    private RetrievalCandidate candidate(double hybridScore) {
        return new RetrievalCandidate(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Saved note",
            0,
            "Useful context",
            0,
            0,
            hybridScore,
            0
        );
    }
}
