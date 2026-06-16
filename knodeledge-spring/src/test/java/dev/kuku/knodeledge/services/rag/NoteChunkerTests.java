package dev.kuku.knodeledge.services.rag;

import dev.kuku.knodeledge.services.rag.model.IndexStatus;
import dev.kuku.knodeledge.services.rag.model.Note;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteChunkerTests {
    private final NoteChunker chunker = new NoteChunker();

    @Test
    void includesTitleAndCreatedDateInChunkText() {
        Note note = note("Trip plan", "Flights\n\n- Book hotel\n- Pack charger");

        var chunks = chunker.chunk(note);

        assertEquals(1, chunks.size());
        assertTrue(chunks.getFirst().content().contains("Note title: Trip plan"));
        assertTrue(chunks.getFirst().content().contains("Created date: 2026-06-16"));
        assertTrue(chunks.getFirst().content().contains("- Book hotel"));
    }

    @Test
    void splitsLargeNotesWithOverlap() {
        String content = String.join(
            " ",
            IntStream.range(0, 1400).mapToObj(index -> "token" + index).toList()
        );

        var chunks = chunker.chunk(note("Large note", content));

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.get(1).content().contains("token550"));
        assertTrue(chunks.get(1).content().contains("token649"));
    }

    private Note note(String title, String content) {
        return new Note(
            UUID.randomUUID(),
            UUID.randomUUID(),
            title,
            content,
            IndexStatus.PENDING,
            1,
            null,
            null,
            null,
            Instant.parse("2026-06-16T10:15:30Z"),
            Instant.parse("2026-06-16T10:15:30Z")
        );
    }
}
