package dev.kuku.knodeledge.services.auth.internal;

import dev.kuku.knodeledge.repositories.UserRepository;
import dev.kuku.knodeledge.services.auth.AuthService;
import dev.kuku.knodeledge.services.auth.dto.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DatabaseAuthService implements AuthService {
    private final UserRepository userRepository;

    @Override
    public User createUser(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        String normalizedUsername = username.trim();
        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        User user = new User(UUID.randomUUID().toString(), normalizedUsername, password);
        return userRepository.save(user);
    }

    @Override
    public User getUserById(String id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Override
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Override
    public boolean authenticate(String username, String password) {
        return userRepository.findByUsername(username)
            .map(user -> user.password().equals(password))
            .orElse(false);
    }
}
