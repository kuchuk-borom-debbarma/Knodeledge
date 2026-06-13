package dev.kuku.knodeledge.services.ai.internal;

import dev.kuku.knodeledge.services.ai.AIService;
import dev.kuku.topotracer.spring.Traced;
import dev.kuku.topotracer.sdk.TopoNodeType;
import dev.kuku.topotracer.sdk.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Map;

/**
 * LLM focused AIService work-flow
 */
@Service
public class LLMAIService implements AIService {

    @Autowired
    private NoteProcessor noteProcessor;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private NoteIOHelper noteIOHelper;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private Tracer tracer;

    @Override
    @Traced(value = "ai.process-notes", type = TopoNodeType.METHOD)
    public void startIngestingRawNotes(ArrayList<String> notes) {
        if (notes != null) {
            tracer.log("Starting raw note ingestion", Map.of("notes_count", String.valueOf(notes.size())));
            for (String note : notes) {
                tracer.log("Processing raw note: " + note);
                
                // 1. DB call
                String context = noteRepository.findContextForNote(note);
                tracer.log("Fetched database context for note", Map.of("context", context));

                // 2. IO call
                noteIOHelper.writeNoteLog(note);
                tracer.log("Completed disk write for note", 2);

                // 3. Process note (dynamic)
                noteProcessor.processSingleNote(note);

                // 4. Remote call
                try {
                    restTemplate.getForObject("http://localhost:3999/", String.class);
                    tracer.log("Dispatched telemetry remote call for note", 2);
                } catch (Exception e) {
                    // Suppress remote server offline errors for resilience in test suite
                }
            }
        }
    }
}
