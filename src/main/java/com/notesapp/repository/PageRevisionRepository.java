package com.notesapp.repository;

import com.notesapp.domain.PageRevision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageRevisionRepository extends JpaRepository<PageRevision, Long> {
}
