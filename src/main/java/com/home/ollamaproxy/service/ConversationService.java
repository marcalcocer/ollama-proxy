package com.home.ollamaproxy.service;

import com.home.ollamaproxy.config.ConversationProperties;
import com.home.ollamaproxy.model.OllamaMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConversationService {

    private final ConcurrentHashMap<String, ConversationState> store = new ConcurrentHashMap<>();
    private final ConversationProperties properties;

    public ConversationService(ConversationProperties properties) {
        this.properties = properties;
    }

    public List<OllamaMessage> append(String conversationId, OllamaMessage message) {
        validateConversationId(conversationId);
        Objects.requireNonNull(message, "message must not be null");

        ConversationState state = store.computeIfAbsent(conversationId, id -> new ConversationState());
        synchronized (state) {
            state.messages.add(message);
            state.lastTouched = Instant.now();
            trim(state.messages);
            return List.copyOf(state.messages);
        }
    }

    public List<OllamaMessage> getMessages(String conversationId) {
        validateConversationId(conversationId);
        ConversationState state = store.get(conversationId);
        if (state == null) {
            return List.of();
        }

        synchronized (state) {
            state.lastTouched = Instant.now();
            return List.copyOf(state.messages);
        }
    }

    public void clear(String conversationId) {
        validateConversationId(conversationId);
        store.remove(conversationId);
    }

    public void cleanupExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    @Scheduled(fixedDelay = 300000)
    public void scheduledCleanup() {
        cleanupExpired();
    }

    private boolean isExpired(ConversationState state, Instant now) {
        synchronized (state) {
            return state.lastTouched.plus(properties.ttl()).isBefore(now);
        }
    }

    private void trim(List<OllamaMessage> messages) {
        while (messages.size() > properties.maxHistoryMessages()) {
            messages.remove(0);
        }
    }

    private void validateConversationId(String conversationId) {
        try {
            UUID.fromString(conversationId);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId must be a valid UUID");
        }
    }

    private static final class ConversationState {
        private final List<OllamaMessage> messages = new ArrayList<>();
        private Instant lastTouched = Instant.now();
    }
}
