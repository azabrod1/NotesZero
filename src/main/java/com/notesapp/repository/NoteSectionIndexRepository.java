package com.notesapp.repository;

import com.notesapp.domain.NoteSectionIndex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoteSectionIndexRepository extends JpaRepository<NoteSectionIndex, Long> {

    List<NoteSectionIndex> findByNoteId(Long noteId);

    void deleteByNoteId(Long noteId);
}
