package com.notesapp.repository;

import com.notesapp.domain.NotebookRoutingIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotebookRoutingIndexRepository extends JpaRepository<NotebookRoutingIndex, Long> {

    @Query("SELECT n FROM NotebookRoutingIndex n WHERE LOWER(n.entityTags) LIKE LOWER(CONCAT('%', :entity, '%'))")
    List<NotebookRoutingIndex> findByEntityTagContaining(String entity);
}
