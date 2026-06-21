package com.home.ollamaproxy.controller;

import com.home.ollamaproxy.model.ChatRequest;
import com.home.ollamaproxy.model.ChatResponse;
import com.home.ollamaproxy.model.ConversationDetail;
import com.home.ollamaproxy.model.ConversationSummary;
import com.home.ollamaproxy.service.OllamaService;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
public class OllamaController {

    private final OllamaService ollamaService;

    public OllamaController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
        log.info("Initialized OllamaController");
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        log.debug("Health check requested");
        return ResponseEntity.ok(Map.of("status", "UP", "service", "ollama-proxy"));
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("POST /chat - conversationId={}, model={}", request.conversationId(), request.model());
        ChatResponse response = ollamaService.chat(request);
        log.info("Chat response: conversationId={}, conversationName={}", response.conversationId(), response.conversationName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversations")
    public ResponseEntity<Map<String, List<ConversationSummary>>> listConversations() {
        log.debug("GET /conversations");
        return ResponseEntity.ok(Map.of("conversations", ollamaService.listConversations()));
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ConversationDetail> getConversation(@PathVariable String conversationId) {
        log.debug("GET /conversations/{}", conversationId);
        return ResponseEntity.ok(ollamaService.getConversationDetail(conversationId));
    }

    @PostMapping("/conversations/{conversationId}/rename")
    public ResponseEntity<Map<String, String>> renameConversation(@PathVariable String conversationId) {
        log.info("POST /conversations/{}/rename", conversationId);
        String name = ollamaService.renameConversation(conversationId);
        log.info("Conversation {} renamed to: {}", conversationId, name);
        return ResponseEntity.ok(Map.of(
                "conversationId", conversationId,
                "name", name
        ));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> clearConversation(@PathVariable String conversationId) {
        log.info("DELETE /conversations/{}", conversationId);
        ollamaService.clearConversation(conversationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/models")
    public ResponseEntity<String> listModels() {
        log.debug("GET /models");
        return ResponseEntity.ok(ollamaService.listModels());
    }

    @PostMapping("/models/pull")
    public ResponseEntity<Map<String, String>> pullModel(@RequestBody Map<String, Object> payload) {
        String modelName = String.valueOf(payload.get("model"));
        log.info("POST /models/pull - model={}", modelName);
        ollamaService.pullModel(modelName);
        return ResponseEntity.accepted().body(Map.of(
                "status", "Downloading " + modelName + "..."
        ));
    }

    @DeleteMapping("/models/{name}")
    public ResponseEntity<Map<String, String>> deleteModel(@PathVariable("name") String name) {
        log.info("DELETE /models/{}", name);
        ollamaService.deleteModel(name);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Model " + name + " deleted successfully."
        ));
    }
}