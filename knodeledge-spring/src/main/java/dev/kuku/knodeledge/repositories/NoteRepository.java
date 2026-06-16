package dev.kuku.knodeledge.repositories;

import dev.kuku.knodeledge.services.rag.model.Note;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository {
    Note create(UUID userId, String title, String content);

    List<Note> findActiveByUserId(UUID userId);

    Optional<Note> findActiveById(UUID userId, UUID noteId);

    Optional<Note> updateContent(UUID userId, UUID noteId, String title, String content);

    Optional<Note> markPending(UUID userId, UUID noteId);

    boolean markIndexing(UUID userId, UUID noteId, int indexVersion);

    void markReady(UUID userId, UUID noteId, int indexVersion);

    void markFailed(UUID userId, UUID noteId, int indexVersion, String error);

    boolean softDelete(UUID userId, UUID noteId);
}
