package com.notesapp.service;

import com.notesapp.domain.Notebook;
import com.notesapp.repository.NotebookRepository;
import com.notesapp.web.dto.CreateNotebookRequest;
import com.notesapp.web.dto.NotebookResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotebookService {

    public static final Long DEFAULT_USER_ID = 1L;

    private final NotebookRepository notebookRepository;

    public NotebookService(NotebookRepository notebookRepository) {
        this.notebookRepository = notebookRepository;
    }

    @PostConstruct
    @Transactional
    public void createDefaultNotebooks() {
        ensureNotebookExists("Dog notes", "Dog health and behavior notes");
        ensureNotebookExists("Work websites", "Work links, resources, and references");
        ensureNotebookExists("Family health", "Baby and family health observations");
    }

    @Transactional
    public NotebookResponse createNotebook(CreateNotebookRequest request) {
        notebookRepository.findByUserIdAndNameIgnoreCase(DEFAULT_USER_ID, request.getName())
            .ifPresent(existing -> {
                throw new ValidationException("Notebook already exists: " + existing.getName());
            });
        Notebook notebook = new Notebook(DEFAULT_USER_ID, request.getName().trim(), request.getDescription().trim(), Instant.now());
        Notebook saved = notebookRepository.save(notebook);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<NotebookResponse> listNotebooks() {
        return notebookRepository.findByUserIdOrderByNameAsc(DEFAULT_USER_ID)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Notebook getNotebookRequired(Long notebookId) {
        return notebookRepository.findById(notebookId)
            .filter(notebook -> DEFAULT_USER_ID.equals(notebook.getUserId()))
            .orElseThrow(() -> new NotFoundException("Notebook not found: " + notebookId));
    }

    @Transactional(readOnly = true)
    public List<Notebook> listNotebookEntities() {
        return notebookRepository.findByUserIdOrderByNameAsc(DEFAULT_USER_ID);
    }

    private void ensureNotebookExists(String name, String description) {
        if (notebookRepository.findByUserIdAndNameIgnoreCase(DEFAULT_USER_ID, name).isEmpty()) {
            notebookRepository.save(new Notebook(DEFAULT_USER_ID, name, description, Instant.now()));
        }
    }

    private NotebookResponse toResponse(Notebook notebook) {
        return new NotebookResponse(notebook.getId(), notebook.getName(), notebook.getDescription(), notebook.getCreatedAt());
    }
}
