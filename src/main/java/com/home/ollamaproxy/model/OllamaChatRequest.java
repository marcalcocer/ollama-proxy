package com.home.ollamaproxy.model;

import java.util.List;

public record OllamaChatRequest(
        String model,
        List<OllamaMessage> messages,
        boolean stream
) {
}
