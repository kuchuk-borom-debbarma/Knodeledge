package dev.kuku.knodeledge.services.context_boundary.internal;

import dev.kuku.knodeledge.controllers.models.CreateContextBoundaryBody;
import dev.kuku.knodeledge.services.context_boundary.ContextBoundaryService;
import dev.kuku.knodeledge.services.context_boundary.dto.ContextBoundary;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
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

    @Override
    public ContextBoundary getContextBoundaryById(String contextBoundaryId, String userId) {
        return new ContextBoundary(
            contextBoundaryId,
            "Stub Boundary Name",
            "Stub Context Description",
            new Date(),
            new Date(),
            userId
        );
    }

    @Override
    public List<ContextBoundary> getContextBoundariesByUserId(String userId) {
        return List.of(new ContextBoundary(
            UUID.randomUUID().toString(),
            "Stub Boundary Name",
            "Stub Context Description",
            new Date(),
            new Date(),
            userId
        ));
    }
}
