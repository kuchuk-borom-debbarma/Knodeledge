package dev.kuku.knodeledge.controllers;

import dev.kuku.knodeledge.controllers.models.CreateNoteRequest;
import dev.kuku.knodeledge.controllers.models.NoteResponse;
import dev.kuku.knodeledge.controllers.models.UpdateNoteRequest;
import dev.kuku.knodeledge.services.auth.dto.User;
import dev.kuku.knodeledge.services.rag.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NotesController {
    private final CurrentUser currentUser;
    private final NoteService noteService;

    @PostMapping
    public ResponseEntity<NoteResponse> create(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody CreateNoteRequest request
    ) {
        User user = currentUser.require(authorization);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(NoteResponse.from(noteService.create(
                UUID.fromString(user.id()),
                request.title(),
                request.content()
            )));
    }

    @GetMapping
    public List<NoteResponse> list(
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User user = currentUser.require(authorization);
        return noteService.list(UUID.fromString(user.id()))
            .stream()
            .map(NoteResponse::from)
            .toList();
    }

    @GetMapping("/{id}")
    public NoteResponse get(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @PathVariable String id
    ) {
        User user = currentUser.require(authorization);
        return NoteResponse.from(noteService.get(UUID.fromString(user.id()), parseId(id)));
    }

    @PatchMapping("/{id}")
    public NoteResponse update(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @PathVariable String id,
        @RequestBody UpdateNoteRequest request
    ) {
        User user = currentUser.require(authorization);
        return NoteResponse.from(noteService.update(
            UUID.fromString(user.id()),
            parseId(id),
            request.title(),
            request.content()
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @PathVariable String id
    ) {
        User user = currentUser.require(authorization);
        noteService.delete(UUID.fromString(user.id()), parseId(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reindex")
    public NoteResponse reindex(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @PathVariable String id
    ) {
        User user = currentUser.require(authorization);
        return NoteResponse.from(noteService.reindex(UUID.fromString(user.id()), parseId(id)));
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid note id");
        }
    }
}
