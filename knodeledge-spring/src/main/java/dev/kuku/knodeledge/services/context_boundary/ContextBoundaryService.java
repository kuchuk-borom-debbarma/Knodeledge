package dev.kuku.knodeledge.services.context_boundary;

import dev.kuku.knodeledge.models.CreateContextBoundaryBody;
import dev.kuku.knodeledge.services.context_boundary.dto.ContextBoundary;
import org.springframework.stereotype.Service;

@Service
public interface ContextBoundaryService {
    ContextBoundary createContextBoundary(CreateContextBoundaryBody createContextBoundaryBody, String userId);
}
