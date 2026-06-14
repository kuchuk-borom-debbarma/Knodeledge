package dev.kuku.knodeledge.services.auth;

import dev.kuku.knodeledge.services.auth.dto.User;

public interface AuthService {
    User createUser(String username, String password);
    User getUserById(String id);
    User getUserByUsername(String username);
    boolean authenticate(String username, String password);
}
