package com.home.ollamaproxy.service;

import com.home.ollamaproxy.config.ConversationProperties;
import com.home.ollamaproxy.model.ChatRequest;
import com.home.ollamaproxy.model.ChatResponse;
import com.home.ollamaproxy.model.OllamaChatRequest;
import com.home.ollamaproxy.model.OllamaChatResponse;
import com.home.ollamaproxy.model.OllamaMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
    }

    public ChatResponse chat(ChatRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prompt is required");
        }

        String model = request.model() != null && !request.model().isBlank() ? request.model() : defaultModel;
        String conversationId = request.conversationId() != null && !request.conversationId().isBlank()
                ? request.conversationId()
                : UUID.randomUUID().toString();

        OllamaMessage userMessage = new OllamaMessage("user", request.prompt());
        List<OllamaMessage> messages = buildMessages(conversationId, userMessage);

        OllamaChatResponse ollamaResponse = gateway.chat(new OllamaChatRequest(model, messages, false));
        if (ollamaResponse == null || ollamaResponse.message() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ollama returned an empty response");
        }

        conversationService.append(conversationId, ollamaResponse.message());

        return new ChatResponse(
                ollamaResponse.model() != null ? ollamaResponse.model() : model,
                ollamaResponse.message(),
                ollamaResponse.done(),
                conversationId,
                ollamaResponse.message().content()
        );
    }

    public List<OllamaMessage> getConversation(String conversationId) {
        return conversationService.getMessages(conversationId);
    }

    public void clearConversation(String conversationId) {
        conversationService.clear(conversationId);
    }

    public String listModels() {
        return gateway.listModels();
    }

    public void pullModel(String model) {
        gateway.pullModel(Map.of("model", model, "stream", false));
    }

    public void deleteModel(String name) {
        gateway.deleteModel(name);
    }

    private List<OllamaMessage> buildMessages(String conversationId, OllamaMessage userMessage) {
        List<OllamaMessage> history = new ArrayList<>(conversationService.getMessages(conversationId));
        history.add(userMessage);
        trim(history);
        conversationService.append(conversationId, userMessage);
        return history;
    }

    private void trim(List<OllamaMessage> messages) {
        while (messages.size() > conversationProperties.maxHistoryMessages()) {
            messages.remove(0);
        }
    }
}
