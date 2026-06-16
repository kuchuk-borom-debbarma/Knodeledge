package dev.kuku.knodeledge.services.rag;

import dev.kuku.knodeledge.services.rag.model.RetrievalCandidate;

import java.util.List;

public interface CandidateReranker {
    List<RetrievalCandidate> rerank(String question, List<RetrievalCandidate> candidates);
}
