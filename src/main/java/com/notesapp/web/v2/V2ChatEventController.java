package com.notesapp.web.v2;

import com.notesapp.service.v2.ChatCommitService;
import com.notesapp.service.v2.ChatCommitRateLimiter;
import com.notesapp.web.dto.v2.CommitChatRequest;
import com.notesapp.web.dto.v2.CommitChatResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v2/chat-events")
public class V2ChatEventController {

    private final ChatCommitService chatCommitService;
    private final ChatCommitRateLimiter chatCommitRateLimiter;

    public V2ChatEventController(ChatCommitService chatCommitService,
                                 ChatCommitRateLimiter chatCommitRateLimiter) {
        this.chatCommitService = chatCommitService;
        this.chatCommitRateLimiter = chatCommitRateLimiter;
    }

    @PostMapping("/commit")
    public CommitChatResponse commit(@Valid @RequestBody CommitChatRequest request,
                                     HttpServletRequest httpServletRequest) {
        chatCommitRateLimiter.assertAllowed(actorKey(httpServletRequest));
        return chatCommitService.commit(request);
    }

    private String actorKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
