package dev.kuku.knodeledge.repositories.internal;

import dev.kuku.knodeledge.repositories.NoteRepository;
import dev.kuku.knodeledge.services.rag.model.IndexStatus;
import dev.kuku.knodeledge.services.rag.model.Note;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcNoteRepository implements NoteRepository {
    private static final RowMapper<Note> NOTE_ROW_MAPPER = (rs, rowNum) -> new Note(
        rs.getObject("id", UUID.class),
        rs.getObject("user_id", UUID.class),
        rs.getString("title"),
        rs.getString("content"),
        IndexStatus.fromValue(rs.getString("index_status")),
        rs.getInt("index_version"),
        rs.getString("index_error"),
        instant(rs.getTimestamp("indexed_at")),
        instant(rs.getTimestamp("deleted_at")),
        instant(rs.getTimestamp("created_at")),
        instant(rs.getTimestamp("updated_at"))
    );

    private final JdbcClient jdbcClient;

    public JdbcNoteRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Note create(UUID userId, String title, String content) {
        UUID id = UUID.randomUUID();
        return jdbcClient.sql("""
                INSERT INTO notes (id, user_id, title, content, index_status)
                VALUES (:id, :userId, :title, :content, 'pending')
                RETURNING *
                """)
            .param("id", id)
            .param("userId", userId)
            .param("title", title)
            .param("content", content)
            .query(NOTE_ROW_MAPPER)
            .single();
    }

    @Override
    public List<Note> findActiveByUserId(UUID userId) {
        return jdbcClient.sql("""
                SELECT *
                FROM notes
                WHERE user_id = :userId
                  AND deleted_at IS NULL
                ORDER BY updated_at DESC
                """)
            .param("userId", userId)
            .query(NOTE_ROW_MAPPER)
            .list();
    }

    @Override
    public Optional<Note> findActiveById(UUID userId, UUID noteId) {
        return jdbcClient.sql("""
                SELECT *
                FROM notes
                WHERE id = :noteId
                  AND user_id = :userId
                  AND deleted_at IS NULL
                """)
            .param("noteId", noteId)
            .param("userId", userId)
            .query(NOTE_ROW_MAPPER)
            .optional();
    }

    @Override
    public Optional<Note> updateContent(UUID userId, UUID noteId, String title, String content) {
        return jdbcClient.sql("""
                UPDATE notes
                SET title = :title,
                    content = :content,
                    index_status = 'pending',
                    index_version = index_version + 1,
                    index_error = NULL,
                    indexed_at = NULL,
                    updated_at = now()
                WHERE id = :noteId
                  AND user_id = :userId
                  AND deleted_at IS NULL
                RETURNING *
                """)
            .param("title", title)
            .param("content", content)
            .param("noteId", noteId)
            .param("userId", userId)
            .query(NOTE_ROW_MAPPER)
            .optional();
    }

    @Override
    public Optional<Note> markPending(UUID userId, UUID noteId) {
        return jdbcClient.sql("""
                UPDATE notes
                SET index_status = 'pending',
                    index_version = index_version + 1,
                    index_error = NULL,
                    indexed_at = NULL,
                    updated_at = now()
                WHERE id = :noteId
                  AND user_id = :userId
                  AND deleted_at IS NULL
                RETURNING *
                """)
            .param("noteId", noteId)
            .param("userId", userId)
            .query(NOTE_ROW_MAPPER)
            .optional();
    }

    @Override
    public boolean markIndexing(UUID userId, UUID noteId, int indexVersion) {
        int updated = jdbcClient.sql("""
                UPDATE notes
                SET index_status = 'indexing',
                    index_error = NULL,
                    updated_at = now()
                WHERE id = :noteId
                  AND user_id = :userId
                  AND index_version = :indexVersion
                  AND deleted_at IS NULL
                """)
            .param("noteId", noteId)
            .param("userId", userId)
            .param("indexVersion", indexVersion)
            .update();
        return updated == 1;
    }

    @Override
    public void markReady(UUID userId, UUID noteId, int indexVersion) {
        jdbcClient.sql("""
                UPDATE notes
                SET index_status = 'ready',
                    index_error = NULL,
                    indexed_at = now(),
                    updated_at = now()
                WHERE id = :noteId
                  AND user_id = :userId
                  AND index_version = :indexVersion
                  AND deleted_at IS NULL
                """)
            .param("noteId", noteId)
            .param("userId", userId)
            .param("indexVersion", indexVersion)
            .update();
    }

    @Override
    public void markFailed(UUID userId, UUID noteId, int indexVersion, String error) {
        jdbcClient.sql("""
                UPDATE notes
                SET index_status = 'failed',
                    index_error = :error,
                    updated_at = now()
                WHERE id = :noteId
                  AND user_id = :userId
                  AND index_version = :indexVersion
                  AND deleted_at IS NULL
                """)
            .param("noteId", noteId)
            .param("userId", userId)
            .param("indexVersion", indexVersion)
            .param("error", error)
            .update();
    }

    @Override
    public boolean softDelete(UUID userId, UUID noteId) {
        int updated = jdbcClient.sql("""
                UPDATE notes
                SET deleted_at = now(),
                    updated_at = now()
                WHERE id = :noteId
                  AND user_id = :userId
                  AND deleted_at IS NULL
                """)
            .param("noteId", noteId)
            .param("userId", userId)
            .update();
        return updated == 1;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
