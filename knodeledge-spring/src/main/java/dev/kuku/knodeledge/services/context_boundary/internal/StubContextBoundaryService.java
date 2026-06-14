package dev.kuku.knodeledge.services.context_boundary.internal;

import dev.kuku.knodeledge.models.CreateContextBoundaryBody;
import dev.kuku.knodeledge.services.context_boundary.ContextBoundaryService;
import dev.kuku.knodeledge.services.context_boundary.dto.ContextBoundary;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Service
public class StubContextBoundaryService implements ContextBoundaryService {
    @Override
    public ContextBoundary createContextBoundary(CreateContextBoundaryBody body, String userId) {
        return new ContextBoundary(
            UUID.randomUUID().toString(),
            body.name(),
            body.context(),
            new Date(),
            new Date(),
            userId
        );
    }
}
