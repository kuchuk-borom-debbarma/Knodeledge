package dev.kuku.knodeledge.services.ai;

import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Abstracts away the flow of the whole AI workflow.
 */
@Service
public interface AI {
    void startIngestingRawNotes(ArrayList<String> notes);
}
