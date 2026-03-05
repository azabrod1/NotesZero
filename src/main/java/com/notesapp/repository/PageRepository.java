package com.notesapp.repository;

import com.notesapp.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Long> {

    Optional<Page> findByNotebook_IdAndTitleIgnoreCase(Long notebookId, String title);
}
