package com.notesapp.repository;

import com.notesapp.domain.NoteRoutingIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NoteRoutingIndexRepository extends JpaRepository<NoteRoutingIndex, Long> {

    List<NoteRoutingIndex> findByNotebookId(Long notebookId);

    List<NoteRoutingIndex> findByActivityStatus(String activityStatus);

    @Query("SELECT n FROM NoteRoutingIndex n WHERE n.activityStatus = 'active' ORDER BY n.refreshedAt DESC")
    List<NoteRoutingIndex> findActiveOrderByRefreshedAtDesc();

    @Query("SELECT n FROM NoteRoutingIndex n WHERE LOWER(n.entityTags) LIKE LOWER(CONCAT('%', :entity, '%'))")
    List<NoteRoutingIndex> findByEntityTagContaining(String entity);

    @Query("SELECT n FROM NoteRoutingIndex n WHERE LOWER(n.aliases) LIKE LOWER(CONCAT('%', :alias, '%'))")
    List<NoteRoutingIndex> findByAliasContaining(String alias);

    @Query("SELECT n FROM NoteRoutingIndex n WHERE LOWER(n.lexicalText) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<NoteRoutingIndex> findByLexicalTextContaining(String term);
}
