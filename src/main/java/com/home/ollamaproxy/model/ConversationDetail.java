package com.home.ollamaproxy.model;

import java.util.List;

public record ConversationDetail(
        String id,
        String name,
        List<OllamaMessage> messages
) {
}