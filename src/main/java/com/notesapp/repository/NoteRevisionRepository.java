package com.notesapp.repository;

import com.notesapp.domain.NoteRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoteRevisionRepository extends JpaRepository<NoteRevision, Long> {

    List<NoteRevision> findByNote_IdOrderByCreatedAtDesc(Long noteId);

    Optional<NoteRevision> findTopByNote_IdOrderByRevisionNumberDesc(Long noteId);
}
