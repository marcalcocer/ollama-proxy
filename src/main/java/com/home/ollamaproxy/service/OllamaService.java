package com.home.ollamaproxy.service;

import com.home.ollamaproxy.config.ConversationProperties;
import com.home.ollamaproxy.model.ChatRequest;
import com.home.ollamaproxy.model.ChatResponse;
import com.home.ollamaproxy.model.ConversationDetail;
import com.home.ollamaproxy.model.ConversationSummary;
import com.home.ollamaproxy.model.OllamaChatRequest;
import com.home.ollamaproxy.model.OllamaChatResponse;
import com.home.ollamaproxy.model.OllamaMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class OllamaService {

    private final OllamaGateway gateway;
    private final ConversationService conversationService;
    private final ConversationProperties conversationProperties;
    private final String defaultModel;

    public OllamaService(
            OllamaGateway gateway,
            ConversationService conversationService,
            ConversationProperties conversationProperties,
            @Value("${spring.ai.ollama.chat.options.model:llama3}") String defaultModel
    ) {
        this.gateway = gateway;
        this.conversationService = conversationService;
        this.conversationProperties = conversationProperties;
        this.defaultModel = defaultModel;
        log.info("Initialized with default model: {}", defaultModel);
    }

    public ChatResponse chat(ChatRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            log.warn("Chat request with null or blank prompt");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prompt is required");
        }

        String model = request.model() != null && !request.model().isBlank() ? request.model() : defaultModel;
        String conversationId = request.conversationId() != null && !request.conversationId().isBlank()
                ? request.conversationId()
                : UUID.randomUUID().toString();

        log.info("Chat request: conversationId={}, model={}, promptLength={}",
                conversationId, model, request.prompt().length());
        log.debug("Prompt: {}", request.prompt());

        OllamaMessage userMessage = new OllamaMessage("user", request.prompt());
        List<OllamaMessage> messages = buildMessages(conversationId, userMessage);
        log.debug("Conversation {} has {} messages in context", conversationId, messages.size());

        OllamaChatResponse ollamaResponse = gateway.chat(new OllamaChatRequest(model, messages, false));
        if (ollamaResponse == null || ollamaResponse.message() == null) {
            log.error("Ollama returned empty response for conversation {}", conversationId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ollama returned an empty response");
        }
        log.info("Ollama response for {}: model={}, done={}, responseLength={}",
                conversationId, ollamaResponse.model(), ollamaResponse.done(),
                ollamaResponse.message().content() != null ? ollamaResponse.message().content().length() : 0);

        conversationService.append(conversationId, ollamaResponse.message());

        boolean isFirstMessage = messages.size() == 1;
        if (isFirstMessage && conversationService.tryStartNaming(conversationId)) {
            log.info("Triggering async naming for new conversation {}", conversationId);
            generateConversationName(conversationId, request.prompt());
        }

        String conversationName = conversationService.getName(conversationId);
        if (conversationName != null) {
            log.debug("Conversation {} has name: {}", conversationId, conversationName);
        }

        return new ChatResponse(
                ollamaResponse.model() != null ? ollamaResponse.model() : model,
                ollamaResponse.message(),
                ollamaResponse.done(),
                conversationId,
                conversationName,
                ollamaResponse.message().content()
        );
    }

    public List<OllamaMessage> getConversation(String conversationId) {
        log.debug("Fetching conversation {}", conversationId);
        return conversationService.getMessages(conversationId);
    }

    public void clearConversation(String conversationId) {
        log.info("Clearing conversation {}", conversationId);
        conversationService.clear(conversationId);
    }

    public String listModels() {
        log.debug("Listing models");
        return gateway.listModels();
    }

    public void pullModel(String model) {
        log.info("Pulling model: {}", model);
        gateway.pullModel(Map.of("model", model, "stream", false));
    }

    public void deleteModel(String name) {
        log.info("Deleting model: {}", name);
        gateway.deleteModel(name);
    }

    public String renameConversation(String conversationId) {
        log.info("Renaming conversation {}", conversationId);
        List<OllamaMessage> messages = conversationService.getMessages(conversationId);
        if (messages.isEmpty()) {
            log.warn("Conversation {} not found for rename", conversationId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }

        String namingModel = conversationProperties.namingModel();
        String namingPrompt = conversationProperties.namingPrompt();
        String conversationText = buildConversationText(messages);

        String fullPrompt = namingPrompt + "\n\n" + conversationText;
        List<OllamaMessage> renameMessages = List.of(
                new OllamaMessage("user", fullPrompt)
        );

        OllamaChatResponse response = gateway.chat(new OllamaChatRequest(namingModel, renameMessages, false));
        if (response == null || response.message() == null || response.message().content() == null) {
            log.error("Failed to generate name for conversation {}", conversationId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to generate name");
        }

        String name = response.message().content().strip();
        conversationService.setName(conversationId, name);
        log.info("Conversation {} renamed to: {}", conversationId, name);
        return name;
    }

    public List<ConversationSummary> listConversations() {
        log.debug("Listing conversations");
        return conversationService.listConversations();
    }

    public ConversationDetail getConversationDetail(String conversationId) {
        log.debug("Getting detail for conversation {}", conversationId);
        List<OllamaMessage> messages = conversationService.getMessages(conversationId);
        String name = conversationService.getName(conversationId);
        return new ConversationDetail(conversationId, name, messages);
    }

    @Async
    public void generateConversationName(String conversationId, String userMessage) {
        log.info("Generating name for conversation {} (async)", conversationId);
        try {
            String namingPrompt = conversationProperties.namingPrompt();
            String namingModel = conversationProperties.namingModel();

            List<OllamaMessage> namingMessages = List.of(
                    new OllamaMessage("system", namingPrompt),
                    new OllamaMessage("user", userMessage)
            );

            OllamaChatResponse response = gateway.chat(
                    new OllamaChatRequest(namingModel, namingMessages, false)
            );

            if (response != null && response.message() != null && response.message().content() != null) {
                String name = response.message().content().strip();
                conversationService.setName(conversationId, name);
                log.info("Generated name for conversation {}: {}", conversationId, name);
            } else {
                log.warn("Empty naming response for conversation {}", conversationId);
                conversationService.clearNamingInProgress(conversationId);
            }
        } catch (Exception e) {
            log.error("Failed to generate conversation name for {}", conversationId, e);
            conversationService.clearNamingInProgress(conversationId);
        }
    }

    private List<OllamaMessage> buildMessages(String conversationId, OllamaMessage userMessage) {
        List<OllamaMessage> history = new ArrayList<>(conversationService.getMessages(conversationId));
        history.add(userMessage);
        trim(history);
        conversationService.append(conversationId, userMessage);
        return history;
    }

    private void trim(List<OllamaMessage> messages) {
        int before = messages.size();
        while (messages.size() > conversationProperties.maxHistoryMessages()) {
            messages.remove(0);
        }
        if (messages.size() < before) {
            log.debug("Trimmed conversation history from {} to {} messages", before, messages.size());
        }
    }

    private String buildConversationText(List<OllamaMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (OllamaMessage msg : messages) {
            sb.append(msg.role()).append(": ").append(msg.content()).append("\n");
        }
        return sb.toString();
    }
}