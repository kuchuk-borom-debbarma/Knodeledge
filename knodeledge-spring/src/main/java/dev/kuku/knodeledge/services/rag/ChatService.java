package dev.kuku.knodeledge.services.rag;

import dev.kuku.knodeledge.services.rag.model.ChatCitation;
import dev.kuku.knodeledge.services.rag.model.ChatResult;
import dev.kuku.knodeledge.services.rag.model.GeneratedAnswer;
import dev.kuku.knodeledge.services.rag.model.RetrievalCandidate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatService {
    private final RetrievalService retrievalService;
    private final CandidateReranker reranker;
    private final AnswerGenerator answerGenerator;
    private final int rerankLimit;
    private final int answerContextLimit;
    private final double minRerankScore;

    public ChatService(
        RetrievalService retrievalService,
        CandidateReranker reranker,
        AnswerGenerator answerGenerator,
        @Value("${knodeledge.rag.rerank-limit:50}") int rerankLimit,
        @Value("${knodeledge.rag.answer-context-limit:10}") int answerContextLimit,
        @Value("${knodeledge.rag.min-rerank-score:0.15}") double minRerankScore
    ) {
        this.retrievalService = retrievalService;
        this.reranker = reranker;
        this.answerGenerator = answerGenerator;
        this.rerankLimit = rerankLimit;
        this.answerContextLimit = answerContextLimit;
        this.minRerankScore = minRerankScore;
    }

    public ChatResult ask(UUID userId, String question) {
        if (question == null || question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question must not be blank");
        }

        List<RetrievalCandidate> candidates = retrievalService.retrieve(userId, question.trim());
        if (candidates.isEmpty()) {
            return ChatResult.notEnough();
        }

        List<RetrievalCandidate> rerankInput = candidates.stream()
            .limit(rerankLimit)
            .toList();
        List<RetrievalCandidate> ranked = rerankWithFallback(question, rerankInput);
        if (ranked.isEmpty() || ranked.getFirst().rerankScore() < minRerankScore) {
            return ChatResult.notEnough();
        }

        List<RetrievalCandidate> context = ranked.stream()
            .limit(answerContextLimit)
            .toList();
        GeneratedAnswer generated = answerGenerator.answer(question.trim(), context);
        return validateAndMap(generated, context);
    }

    private List<RetrievalCandidate> rerankWithFallback(
        String question,
        List<RetrievalCandidate> candidates
    ) {
        try {
            List<RetrievalCandidate> ranked = reranker.rerank(question, candidates);
            if (ranked != null && !ranked.isEmpty()) {
                return ranked;
            }
        } catch (RuntimeException ignored) {
            // Weighted hybrid score is the v1 fallback when the reranker provider is unavailable.
        }
        return candidates.stream()
            .map(candidate -> candidate.withRerankScore(candidate.hybridScore()))
            .sorted(Comparator.comparingDouble(RetrievalCandidate::rerankScore).reversed())
            .toList();
    }

    private ChatResult validateAndMap(GeneratedAnswer generated, List<RetrievalCandidate> context) {
        if (generated == null || generated.notEnoughInfo()) {
            return ChatResult.notEnough();
        }
        if (generated.answer() == null || generated.answer().isBlank()) {
            return ChatResult.notEnough();
        }
        if (generated.citedChunkIds() == null || generated.citedChunkIds().isEmpty()) {
            return ChatResult.notEnough();
        }

        Map<String, RetrievalCandidate> allowed = context.stream()
            .collect(Collectors.toMap(
                candidate -> candidate.chunkId().toString(),
                Function.identity()
            ));
        List<ChatCitation> citations = generated.citedChunkIds().stream()
            .distinct()
            .map(allowed::get)
            .map(candidate -> candidate == null
                ? null
                : new ChatCitation(
                    candidate.chunkId().toString(),
                    candidate.noteId().toString(),
                    candidate.noteTitle(),
                    candidate.chunkIndex()
                ))
            .toList();
        if (citations.stream().anyMatch(java.util.Objects::isNull)) {
            return ChatResult.notEnough();
        }
        return new ChatResult(
            generated.answer(),
            false,
            generated.inference(),
            citations
        );
    }
}
