package com.notesapp.web.v2;

import com.notesapp.service.eval.EvalReport;
import com.notesapp.service.eval.RoutingEvalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/v2/eval")
public class V2EvalController {

    private final RoutingEvalService routingEvalService;

    public V2EvalController(RoutingEvalService routingEvalService) {
        this.routingEvalService = routingEvalService;
    }

    @PostMapping("/routing")
    public ResponseEntity<EvalReport> runRoutingEval() throws IOException {
        EvalReport report = routingEvalService.runEval();
        return ResponseEntity.ok(report);
    }
}
