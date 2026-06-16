package dev.kuku.knodeledge.repositories.internal;

import dev.kuku.knodeledge.services.auth.dto.User;
import dev.kuku.knodeledge.services.rag.model.IndexedChunk;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "KNODELEDGE_RUN_POSTGRES_TESTS", matches = "true")
class JdbcRagRepositoryPostgresTests {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres")
    )
        .withDatabaseName("knodeledge")
        .withUsername("knodeledge")
        .withPassword("knodeledge");

    private static JdbcUserRepository users;
    private static JdbcNoteRepository notes;
    private static JdbcChunkRepository chunks;

    @BeforeAll
    static void setupDatabase() {
        POSTGRES.start();
        Flyway.configure()
            .dataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            )
            .locations("filesystem:src/main/resources/db/migration")
            .load()
            .migrate();
        var dataSource = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        users = new JdbcUserRepository(jdbcClient);
        notes = new JdbcNoteRepository(jdbcClient);
        chunks = new JdbcChunkRepository(jdbcClient);
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void searchesOnlyTheCurrentUsersChunks() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        users.save(new User(userId.toString(), "owner-" + userId, "pw"));
        users.save(new User(otherUserId.toString(), "other-" + otherUserId, "pw"));

        var note = notes.create(userId, "Owner note", "alpha project decision");
        var otherNote = notes.create(otherUserId, "Other note", "alpha private decision");
        chunks.insertChunks(
            userId,
            note.id(),
            List.of(new IndexedChunk(0, "alpha project decision", vector(0.1f), "{}"))
        );
        chunks.insertChunks(
            otherUserId,
            otherNote.id(),
            List.of(new IndexedChunk(0, "alpha private decision", vector(0.9f), "{}"))
        );
        notes.markReady(userId, note.id(), note.indexVersion());
        notes.markReady(otherUserId, otherNote.id(), otherNote.indexVersion());

        var sparse = chunks.sparseSearch(userId, "alpha decision", 30);
        var dense = chunks.denseSearch(userId, vector(0.1f), 30);

        assertEquals(1, sparse.size());
        assertEquals(note.id(), sparse.getFirst().noteId());
        assertEquals(1, dense.size());
        assertEquals(note.id(), dense.getFirst().noteId());
        assertTrue(notes.softDelete(userId, note.id()));
        assertEquals(0, chunks.sparseSearch(userId, "alpha decision", 30).size());
    }

    private static float[] vector(float seed) {
        float[] vector = new float[1536];
        for (int index = 0; index < vector.length; index++) {
            vector[index] = seed;
        }
        return vector;
    }
}
