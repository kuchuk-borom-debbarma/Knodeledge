package dev.kuku.knodeledge.services.rag;

import dev.kuku.knodeledge.services.rag.model.GeneratedAnswer;
import dev.kuku.knodeledge.services.rag.model.RetrievalCandidate;

import java.util.List;

public interface AnswerGenerator {
    GeneratedAnswer answer(String question, List<RetrievalCandidate> context);
}
