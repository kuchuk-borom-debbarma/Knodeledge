package dev.kuku.knodeledge.services.rag;

import dev.kuku.knodeledge.repositories.ChunkRepository;
import dev.kuku.knodeledge.repositories.NoteRepository;
import dev.kuku.knodeledge.services.rag.model.IndexStatus;
import dev.kuku.knodeledge.services.rag.model.IndexedChunk;
import dev.kuku.knodeledge.services.rag.model.Note;
import dev.kuku.knodeledge.services.rag.model.RetrievalCandidate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoteIndexingServiceTests {
    @Test
    void indexesChunksAndMarksNoteReady() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        var notes = new FakeNoteRepository(note(userId, noteId, 1));
        var chunks = new FakeChunkRepository();
        var service = new NoteIndexingService(
            notes,
            chunks,
            new NoteChunker(),
            new FixedEmbeddingService(3),
            3
        );

        service.indexNote(userId, noteId, 1);

        assertEquals(IndexStatus.READY, notes.status);
        assertEquals(1, chunks.inserted.size());
    }

    @Test
    void marksFailedWhenEmbeddingDimensionsDoNotMatch() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        var notes = new FakeNoteRepository(note(userId, noteId, 1));
        var service = new NoteIndexingService(
            notes,
            new FakeChunkRepository(),
            new NoteChunker(),
            new FixedEmbeddingService(2),
            3
        );

        assertThrows(IllegalStateException.class, () -> service.indexNote(userId, noteId, 1));
        assertEquals(IndexStatus.FAILED, notes.status);
    }

    private Note note(UUID userId, UUID noteId, int version) {
        return new Note(
            noteId,
            userId,
            "Title",
            "Useful note text",
            IndexStatus.PENDING,
            version,
            null,
            null,
            null,
            Instant.parse("2026-06-16T10:15:30Z"),
            Instant.parse("2026-06-16T10:15:30Z")
        );
    }

    private static class FixedEmbeddingService implements TextEmbeddingService {
        private final int dimensions;

        FixedEmbeddingService(int dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public float[] embed(String text) {
            return vector();
        }

        @Override
        public List<float[]> embedAll(List<String> texts) {
            return texts.stream().map(text -> vector()).toList();
        }

        private float[] vector() {
            float[] vector = new float[dimensions];
            for (int index = 0; index < dimensions; index++) {
                vector[index] = 0.1f;
            }
            return vector;
        }
    }

    private static class FakeNoteRepository implements NoteRepository {
        private final Note note;
        private IndexStatus status = IndexStatus.PENDING;

        FakeNoteRepository(Note note) {
            this.note = note;
        }

        @Override
        public Note create(UUID userId, String title, String content) {
            return note;
        }

        @Override
        public List<Note> findActiveByUserId(UUID userId) {
            return List.of(note);
        }

        @Override
        public Optional<Note> findActiveById(UUID userId, UUID noteId) {
            return Optional.of(note);
        }

        @Override
        public Optional<Note> updateContent(UUID userId, UUID noteId, String title, String content) {
            return Optional.of(note);
        }

        @Override
        public Optional<Note> markPending(UUID userId, UUID noteId) {
            return Optional.of(note);
        }

        @Override
        public boolean markIndexing(UUID userId, UUID noteId, int indexVersion) {
            status = IndexStatus.INDEXING;
            return true;
        }

        @Override
        public void markReady(UUID userId, UUID noteId, int indexVersion) {
            status = IndexStatus.READY;
        }

        @Override
        public void markFailed(UUID userId, UUID noteId, int indexVersion, String error) {
            status = IndexStatus.FAILED;
        }

        @Override
        public boolean softDelete(UUID userId, UUID noteId) {
            return true;
        }
    }

    private static class FakeChunkRepository implements ChunkRepository {
        private List<IndexedChunk> inserted = List.of();

        @Override
        public void deleteByNote(UUID userId, UUID noteId) {
        }

        @Override
        public void insertChunks(UUID userId, UUID noteId, List<IndexedChunk> chunks) {
            inserted = chunks;
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
