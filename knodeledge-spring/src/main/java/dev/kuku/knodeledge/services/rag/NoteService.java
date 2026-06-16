package dev.kuku.knodeledge.services.rag;

import dev.kuku.knodeledge.repositories.ChunkRepository;
import dev.kuku.knodeledge.repositories.NoteRepository;
import dev.kuku.knodeledge.services.rag.model.Note;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class NoteService {
    private final NoteRepository noteRepository;
    private final ChunkRepository chunkRepository;
    private final NoteIndexingService indexingService;

    public NoteService(
        NoteRepository noteRepository,
        ChunkRepository chunkRepository,
        NoteIndexingService indexingService
    ) {
        this.noteRepository = noteRepository;
        this.chunkRepository = chunkRepository;
        this.indexingService = indexingService;
    }

    @Transactional
    public Note create(UUID userId, String title, String content) {
        String normalizedContent = requireContent(content);
        Note note = noteRepository.create(userId, normalizeTitle(title, normalizedContent), normalizedContent);
        enqueueAfterCommit(note);
        return note;
    }

    public List<Note> list(UUID userId) {
        return noteRepository.findActiveByUserId(userId);
    }

    public Note get(UUID userId, UUID noteId) {
        return noteRepository.findActiveById(userId, noteId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
    }

    @Transactional
    public Note update(UUID userId, UUID noteId, String title, String content) {
        String normalizedContent = requireContent(content);
        Note note = noteRepository.updateContent(
                userId,
                noteId,
                normalizeTitle(title, normalizedContent),
                normalizedContent
            )
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
        chunkRepository.deleteByNote(userId, noteId);
        enqueueAfterCommit(note);
        return note;
    }

    @Transactional
    public void delete(UUID userId, UUID noteId) {
        boolean deleted = noteRepository.softDelete(userId, noteId);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found");
        }
        chunkRepository.deleteByNote(userId, noteId);
    }

    @Transactional
    public Note reindex(UUID userId, UUID noteId) {
        Note note = noteRepository.markPending(userId, noteId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
        chunkRepository.deleteByNote(userId, noteId);
        enqueueAfterCommit(note);
        return note;
    }

    private void enqueueAfterCommit(Note note) {
        Runnable enqueue = () -> indexingService.indexNoteAsync(
            note.userId(),
            note.id(),
            note.indexVersion()
        );
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            enqueue.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                enqueue.run();
            }
        });
    }

    private String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note content must not be blank");
        }
        return content.trim();
    }

    private String normalizeTitle(String title, String content) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        return content.lines()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .findFirst()
            .map(line -> line.length() > 80 ? line.substring(0, 80) : line)
            .orElse("Untitled note");
    }
}
