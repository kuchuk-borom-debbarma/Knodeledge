package dev.kuku.knodeledge.controllers;

import dev.kuku.knodeledge.services.hierarchy.HierarchyService;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.BoundaryHierarchy;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyLevelResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hierarchy")
@RequiredArgsConstructor
public class HierarchyController {
    private final HierarchyService hierarchyService;

    @GetMapping("/{boundaryId}/level")
    public ResponseEntity<HierarchyLevelResponse> getLevel(
        @PathVariable String boundaryId,
        @RequestParam String userId,
        @RequestParam(required = false) String nodeId
    ) {
        return ResponseEntity.ok(hierarchyService.getLevel(boundaryId, userId, nodeId));
    }

    @GetMapping("/debug/{boundaryId}")
    public ResponseEntity<BoundaryHierarchy> getDebugHierarchy(
        @PathVariable String boundaryId,
        @RequestParam String userId
    ) {
        return ResponseEntity.ok(hierarchyService.getDebugHierarchy(boundaryId, userId));
    }
}
