package dev.kuku.knodeledge.services.auth.internal;

import dev.kuku.knodeledge.repositories.UserRepository;
import dev.kuku.knodeledge.services.auth.AuthService;
import dev.kuku.knodeledge.services.auth.dto.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InMemoryAuthService implements AuthService {
    private final UserRepository userRepository;

    @Override
    public User createUser(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        User user = new User(UUID.randomUUID().toString(), username, password);
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
