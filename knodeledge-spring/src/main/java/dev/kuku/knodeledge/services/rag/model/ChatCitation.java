package dev.kuku.knodeledge.services.rag.model;

public record ChatCitation(
    String chunkId,
    String noteId,
    String noteTitle,
    int chunkIndex
) {
}
