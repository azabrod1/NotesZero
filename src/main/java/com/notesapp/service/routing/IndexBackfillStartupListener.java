package com.notesapp.service.routing;

import com.notesapp.repository.NoteRoutingIndexRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Auto-backfills the routing index on startup if it is empty.
 */
@Component
public class IndexBackfillStartupListener {

    private static final Logger log = LoggerFactory.getLogger(IndexBackfillStartupListener.class);

    private final NoteRoutingIndexRepository noteRoutingIndexRepo;
    private final IndexRefreshService indexRefreshService;

    public IndexBackfillStartupListener(NoteRoutingIndexRepository noteRoutingIndexRepo,
                                        IndexRefreshService indexRefreshService) {
        this.noteRoutingIndexRepo = noteRoutingIndexRepo;
        this.indexRefreshService = indexRefreshService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (noteRoutingIndexRepo.count() == 0) {
            log.info("Routing index is empty — running initial backfill.");
            indexRefreshService.backfillAll();
        } else {
            log.info("Routing index already populated ({} entries). Skipping backfill.", noteRoutingIndexRepo.count());
        }
    }
}
