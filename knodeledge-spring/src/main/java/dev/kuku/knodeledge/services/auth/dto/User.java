package dev.kuku.knodeledge.services.auth.dto;

import java.util.Date;

public record User(String id, String username, String displayName, Date createdAt, Date updatedAt) {
}
