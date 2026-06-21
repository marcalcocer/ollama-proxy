package com.home.ollamaproxy.service;

import com.home.ollamaproxy.config.ConversationProperties;
import com.home.ollamaproxy.model.ConversationSummary;
import com.home.ollamaproxy.model.OllamaMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class ConversationService {

    private final ConcurrentHashMap<String, ConversationState> store = new ConcurrentHashMap<>();
    private final ConversationProperties properties;

    public ConversationService(ConversationProperties properties) {
        this.properties = properties;
        log.info("Initialized with TTL={}s, maxHistory={}", properties.ttl().toSeconds(), properties.maxHistoryMessages());
    }

    public List<OllamaMessage> append(String conversationId, OllamaMessage message) {
        validateConversationId(conversationId);
        Objects.requireNonNull(message, "message must not be null");

        ConversationState state = store.computeIfAbsent(conversationId, id -> {
            log.debug("Creating new conversation state for {}", id);
            return new ConversationState();
        });
        synchronized (state) {
            state.messages.add(message);
            state.updatedAt = Instant.now();
            log.debug("Appended {} message to {}. Total messages: {}", message.role(), conversationId, state.messages.size());
            trim(state.messages);
            return List.copyOf(state.messages);
        }
    }

    public List<OllamaMessage> getMessages(String conversationId) {
        validateConversationId(conversationId);
        ConversationState state = store.get(conversationId);
        if (state == null) {
            log.debug("Conversation {} not found, returning empty list", conversationId);
            return List.of();
        }

        synchronized (state) {
            state.updatedAt = Instant.now();
            log.debug("Retrieved {} messages for conversation {}", state.messages.size(), conversationId);
            return List.copyOf(state.messages);
        }
    }

    public void clear(String conversationId) {
        validateConversationId(conversationId);
        ConversationState removed = store.remove(conversationId);
        if (removed != null) {
            log.info("Cleared conversation {} ({} messages)", conversationId, removed.messages.size());
        } else {
            log.debug("Conversation {} not found for clear", conversationId);
        }
    }

    public boolean tryStartNaming(String conversationId) {
        ConversationState state = store.get(conversationId);
        if (state == null) return false;
        synchronized (state) {
            if (state.namingInProgress) {
                log.debug("Naming already in progress for {}", conversationId);
                return false;
            }
            state.namingInProgress = true;
            log.debug("Started naming for {}", conversationId);
            return true;
        }
    }

    public void setName(String conversationId, String name) {
        ConversationState state = store.get(conversationId);
        if (state == null) {
            log.warn("Conversation {} not found when setting name", conversationId);
            return;
        }
        synchronized (state) {
            state.name = name;
            state.namingInProgress = false;
            log.debug("Set name for {}: {}", conversationId, name);
        }
    }

    public String getName(String conversationId) {
        ConversationState state = store.get(conversationId);
        if (state == null) return null;
        synchronized (state) {
            return state.name;
        }
    }

    public void clearNamingInProgress(String conversationId) {
        ConversationState state = store.get(conversationId);
        if (state == null) return;
        synchronized (state) {
            state.namingInProgress = false;
            log.debug("Cleared naming in progress for {}", conversationId);
        }
    }

    public List<ConversationSummary> listConversations() {
        List<ConversationSummary> result = new ArrayList<>();
        Instant now = Instant.now();
        for (var entry : store.entrySet()) {
            ConversationState state = entry.getValue();
            synchronized (state) {
                if (isExpired(state, now)) {
                    log.debug("Skipping expired conversation {}", entry.getKey());
                    continue;
                }
                result.add(new ConversationSummary(
                        entry.getKey(),
                        state.name,
                        state.messages.size(),
                        state.updatedAt
                ));
            }
        }
        log.debug("Listed {} active conversations", result.size());
        return result;
    }

    public void cleanupExpired() {
        Instant now = Instant.now();
        int before = store.size();
        store.entrySet().removeIf(entry -> {
            boolean expired = isExpired(entry.getValue(), now);
            if (expired) {
                log.info("Expiring conversation {}", entry.getKey());
            }
            return expired;
        });
        int removed = before - store.size();
        if (removed > 0) {
            log.info("Cleanup removed {} expired conversations ({} remaining)", removed, store.size());
        }
    }

    @Scheduled(fixedDelay = 300000)
    public void scheduledCleanup() {
        log.debug("Running scheduled cleanup");
        cleanupExpired();
    }

    private boolean isExpired(ConversationState state, Instant now) {
        synchronized (state) {
            return state.updatedAt.plus(properties.ttl()).isBefore(now);
        }
    }

    private void trim(List<OllamaMessage> messages) {
        int before = messages.size();
        while (messages.size() > properties.maxHistoryMessages()) {
            messages.remove(0);
        }
        if (messages.size() < before) {
            log.debug("Trimmed messages from {} to {}", before, messages.size());
        }
    }

    private void validateConversationId(String conversationId) {
        try {
            UUID.fromString(conversationId);
        } catch (RuntimeException ex) {
            log.warn("Invalid conversationId: {}", conversationId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId must be a valid UUID");
        }
    }

    private static final class ConversationState {
        private final List<OllamaMessage> messages = new ArrayList<>();
        private Instant updatedAt = Instant.now();
        private String name;
        private boolean namingInProgress;
    }
}