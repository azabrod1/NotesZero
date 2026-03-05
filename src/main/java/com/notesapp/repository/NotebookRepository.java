package com.notesapp.repository;

import com.notesapp.domain.Notebook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotebookRepository extends JpaRepository<Notebook, Long> {

    List<Notebook> findByUserIdOrderByNameAsc(Long userId);

    Optional<Notebook> findByUserIdAndNameIgnoreCase(Long userId, String name);
}
