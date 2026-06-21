package com.home.ollamaproxy.service;

import com.home.ollamaproxy.model.OllamaChatRequest;
import com.home.ollamaproxy.model.OllamaChatResponse;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpMethod;

@Slf4j
@Service
public class RestClientOllamaGateway implements OllamaGateway {

    private final RestClient restClient;

    public RestClientOllamaGateway(RestClient restClient) {
        this.restClient = restClient;
        log.info("Initialized with base URL: {}", restClient);
    }

    @Override
    public OllamaChatResponse chat(OllamaChatRequest request) {
        log.debug("POST /api/chat - model={}, messages={}", request.model(), request.messages().size());
        OllamaChatResponse response = restClient.post()
                .uri("/api/chat")
                .body(request)
                .retrieve()
                .body(OllamaChatResponse.class);
        if (response != null) {
            log.debug("Ollama chat response: model={}, done={}", response.model(), response.done());
        }
        return response;
    }

    @Override
    public String listModels() {
        log.debug("GET /api/tags");
        return restClient.get()
                .uri("/api/tags")
                .retrieve()
                .body(String.class);
    }

    @Override
    public void pullModel(Map<String, Object> payload) {
        log.info("POST /api/pull - model={}", payload.get("model"));
        restClient.post()
                .uri("/api/pull")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void deleteModel(String name) {
        log.info("DELETE /api/delete - model={}", name);
        restClient.method(HttpMethod.DELETE)
                .uri("/api/delete")
                .body(Map.of("model", name))
                .retrieve()
                .toBodilessEntity();
    }
}
