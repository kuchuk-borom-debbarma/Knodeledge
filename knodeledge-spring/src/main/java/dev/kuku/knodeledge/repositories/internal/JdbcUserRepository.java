package dev.kuku.knodeledge.repositories.internal;

import dev.kuku.knodeledge.repositories.UserRepository;
import dev.kuku.knodeledge.services.auth.dto.User;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcUserRepository implements UserRepository {
    private final JdbcClient jdbcClient;

    public JdbcUserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public User save(User user) {
        jdbcClient.sql("""
                INSERT INTO app_users (id, username, password)
                VALUES (:id, :username, :password)
                ON CONFLICT (id) DO UPDATE
                SET username = excluded.username,
                    password = excluded.password
                """)
            .param("id", UUID.fromString(user.id()))
            .param("username", user.username())
            .param("password", user.password())
            .update();
        return user;
    }

    @Override
    public Optional<User> findById(String id) {
        return jdbcClient.sql("""
                SELECT id, username, password
                FROM app_users
                WHERE id = :id
                """)
            .param("id", UUID.fromString(id))
            .query((rs, rowNum) -> new User(
                rs.getObject("id", UUID.class).toString(),
                rs.getString("username"),
                rs.getString("password")
            ))
            .optional();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jdbcClient.sql("""
                SELECT id, username, password
                FROM app_users
                WHERE username = :username
                """)
            .param("username", username)
            .query((rs, rowNum) -> new User(
                rs.getObject("id", UUID.class).toString(),
                rs.getString("username"),
                rs.getString("password")
            ))
            .optional();
    }
}
