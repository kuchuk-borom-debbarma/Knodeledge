package dev.kuku.knodeledge.services.ai;

import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Abstracts away the flow of the whole AIService workflow.
 */
@Service
public interface AIService {
    void startIngestingRawNotes(ArrayList<String> notes);
}
