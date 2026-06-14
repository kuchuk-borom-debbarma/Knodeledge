package dev.kuku.knodeledge.controllers;

import dev.kuku.knodeledge.infra.KnodeledgeImportance;
import dev.kuku.knodeledge.models.IngestBody;
import dev.kuku.knodeledge.services.ai.AIService;
import dev.kuku.knodeledge.services.ai.dto.Kgraph;
import dev.kuku.topotracer.spring.Traced;
import dev.kuku.topotracer.sdk.TopoNodeType;
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
    final AIService aiService;
    final Tracer tracer;

    @PostMapping("/")
    @Traced(value = "aiService-controller.ingest-notes", type = TopoNodeType.CONTROLLER)
    public ResponseEntity<Kgraph> ingestNotes(@RequestBody IngestBody body) {
        log.info("ingestNotes endpoint hit: {}", body);
        tracer.log("ingestNotes endpoint hit", Map.of("notesCount", String.valueOf(body.notes().size())), KnodeledgeImportance.CONTROLLER);
        Kgraph graph = aiService.generateLocalGraphFromNotes(body.notes());
        return ResponseEntity.ok(graph);
    }
}

