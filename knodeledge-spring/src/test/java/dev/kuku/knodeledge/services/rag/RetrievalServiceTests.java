package dev.kuku.knodeledge.services.rag;

import dev.kuku.knodeledge.repositories.ChunkRepository;
import dev.kuku.knodeledge.services.rag.model.RetrievalCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetrievalServiceTests {
    @Test
    void mergesAndDedupesDenseAndSparseCandidates() {
        RetrievalService service = new RetrievalService(
            new NoopChunkRepository(),
            new FixedEmbeddingService(),
            30,
            30
        );
        UUID shared = UUID.randomUUID();
        var denseOnly = candidate(UUID.randomUUID(), 0.9, 0);
        var sharedDense = candidate(shared, 0.6, 0);
        var sharedSparse = candidate(shared, 0, 4.0);

        var merged = service.merge(List.of(denseOnly, sharedDense), List.of(sharedSparse));

        assertEquals(2, merged.size());
        assertEquals(shared, merged.getFirst().chunkId());
        assertEquals(4.0, merged.getFirst().sparseScore());
    }

    private RetrievalCandidate candidate(UUID chunkId, double dense, double sparse) {
        return new RetrievalCandidate(
            chunkId,
            UUID.randomUUID(),
            "Note",
            0,
            "content",
            dense,
            sparse,
            0,
            0
        );
    }

    private static class FixedEmbeddingService implements TextEmbeddingService {
        @Override
        public float[] embed(String text) {
            return new float[] { 1 };
        }

        @Override
        public List<float[]> embedAll(List<String> texts) {
            return texts.stream().map(text -> new float[] { 1 }).toList();
        }
    }

    private static class NoopChunkRepository implements ChunkRepository {
        @Override
        public void deleteByNote(UUID userId, UUID noteId) {
        }

        @Override
        public void insertChunks(
            UUID userId,
            UUID noteId,
            List<dev.kuku.knodeledge.services.rag.model.IndexedChunk> chunks
        ) {
        }

        @Override
        public List<RetrievalCandidate> denseSearch(UUID userId, float[] embedding, int limit) {
            return List.of();
        }

        @Override
        public List<RetrievalCandidate> sparseSearch(UUID userId, String question, int limit) {
            return List.of();
        }
    }
}
