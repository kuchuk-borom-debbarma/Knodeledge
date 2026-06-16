package dev.kuku.knodeledge.services.rag.model;

import java.time.Instant;
import java.util.UUID;

public record Note(
    UUID id,
    UUID userId,
    String title,
    String content,
    IndexStatus indexStatus,
    int indexVersion,
    String indexError,
    Instant indexedAt,
    Instant deletedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
