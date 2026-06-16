package dev.kuku.knodeledge.services.rag;

import dev.kuku.knodeledge.services.ai.cache.CachedPromptExecutor;
import dev.kuku.knodeledge.services.rag.model.GeneratedAnswer;
import dev.kuku.knodeledge.services.rag.model.RetrievalCandidate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LlmAnswerGenerator implements AnswerGenerator {
    private static final String SYSTEM_PROMPT = """
        You answer questions using only the supplied saved-note chunks.
        Do not use model memory or outside knowledge.
        If the chunks do not contain enough information, set notEnoughInfo to true.
        If you infer something from multiple chunks, set inference to true and make that clear in the answer.
        Every factual answer must cite one or more chunk IDs from the supplied context.
        Return JSON only with shape:
        {
          "answer": "string",
          "notEnoughInfo": false,
          "inference": false,
          "citedChunkIds": ["chunk-id"]
        }
        """;

    private final CachedPromptExecutor promptExecutor;

    public LlmAnswerGenerator(CachedPromptExecutor promptExecutor) {
        this.promptExecutor = promptExecutor;
    }

    @Override
    public GeneratedAnswer answer(String question, List<RetrievalCandidate> context) {
        AnswerModelResponse response = promptExecutor.entity(
            "answer-rag-question",
            SYSTEM_PROMPT,
            userPrompt(question, context),
            AnswerModelResponse.class
        );
        if (response == null) {
            return GeneratedAnswer.notEnough();
        }
        return new GeneratedAnswer(
            response.answer(),
            response.notEnoughInfo(),
            response.inference(),
            response.citedChunkIds() == null ? List.of() : response.citedChunkIds()
        );
    }

    private String userPrompt(String question, List<RetrievalCandidate> context) {
        StringBuilder builder = new StringBuilder();
        builder.append("Question:\n").append(question).append("\n\nSaved-note chunks:\n");
        for (RetrievalCandidate candidate : context) {
            builder
                .append("Chunk ID: ").append(candidate.chunkId()).append('\n')
                .append("Note ID: ").append(candidate.noteId()).append('\n')
                .append("Note title: ").append(candidate.noteTitle()).append('\n')
                .append("Chunk index: ").append(candidate.chunkIndex()).append('\n')
                .append("Text:\n")
                .append(candidate.content())
                .append("\n\n");
        }
        return builder.toString();
    }

    public record AnswerModelResponse(
        String answer,
        boolean notEnoughInfo,
        boolean inference,
        List<String> citedChunkIds
    ) {
    }
}
