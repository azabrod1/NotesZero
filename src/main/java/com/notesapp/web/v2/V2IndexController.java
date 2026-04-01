package com.notesapp.web.v2;

import com.notesapp.service.routing.IndexRefreshService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v2/index")
public class V2IndexController {

    private final IndexRefreshService indexRefreshService;

    public V2IndexController(IndexRefreshService indexRefreshService) {
        this.indexRefreshService = indexRefreshService;
    }

    @PostMapping("/backfill")
    public ResponseEntity<Map<String, String>> backfill() {
        indexRefreshService.backfillAll();
        return ResponseEntity.ok(Map.of("status", "complete"));
    }

    @PostMapping("/refresh-stale")
    public ResponseEntity<Map<String, Object>> refreshStale() {
        int refreshed = indexRefreshService.refreshStaleIndexes();
        return ResponseEntity.ok(Map.of("status", "complete", "refreshed", refreshed));
    }
}
