package dev.kuku.knodeledge.controllers;

import dev.kuku.knodeledge.controllers.models.ChatRequest;
import dev.kuku.knodeledge.controllers.models.ChatResponse;
import dev.kuku.knodeledge.services.auth.dto.User;
import dev.kuku.knodeledge.services.rag.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {
    private final CurrentUser currentUser;
    private final ChatService chatService;

    @PostMapping
    public ChatResponse ask(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody ChatRequest request
    ) {
        User user = currentUser.require(authorization);
        return ChatResponse.from(chatService.ask(UUID.fromString(user.id()), request.question()));
    }
}
