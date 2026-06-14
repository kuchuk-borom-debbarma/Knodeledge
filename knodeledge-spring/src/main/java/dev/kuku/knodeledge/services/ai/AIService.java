package dev.kuku.knodeledge.services.ai;

import dev.kuku.knodeledge.services.ai.dto.Kgraph;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Abstracts away the flow of the whole AIService workflow.
 */
@Service
public interface AIService {
    Kgraph generateLocalGraphFromNotes(ArrayList<String> notes);
    void ingestNote(String note);
}
