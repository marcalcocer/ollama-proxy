package com.home.ollamaproxy.model;

import java.time.Instant;

public record ConversationSummary(
        String id,
        String name,
        int messageCount,
        Instant updatedAt
) {
}