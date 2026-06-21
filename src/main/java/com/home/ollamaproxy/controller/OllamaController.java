package com.home.ollamaproxy.controller;

import com.home.ollamaproxy.model.ChatRequest;
import com.home.ollamaproxy.model.ChatResponse;
import com.home.ollamaproxy.model.OllamaMessage;
import com.home.ollamaproxy.service.OllamaService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class OllamaController {

    private final OllamaService ollamaService;

    public OllamaController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "ollama-proxy"));
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        return ResponseEntity.ok(ollamaService.chat(request));
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<List<OllamaMessage>> getConversation(@PathVariable String conversationId) {
        return ResponseEntity.ok(ollamaService.getConversation(conversationId));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> clearConversation(@PathVariable String conversationId) {
        ollamaService.clearConversation(conversationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/models")
    public ResponseEntity<String> listModels() {
        return ResponseEntity.ok(ollamaService.listModels());
    }

    @PostMapping("/models/pull")
    public ResponseEntity<Map<String, String>> pullModel(@RequestBody Map<String, Object> payload) {
        String modelName = String.valueOf(payload.get("model"));
        ollamaService.pullModel(modelName);
        return ResponseEntity.accepted().body(Map.of(
                "status", "Downloading " + modelName + "..."
        ));
    }

    @DeleteMapping("/models/{name}")
    public ResponseEntity<Map<String, String>> deleteModel(@PathVariable("name") String name) {
        ollamaService.deleteModel(name);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Model " + name + " deleted successfully."
        ));
    }
}
