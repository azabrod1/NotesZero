package com.notesapp.repository;

import com.notesapp.domain.ClarificationTask;
import com.notesapp.domain.enums.ClarificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClarificationTaskRepository extends JpaRepository<ClarificationTask, Long> {

    List<ClarificationTask> findByNote_IdAndStatus(Long noteId, ClarificationStatus status);

    List<ClarificationTask> findByStatusOrderByCreatedAtAsc(ClarificationStatus status);

    void deleteByNote_IdAndStatus(Long noteId, ClarificationStatus status);
}
