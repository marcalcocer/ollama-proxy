package com.home.ollamaproxy.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.conversations")
public record ConversationProperties(
        int maxHistoryMessages,
        Duration ttl,
        String namingPrompt,
        String namingModel
) {
}
