package dev.kuku.knodeledge.services.rag;

import dev.kuku.knodeledge.services.ai.cache.CachedPromptExecutor;
import dev.kuku.knodeledge.services.rag.model.RetrievalCandidate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmCandidateReranker implements CandidateReranker {
    private static final String SYSTEM_PROMPT = """
        You rerank saved-note chunks for a personal knowledge base.
        Score each candidate from 0.0 to 1.0 for relevance to the question.
        Return JSON only with shape: {"scores":[{"chunkId":"...","score":0.0}]}.
        Do not answer the question.
        """;

    private final CachedPromptExecutor promptExecutor;

    public LlmCandidateReranker(CachedPromptExecutor promptExecutor) {
        this.promptExecutor = promptExecutor;
    }

    @Override
    public List<RetrievalCandidate> rerank(String question, List<RetrievalCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        RerankResponse response = promptExecutor.entity(
            "rerank-rag-candidates",
            SYSTEM_PROMPT,
            userPrompt(question, candidates),
            RerankResponse.class
        );
        Map<String, Double> scores = new HashMap<>();
        if (response != null && response.scores() != null) {
            for (RerankScore score : response.scores()) {
                if (score.chunkId() != null) {
                    scores.put(score.chunkId(), clamp(score.score()));
                }
            }
        }
        return candidates.stream()
            .map(candidate -> candidate.withRerankScore(
                scores.getOrDefault(candidate.chunkId().toString(), candidate.hybridScore())
            ))
            .sorted(Comparator.comparingDouble(RetrievalCandidate::rerankScore).reversed())
            .toList();
    }

    private String userPrompt(String question, List<RetrievalCandidate> candidates) {
        StringBuilder builder = new StringBuilder();
        builder.append("Question:\n").append(question).append("\n\nCandidates:\n");
        for (RetrievalCandidate candidate : candidates) {
            builder
                .append("Chunk ID: ").append(candidate.chunkId()).append('\n')
                .append("Note: ").append(candidate.noteTitle()).append('\n')
                .append("Chunk index: ").append(candidate.chunkIndex()).append('\n')
                .append("Text:\n")
                .append(truncate(candidate.content(), 3500))
                .append("\n\n");
        }
        return builder.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }

    public record RerankResponse(List<RerankScore> scores) {
    }

    public record RerankScore(String chunkId, double score) {
    }
}
