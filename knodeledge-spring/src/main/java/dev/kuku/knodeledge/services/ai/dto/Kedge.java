package dev.kuku.knodeledge.services.ai.dto;

public record Kedge(
    String source,
    String target,
    String predicate,
    String context
) {}
