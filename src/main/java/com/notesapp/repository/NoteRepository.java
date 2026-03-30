package com.notesapp.repository;

import com.notesapp.domain.Note;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByUserIdAndNotebook_IdOrderByCreatedAtDesc(Long userId, Long notebookId);

    List<Note> findTop20ByUserIdAndNotebook_IdOrderByCreatedAtDesc(Long userId, Long notebookId);

    List<Note> findTop50ByUserIdOrderByUpdatedAtDesc(Long userId);

    List<Note> findTop50ByUserIdAndNotebook_IdOrderByUpdatedAtDesc(Long userId, Long notebookId);

    java.util.Optional<Note> findByUserIdAndNotebook_IdAndTitleIgnoreCase(Long userId, Long notebookId, String title);

    Optional<Note> findByUserIdAndNotebook_IdAndOccurredAtAndRawText(Long userId, Long notebookId, Instant occurredAt, String rawText);
}
