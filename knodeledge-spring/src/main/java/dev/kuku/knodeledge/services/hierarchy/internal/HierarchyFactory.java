package dev.kuku.knodeledge.services.hierarchy.internal;

import dev.kuku.knodeledge.services.context_boundary.dto.ContextBoundary;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.BoundaryHierarchy;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.HierarchyNode;
import dev.kuku.knodeledge.services.hierarchy.model.HierarchyModels.NodeKind;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HierarchyFactory {
    public BoundaryHierarchy create(ContextBoundary boundary) {
        String rootId = "root_" + boundary.id().replace("-", "");
        var root = new HierarchyNode(
            rootId,
            "root:" + boundary.id(),
            null,
            NodeKind.ROOT,
            null,
            boundary.name().trim(),
            null,
            boundary.context().trim()
        );
        return new BoundaryHierarchy(boundary.id(), rootId, List.of(root), 1);
    }
}
