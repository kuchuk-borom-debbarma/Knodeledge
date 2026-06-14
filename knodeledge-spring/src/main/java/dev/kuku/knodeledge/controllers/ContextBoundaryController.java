package dev.kuku.knodeledge.controllers;

import dev.kuku.knodeledge.controllers.models.CreateContextBoundaryBody;
import dev.kuku.knodeledge.services.context_boundary.ContextBoundaryService;
import dev.kuku.knodeledge.services.context_boundary.dto.ContextBoundary;
import dev.kuku.knodeledge.infra.topo_tracer.KnodeledgeImportanceLevel;
import dev.kuku.knodeledge.infra.topo_tracer.Traced;
import dev.kuku.topotracer.sdk.Tracer;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/context-boundary")
@RequiredArgsConstructor
public class ContextBoundaryController {
    private final Tracer tracer;
    private final ContextBoundaryService contextBoundaryService;

    @Traced(value = "ContextBoundary-controller.create-context-boundary", type = KnodeledgeImportanceLevel.CONTROLLER)
    @PostMapping("/")
    ResponseEntity<ContextBoundary> createContextBoundary(@RequestParam String userId, @RequestBody CreateContextBoundaryBody body) {
        tracer.log("Creating context boundary for user " + userId + " - " + body);
        ContextBoundary contextBoundary = contextBoundaryService.createContextBoundary(body, userId);
        tracer.log("Created context boundary = " + contextBoundary);
        return ResponseEntity.ok(contextBoundary);
    }

    @Traced(value = "ContextBoundary-controller.get-context-boundaries", type = KnodeledgeImportanceLevel.CONTROLLER)
    @GetMapping("/user/{userId}")
    ResponseEntity<List<ContextBoundary>> getContextBoundariesByUserId(@PathVariable String userId) {
        tracer.log("Fetching context boundaries for user " + userId);
        List<ContextBoundary> list = contextBoundaryService.getContextBoundariesByUserId(userId);
        return ResponseEntity.ok(list);
    }
}
