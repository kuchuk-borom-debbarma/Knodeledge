package dev.kuku.knodeledge.services.rag;

import dev.kuku.knodeledge.repositories.ChunkRepository;
import dev.kuku.knodeledge.repositories.NoteRepository;
import dev.kuku.knodeledge.services.rag.model.ChunkText;
import dev.kuku.knodeledge.services.rag.model.IndexedChunk;
import dev.kuku.knodeledge.services.rag.model.Note;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class NoteIndexingService {
    private final NoteRepository noteRepository;
    private final ChunkRepository chunkRepository;
    private final NoteChunker chunker;
    private final TextEmbeddingService embeddingService;
    private final int embeddingDimensions;

    public NoteIndexingService(
        NoteRepository noteRepository,
        ChunkRepository chunkRepository,
        NoteChunker chunker,
        TextEmbeddingService embeddingService,
        @Value("${knodeledge.rag.embedding-dimensions:1536}") int embeddingDimensions
    ) {
        this.noteRepository = noteRepository;
        this.chunkRepository = chunkRepository;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.embeddingDimensions = embeddingDimensions;
    }

    @Async
    public CompletableFuture<Void> indexNoteAsync(UUID userId, UUID noteId, int indexVersion) {
        indexNote(userId, noteId, indexVersion);
        return CompletableFuture.completedFuture(null);
    }

    public void indexNote(UUID userId, UUID noteId, int indexVersion) {
        if (!noteRepository.markIndexing(userId, noteId, indexVersion)) {
            return;
        }
        try {
            Note note = noteRepository.findActiveById(userId, noteId)
                .orElseThrow(() -> new IllegalStateException("Note disappeared before indexing"));
            if (note.indexVersion() != indexVersion) {
                return;
            }

            List<ChunkText> chunkTexts = chunker.chunk(note);
            if (chunkTexts.isEmpty()) {
                throw new IllegalStateException("Chunking produced no chunks");
            }
            List<float[]> embeddings = embeddingService.embedAll(
                chunkTexts.stream().map(ChunkText::content).toList()
            );
            if (embeddings.size() != chunkTexts.size()) {
                throw new IllegalStateException("Embedding count did not match chunk count");
            }

            List<IndexedChunk> chunks = new ArrayList<>();
            for (int index = 0; index < chunkTexts.size(); index++) {
                float[] embedding = embeddings.get(index);
                validateEmbedding(embedding);
                chunks.add(new IndexedChunk(
                    chunkTexts.get(index).index(),
                    chunkTexts.get(index).content(),
                    embedding,
                    metadataJson(note, chunkTexts.get(index))
                ));
            }
            chunkRepository.insertChunks(userId, noteId, chunks);
            noteRepository.markReady(userId, noteId, indexVersion);
        } catch (RuntimeException error) {
            noteRepository.markFailed(userId, noteId, indexVersion, summarize(error));
            throw error;
        }
    }

    private void validateEmbedding(float[] embedding) {
        if (embedding == null || embedding.length != embeddingDimensions) {
            int length = embedding == null ? 0 : embedding.length;
            throw new IllegalStateException(
                "Embedding dimension mismatch: expected " + embeddingDimensions + ", got " + length
            );
        }
    }

    private String metadataJson(Note note, ChunkText chunk) {
        return """
            {"noteTitle":"%s","chunkIndex":%d,"indexVersion":%d}
            """.formatted(
                escape(note.title()),
                chunk.index(),
                note.indexVersion()
            ).trim();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String summarize(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
