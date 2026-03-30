package com.notesapp.repository;

import com.notesapp.domain.DocumentOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentOperationRepository extends JpaRepository<DocumentOperation, Long> {

    List<DocumentOperation> findByNote_IdOrderByCreatedAtDesc(Long noteId);

    Optional<DocumentOperation> findByIdAndNote_Id(Long id, Long noteId);
}
