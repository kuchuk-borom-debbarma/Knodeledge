package dev.kuku.knodeledge.services.ai.internal;

import dev.kuku.topotracer.spring.Traced;
import dev.kuku.topotracer.sdk.TopoNodeType;
import org.springframework.stereotype.Repository;

@Repository
public class NoteRepository {

    @Traced(value = "db.find-note-context", type = TopoNodeType.DB_CALL)
    public String findContextForNote(String note) {
        // Simulate database lookup latency
        try {
            Thread.sleep(15);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "context-for-" + note;
    }
}
