package com.home.ollamaproxy.controller;

import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class OllamaController {

    private final OllamaChatClient chatModel;
    private final RestClient restClient;

    public OllamaController(OllamaChatClient chatModel, RestClient restClient) {
        this.chatModel = chatModel;
        this.restClient = restClient;
    }

    // 1. Health Check
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "ollama-proxy"));
    }

    // 2. Chat Inference (Using Spring AI)
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> payload) {
        String promptText = payload.getOrDefault("prompt", "Hello!");
        
        // Spring AI orchestration
        String response = chatModel.call(promptText);
        
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "response", response
        ));
    }

    // 3. List Local Models (Forwarding to native Ollama API)
    @GetMapping("/models")
    public ResponseEntity<String> listModels() {
        String response = restClient.get()
                .uri("/api/tags")
                .retrieve()
                .body(String.class);
        return ResponseEntity.ok(response);
    }

    // 4. Pull a Model from registry
    @PostMapping("/models/pull")
    public ResponseEntity<Map<String, String>> pullModel(@RequestBody Map<String, Object> payload) {
        String modelName = (String) payload.get("model");
        
        // Force synchronous completion response for the POC
        payload.put("stream", false);

        restClient.post()
                .uri("/api/pull")
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Model " + modelName + " pulled successfully into host machine."
        ));
    }
}
