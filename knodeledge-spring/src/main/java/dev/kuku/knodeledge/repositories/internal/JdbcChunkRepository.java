package dev.kuku.knodeledge.repositories.internal;

import dev.kuku.knodeledge.repositories.ChunkRepository;
import dev.kuku.knodeledge.services.rag.model.IndexedChunk;
import dev.kuku.knodeledge.services.rag.model.RetrievalCandidate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Repository
public class JdbcChunkRepository implements ChunkRepository {
    private static final RowMapper<RetrievalCandidate> CANDIDATE_ROW_MAPPER =
        (rs, rowNum) -> new RetrievalCandidate(
            rs.getObject("chunk_id", UUID.class),
            rs.getObject("note_id", UUID.class),
            rs.getString("note_title"),
            rs.getInt("chunk_index"),
            rs.getString("content"),
            score(rs, "dense_score"),
            score(rs, "sparse_score"),
            0,
            0
        );

    private final JdbcClient jdbcClient;

    public JdbcChunkRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void deleteByNote(UUID userId, UUID noteId) {
        jdbcClient.sql("""
                DELETE FROM note_chunks
                WHERE user_id = :userId
                  AND note_id = :noteId
                """)
            .param("userId", userId)
            .param("noteId", noteId)
            .update();
    }

    @Override
    public void insertChunks(UUID userId, UUID noteId, List<IndexedChunk> chunks) {
        for (IndexedChunk chunk : chunks) {
            jdbcClient.sql("""
                    INSERT INTO note_chunks (
                        id,
                        user_id,
                        note_id,
                        chunk_index,
                        content,
                        embedding,
                        metadata
                    )
                    VALUES (
                        :id,
                        :userId,
                        :noteId,
                        :chunkIndex,
                        :content,
                        CAST(:embedding AS vector),
                        CAST(:metadata AS jsonb)
                    )
                    """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("noteId", noteId)
                .param("chunkIndex", chunk.chunkIndex())
                .param("content", chunk.content())
                .param("embedding", vectorLiteral(chunk.embedding()))
                .param("metadata", chunk.metadataJson())
                .update();
        }
    }

    @Override
    public List<RetrievalCandidate> denseSearch(UUID userId, float[] embedding, int limit) {
        return jdbcClient.sql("""
                SELECT c.id AS chunk_id,
                       c.note_id AS note_id,
                       COALESCE(n.title, 'Untitled note') AS note_title,
                       c.chunk_index,
                       c.content,
                       1 - (c.embedding <=> CAST(:embedding AS vector)) AS dense_score,
                       0::double precision AS sparse_score
                FROM note_chunks c
                JOIN notes n ON n.id = c.note_id
                WHERE c.user_id = :userId
                  AND n.user_id = :userId
                  AND n.deleted_at IS NULL
                  AND c.embedding IS NOT NULL
                ORDER BY c.embedding <=> CAST(:embedding AS vector)
                LIMIT :limit
                """)
            .param("userId", userId)
            .param("embedding", vectorLiteral(embedding))
            .param("limit", limit)
            .query(CANDIDATE_ROW_MAPPER)
            .list();
    }

    @Override
    public List<RetrievalCandidate> sparseSearch(UUID userId, String question, int limit) {
        return jdbcClient.sql("""
                WITH query AS (
                    SELECT websearch_to_tsquery('english', :question) AS q
                )
                SELECT c.id AS chunk_id,
                       c.note_id AS note_id,
                       COALESCE(n.title, 'Untitled note') AS note_title,
                       c.chunk_index,
                       c.content,
                       0::double precision AS dense_score,
                       ts_rank_cd(c.search_text, query.q) AS sparse_score
                FROM note_chunks c
                JOIN notes n ON n.id = c.note_id
                CROSS JOIN query
                WHERE c.user_id = :userId
                  AND n.user_id = :userId
                  AND n.deleted_at IS NULL
                  AND c.search_text @@ query.q
                ORDER BY sparse_score DESC
                LIMIT :limit
                """)
            .param("userId", userId)
            .param("question", question)
            .param("limit", limit)
            .query(CANDIDATE_ROW_MAPPER)
            .list();
    }

    private static double score(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? 0 : value;
    }

    private static String vectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.ROOT, "%.8f", embedding[index]));
        }
        return builder.append(']').toString();
    }
}
