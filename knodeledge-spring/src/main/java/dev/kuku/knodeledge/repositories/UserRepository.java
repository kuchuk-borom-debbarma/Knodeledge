package dev.kuku.knodeledge.repositories;

import dev.kuku.knodeledge.services.auth.dto.User;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(String id);
    Optional<User> findByUsername(String username);
}
