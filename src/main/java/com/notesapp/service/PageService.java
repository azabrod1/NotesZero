package com.notesapp.service;

import com.notesapp.domain.Note;
import com.notesapp.domain.Notebook;
import com.notesapp.domain.Page;
import com.notesapp.domain.PageRevision;
import com.notesapp.repository.PageRepository;
import com.notesapp.repository.PageRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PageService {

    private static final String DEFAULT_TITLE = "Auto Summary";

    private final PageRepository pageRepository;
    private final PageRevisionRepository pageRevisionRepository;

    public PageService(PageRepository pageRepository, PageRevisionRepository pageRevisionRepository) {
        this.pageRepository = pageRepository;
        this.pageRevisionRepository = pageRevisionRepository;
    }

    @Transactional
    public void appendNoteToNotebookPage(Notebook notebook, Note note) {
        if (notebook == null || note == null) {
            return;
        }
        Instant now = Instant.now();
        Page page = pageRepository.findByNotebook_IdAndTitleIgnoreCase(notebook.getId(), DEFAULT_TITLE)
            .orElseGet(() -> new Page(notebook, DEFAULT_TITLE, "# " + notebook.getName() + "\n\n", now));

        String line = "- " + now + ": " + note.getRawText().trim() + "\n";
        String nextContent = page.getContentCurrent() + line;
        page.setContentCurrent(nextContent);
        page.setUpdatedAt(now);
        Page savedPage = pageRepository.save(page);

        PageRevision revision = new PageRevision(savedPage, nextContent, String.valueOf(note.getId()), 0.95, now);
        pageRevisionRepository.save(revision);
    }
}
