package dev.kuku.knodeledge.services.rag;

import dev.kuku.knodeledge.repositories.ChunkRepository;
import dev.kuku.knodeledge.services.rag.model.RetrievalCandidate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RetrievalService {
    private static final double DENSE_WEIGHT = 0.65;
    private static final double SPARSE_WEIGHT = 0.35;

    private final ChunkRepository chunkRepository;
    private final TextEmbeddingService embeddingService;
    private final int denseLimit;
    private final int sparseLimit;

    public RetrievalService(
        ChunkRepository chunkRepository,
        TextEmbeddingService embeddingService,
        @Value("${knodeledge.rag.dense-limit:30}") int denseLimit,
        @Value("${knodeledge.rag.sparse-limit:30}") int sparseLimit
    ) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.denseLimit = denseLimit;
        this.sparseLimit = sparseLimit;
    }

    public List<RetrievalCandidate> retrieve(UUID userId, String question) {
        float[] queryEmbedding = embeddingService.embed(question);
        List<RetrievalCandidate> dense = chunkRepository.denseSearch(
            userId,
            queryEmbedding,
            denseLimit
        );
        List<RetrievalCandidate> sparse = chunkRepository.sparseSearch(
            userId,
            question,
            sparseLimit
        );
        return merge(dense, sparse);
    }

    List<RetrievalCandidate> merge(
        List<RetrievalCandidate> dense,
        List<RetrievalCandidate> sparse
    ) {
        Map<UUID, RetrievalCandidate> candidates = new LinkedHashMap<>();
        for (RetrievalCandidate candidate : dense) {
            candidates.put(candidate.chunkId(), candidate);
        }
        for (RetrievalCandidate candidate : sparse) {
            candidates.merge(
                candidate.chunkId(),
                candidate,
                (existing, incoming) -> existing.withSparseScore(incoming.sparseScore())
            );
        }

        double maxDense = candidates.values().stream()
            .mapToDouble(RetrievalCandidate::denseScore)
            .max()
            .orElse(0);
        double maxSparse = candidates.values().stream()
            .mapToDouble(RetrievalCandidate::sparseScore)
            .max()
            .orElse(0);

        List<RetrievalCandidate> merged = new ArrayList<>();
        for (RetrievalCandidate candidate : candidates.values()) {
            double denseScore = maxDense <= 0 ? 0 : candidate.denseScore() / maxDense;
            double sparseScore = maxSparse <= 0 ? 0 : candidate.sparseScore() / maxSparse;
            merged.add(candidate.withHybridScore(
                DENSE_WEIGHT * denseScore + SPARSE_WEIGHT * sparseScore
            ));
        }
        merged.sort(Comparator.comparingDouble(RetrievalCandidate::hybridScore).reversed());
        return merged;
    }
}
