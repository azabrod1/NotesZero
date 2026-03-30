package com.notesapp.repository;

import com.notesapp.domain.ChatEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatEventRepository extends JpaRepository<ChatEvent, Long> {
}
