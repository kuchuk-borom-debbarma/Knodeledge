package dev.kuku.knodeledge.repositories.internal;

import dev.kuku.knodeledge.repositories.UserRepository;
import dev.kuku.knodeledge.services.auth.dto.User;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> idToUser = new ConcurrentHashMap<>();
    private final Map<String, User> usernameToUser = new ConcurrentHashMap<>();

    @Override
    public User save(User user) {
        idToUser.put(user.id(), user);
        usernameToUser.put(user.username(), user);
        return user;
    }

    @Override
    public Optional<User> findById(String id) {
        return Optional.ofNullable(idToUser.get(id));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(usernameToUser.get(username));
    }
}
