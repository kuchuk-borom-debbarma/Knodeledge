package dev.kuku.knodeledge.controllers.models;

import dev.kuku.knodeledge.services.rag.model.ChatCitation;
import dev.kuku.knodeledge.services.rag.model.ChatResult;

import java.util.List;

public record ChatResponse(
    String answer,
    boolean notEnoughInfo,
    boolean inference,
    List<ChatCitation> citations
) {
    public static ChatResponse from(ChatResult result) {
        return new ChatResponse(
            result.answer(),
            result.notEnoughInfo(),
            result.inference(),
            result.citations()
        );
    }
}
