package dev.kuku.knodeledge.repositories;

import dev.kuku.knodeledge.services.rag.model.IndexedChunk;
import dev.kuku.knodeledge.services.rag.model.RetrievalCandidate;

import java.util.List;
import java.util.UUID;

public interface ChunkRepository {
    void deleteByNote(UUID userId, UUID noteId);

    void insertChunks(UUID userId, UUID noteId, List<IndexedChunk> chunks);

    List<RetrievalCandidate> denseSearch(UUID userId, float[] embedding, int limit);

    List<RetrievalCandidate> sparseSearch(UUID userId, String question, int limit);
}
