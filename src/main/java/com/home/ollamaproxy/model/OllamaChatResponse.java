package com.home.ollamaproxy.model;

public record OllamaChatResponse(
        String model,
        OllamaMessage message,
        boolean done
) {
}
