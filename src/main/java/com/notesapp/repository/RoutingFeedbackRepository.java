package com.notesapp.repository;

import com.notesapp.domain.RoutingFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoutingFeedbackRepository extends JpaRepository<RoutingFeedback, Long> {

    List<RoutingFeedback> findTop100ByOrderByCreatedAtDesc();
}
