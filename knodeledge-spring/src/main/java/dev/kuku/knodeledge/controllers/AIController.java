package dev.kuku.knodeledge.controllers;

import dev.kuku.knodeledge.models.IngestBody;
import dev.kuku.knodeledge.services.ai.AI;
import dev.kuku.topotracer.spring.Traced;
import dev.kuku.topotracer.sdk.TopoNodeType;
import dev.kuku.topotracer.sdk.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class AIController {
    final AI ai;
    final Tracer tracer;

    @GetMapping("/")
    @Traced(value = "ai-controller.ingest-notes", type = TopoNodeType.CONTROLLER)
    public ResponseEntity<String> ingestNotes(@RequestBody IngestBody body) {
        log.info("ingestNotes endpoint hit: {}", body);
        tracer.log("ingestNotes endpoint hit", Map.of("notesCount", String.valueOf(body.notes().size())));
        ai.startIngestingRawNotes(body.notes());
        return ResponseEntity.ok("Success");
    }
}
