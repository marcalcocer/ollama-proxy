package com.home.ollamaproxy.service;

import com.home.ollamaproxy.model.OllamaChatRequest;
import com.home.ollamaproxy.model.OllamaChatResponse;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpMethod;

@Service
public class RestClientOllamaGateway implements OllamaGateway {

    private final RestClient restClient;

    public RestClientOllamaGateway(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public OllamaChatResponse chat(OllamaChatRequest request) {
        return restClient.post()
                .uri("/api/chat")
                .body(request)
                .retrieve()
                .body(OllamaChatResponse.class);
    }

    @Override
    public String listModels() {
        return restClient.get()
                .uri("/api/tags")
                .retrieve()
                .body(String.class);
    }

    @Override
    public void pullModel(Map<String, Object> payload) {
        restClient.post()
                .uri("/api/pull")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void deleteModel(String name) {
        restClient.method(HttpMethod.DELETE)
                .uri("/api/delete")
                .body(Map.of("model", name))
                .retrieve()
                .toBodilessEntity();
    }
}
