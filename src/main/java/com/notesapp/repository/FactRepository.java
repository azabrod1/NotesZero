package com.notesapp.repository;

import com.notesapp.domain.Fact;
import com.notesapp.domain.enums.FactValueType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface FactRepository extends JpaRepository<Fact, Long> {

    List<Fact> findByNotebookIdAndKeyNameOrderByCreatedAtAsc(Long notebookId, String keyName);

    List<Fact> findTop100ByNotebookIdOrderByCreatedAtDesc(Long notebookId);

    List<Fact> findByNote_Id(Long noteId);

    void deleteByNote_Id(Long noteId);

    boolean existsByNotebookIdAndKeyNameAndValueTypeAndValueNumberAndValueTextAndValueDatetimeAndNote_OccurredAt(
        Long notebookId,
        String keyName,
        FactValueType valueType,
        BigDecimal valueNumber,
        String valueText,
        Instant valueDatetime,
        Instant occurredAt
    );
}
