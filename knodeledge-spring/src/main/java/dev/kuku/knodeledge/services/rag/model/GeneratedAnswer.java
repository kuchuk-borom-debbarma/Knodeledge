package dev.kuku.knodeledge.services.rag.model;

import java.util.List;

public record GeneratedAnswer(
    String answer,
    boolean notEnoughInfo,
    boolean inference,
    List<String> citedChunkIds
) {
    public static GeneratedAnswer notEnough() {
        return new GeneratedAnswer(
            "I do not have enough information in your saved notes to answer that.",
            true,
            false,
            List.of()
        );
    }
}
