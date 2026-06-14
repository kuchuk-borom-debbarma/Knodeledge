package dev.kuku.knodeledge.services.context_boundary;

import dev.kuku.knodeledge.controllers.models.CreateContextBoundaryBody;
import dev.kuku.knodeledge.services.context_boundary.dto.ContextBoundary;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public interface ContextBoundaryService {
    ContextBoundary createContextBoundary(CreateContextBoundaryBody createContextBoundaryBody, String userId);
    ContextBoundary getContextBoundaryById(String contextBoundaryId, String userId);
    List<ContextBoundary> getContextBoundariesByUserId(String userId);
}
