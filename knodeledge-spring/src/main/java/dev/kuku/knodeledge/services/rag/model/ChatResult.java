package dev.kuku.knodeledge.services.rag.model;

import java.util.List;

public record ChatResult(
    String answer,
    boolean notEnoughInfo,
    boolean inference,
    List<ChatCitation> citations
) {
    public static ChatResult notEnough() {
        return new ChatResult(
            "I do not have enough information in your saved notes to answer that.",
            true,
            false,
            List.of()
        );
    }
}
