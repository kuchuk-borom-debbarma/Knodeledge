package dev.kuku.knodeledge.controllers;

import dev.kuku.knodeledge.controllers.models.RegisterRequest;
import dev.kuku.knodeledge.controllers.models.LoginRequest;
import dev.kuku.knodeledge.services.auth.AuthService;
import dev.kuku.knodeledge.services.auth.dto.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthUserResponse> register(@RequestBody RegisterRequest request) {
        User user = authService.createUser(request.username(), request.password());
        return ResponseEntity.ok(AuthUserResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthUserResponse> login(@RequestBody LoginRequest request) {
        boolean authenticated = authService.authenticate(request.username(), request.password());
        if (authenticated) {
            User user = authService.getUserByUsername(request.username());
            return ResponseEntity.ok(AuthUserResponse.from(user));
        }
        return ResponseEntity.status(401).build();
    }

    public record AuthUserResponse(String id, String username, String token) {
        static AuthUserResponse from(User user) {
            return new AuthUserResponse(user.id(), user.username(), user.id());
        }
    }
}
