package dev.kuku.knodeledge.services.rag.model;

public record IndexedChunk(
    int chunkIndex,
    String content,
    float[] embedding,
    String metadataJson
) {
}
