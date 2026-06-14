package dev.kuku.knodeledge.services.context_boundary.internal;

import dev.kuku.knodeledge.controllers.models.CreateContextBoundaryBody;
import dev.kuku.knodeledge.infra.topo_tracer.KnodeledgeImportanceLevel;
import dev.kuku.knodeledge.infra.topo_tracer.Traced;
import dev.kuku.knodeledge.repositories.ContextBoundaryRepository;
import dev.kuku.knodeledge.services.context_boundary.ContextBoundaryService;
import dev.kuku.knodeledge.services.context_boundary.dto.ContextBoundary;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@Primary
@RequiredArgsConstructor
public class InMemoryContextBoundaryService implements ContextBoundaryService {
    private final ContextBoundaryRepository contextBoundaryRepository;

    @Override
    public ContextBoundary createContextBoundary(CreateContextBoundaryBody body, String userId) {
        ContextBoundary boundary = new ContextBoundary(
            UUID.randomUUID().toString(),
            body.name(),
            body.context(),
            new Date(),
            new Date(),
            userId
        );
        return contextBoundaryRepository.save(boundary);
    }

    @Override
    @Traced(
        value = "context-boundary.load",
        type = KnodeledgeImportanceLevel.SERVICE)
    public ContextBoundary getContextBoundaryById(String contextBoundaryId, String userId) {
        return contextBoundaryRepository.findById(contextBoundaryId)
            .filter(cb -> cb.userId().equals(userId))
            .orElseThrow(() -> new IllegalArgumentException("Context boundary not found or access denied"));
    }

    @Override
    public List<ContextBoundary> getContextBoundariesByUserId(String userId) {
        return contextBoundaryRepository.findByUserId(userId);
    }
}
