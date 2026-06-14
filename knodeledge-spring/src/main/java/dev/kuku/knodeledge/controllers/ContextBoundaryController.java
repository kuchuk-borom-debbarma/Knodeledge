package dev.kuku.knodeledge.controllers;

import dev.kuku.knodeledge.models.CreateContextBoundaryBody;
import dev.kuku.knodeledge.services.context_boundary.ContextBoundaryService;
import dev.kuku.knodeledge.services.context_boundary.dto.ContextBoundary;
import dev.kuku.knodeledge.infra.topo_tracer.KnodeledgeImportanceLevel;
import dev.kuku.knodeledge.infra.Traced;
import dev.kuku.topotracer.sdk.Tracer;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/context-boundary")
@RequiredArgsConstructor
public class ContextBoundaryController {
    private final Tracer tracer;
    private final ContextBoundaryService contextBoundaryService;

    @Traced(value = "ContextBoundary-controller.create-context-boundary", type = KnodeledgeImportanceLevel.CONTROLLER)
    @PostMapping("/")
    ResponseEntity<ContextBoundary> createContextBoundary(String userId, @RequestBody CreateContextBoundaryBody body) {
        tracer.log("Creating context boundary for user " + userId + " - " + body);
        ContextBoundary contextBoundary = contextBoundaryService.createContextBoundary(body, userId);
        tracer.log("Created context boundary = " + contextBoundary);
        return ResponseEntity.ok(contextBoundary);
    }
}


