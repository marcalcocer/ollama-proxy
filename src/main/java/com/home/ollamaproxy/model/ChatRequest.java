package com.home.ollamaproxy.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public record ChatRequest(
        @JsonAlias({"message", "content"}) String prompt,
        String conversationId,
        String model
) {
}
