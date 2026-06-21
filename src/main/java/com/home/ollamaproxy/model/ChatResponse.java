package com.home.ollamaproxy.model;

public record ChatResponse(
        String model,
        OllamaMessage message,
        boolean done,
        String conversationId,
        String conversationName,
        String response
) {
}
