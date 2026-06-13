package dev.kuku.knodeledge.services.ai.internal;

import dev.kuku.topotracer.spring.Traced;
import dev.kuku.topotracer.sdk.TopoNodeType;
import org.springframework.stereotype.Service;

@Service
public class NoteIOHelper {

    @Traced(value = "io.write-log-file", type = TopoNodeType.IO)
    public void writeNoteLog(String note) {
        // Simulate file/disk IO latency
        try {
            Thread.sleep(8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
