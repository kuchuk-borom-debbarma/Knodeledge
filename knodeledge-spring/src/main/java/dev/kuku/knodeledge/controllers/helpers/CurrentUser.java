package dev.kuku.knodeledge.controllers.helpers;

import dev.kuku.knodeledge.services.auth.AuthService;
import dev.kuku.knodeledge.services.auth.dto.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentUser {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public CurrentUser(AuthService authService) {
        this.authService = authService;
    }

    public User require(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        try {
            return authService.getUserById(token);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bearer token");
        }
    }
}
