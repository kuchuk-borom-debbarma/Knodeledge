package dev.kuku.knodeledge.services.ai;

import org.springframework.stereotype.Service;

/**
 * Abstracts away the flow of the whole AIService workflow.
 */
@Service
public interface AIService {
    void ingestNote(String note, String contextBoundaryId, String actorId);

    String promptGraph(String prompt, String contextBoundaryId, String actorId);
}
