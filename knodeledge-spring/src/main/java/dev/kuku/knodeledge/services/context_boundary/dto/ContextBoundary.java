package dev.kuku.knodeledge.services.context_boundary.dto;

import java.util.Date;

public record ContextBoundary(String id, String name, String context, Date createdAt, Date updatedAt, String userId) {
}
