package com.notesapp.service.v2;

import com.notesapp.config.AiProperties;
import com.notesapp.service.RateLimitExceededException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatCommitRateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> requestWindows = new ConcurrentHashMap<>();
    private final int limitCount;
    private final Duration window;

    public ChatCommitRateLimiter(AiProperties aiProperties) {
        this.limitCount = Math.max(1, aiProperties.getChatCommitRateLimitCount());
        this.window = Duration.ofMinutes(Math.max(1, aiProperties.getChatCommitRateLimitWindowMinutes()));
    }

    public void assertAllowed(String actorKey) {
        String resolvedKey = actorKey == null || actorKey.isBlank() ? "anonymous" : actorKey.trim();
        Deque<Instant> timestamps = requestWindows.computeIfAbsent(resolvedKey, ignored -> new ArrayDeque<>());
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.removeFirst();
            }

            if (timestamps.size() >= limitCount) {
                Instant oldestRetained = timestamps.peekFirst();
                long retryAfterSeconds = oldestRetained == null
                    ? window.toSeconds()
                    : Math.max(1L, Duration.between(now, oldestRetained.plus(window)).toSeconds());
                throw new RateLimitExceededException(
                    "Too many chat requests. Limit is %d requests per %d minutes."
                        .formatted(limitCount, window.toMinutes()),
                    retryAfterSeconds
                );
            }

            timestamps.addLast(now);
        }
    }
}
