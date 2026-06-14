package dev.kuku.knodeledge.controllers;

import dev.kuku.knodeledge.infra.topo_tracer.KnodeledgeImportanceLevel;
import dev.kuku.knodeledge.infra.topo_tracer.Traced;
import dev.kuku.knodeledge.controllers.models.IngestNoteBody;
import dev.kuku.knodeledge.services.ai.AIService;
import dev.kuku.topotracer.sdk.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("api/v1/aiService")
@RequiredArgsConstructor
@Slf4j
public class AIController {
    private final AIService aiService;
    private final Tracer tracer;

    @PostMapping("/ingest")
    @Traced(value = "aiService-controller.ingest-note", type = KnodeledgeImportanceLevel.CONTROLLER)
    public ResponseEntity<Void> ingestNote(@RequestBody IngestNoteBody body) {
        log.info("ingestNote endpoint hit: {}", body);
        tracer.log("ingestNote endpoint hit", Map.of(
            "contextBoundaryId", body.contextBoundaryId(),
            "actorId", body.actorId()
        ));
        aiService.ingestNote(body.note(), body.contextBoundaryId(), body.actorId());
        return ResponseEntity.ok().build();
    }
}
