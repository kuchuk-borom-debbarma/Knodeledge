package dev.kuku.knodeledge.controllers.models;

import dev.kuku.knodeledge.services.rag.model.Note;

import java.time.Instant;

public record NoteResponse(
    String id,
    String title,
    String content,
    String indexStatus,
    int indexVersion,
    String indexError,
    Instant indexedAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static NoteResponse from(Note note) {
        return new NoteResponse(
            note.id().toString(),
            note.title(),
            note.content(),
            note.indexStatus().value(),
            note.indexVersion(),
            note.indexError(),
            note.indexedAt(),
            note.createdAt(),
            note.updatedAt()
        );
    }
}
