package dev.kuku.knodeledge.services.rag.model;

import java.util.UUID;

public record RetrievalCandidate(
    UUID chunkId,
    UUID noteId,
    String noteTitle,
    int chunkIndex,
    String content,
    double denseScore,
    double sparseScore,
    double hybridScore,
    double rerankScore
) {
    public RetrievalCandidate withDenseScore(double score) {
        return new RetrievalCandidate(
            chunkId,
            noteId,
            noteTitle,
            chunkIndex,
            content,
            score,
            sparseScore,
            hybridScore,
            rerankScore
        );
    }

    public RetrievalCandidate withSparseScore(double score) {
        return new RetrievalCandidate(
            chunkId,
            noteId,
            noteTitle,
            chunkIndex,
            content,
            denseScore,
            score,
            hybridScore,
            rerankScore
        );
    }

    public RetrievalCandidate withHybridScore(double score) {
        return new RetrievalCandidate(
            chunkId,
            noteId,
            noteTitle,
            chunkIndex,
            content,
            denseScore,
            sparseScore,
            score,
            rerankScore
        );
    }

    public RetrievalCandidate withRerankScore(double score) {
        return new RetrievalCandidate(
            chunkId,
            noteId,
            noteTitle,
            chunkIndex,
            content,
            denseScore,
            sparseScore,
            hybridScore,
            score
        );
    }
}
