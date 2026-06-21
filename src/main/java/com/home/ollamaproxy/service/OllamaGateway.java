package com.home.ollamaproxy.service;

import com.home.ollamaproxy.model.OllamaChatRequest;
import com.home.ollamaproxy.model.OllamaChatResponse;
import java.util.Map;

public interface OllamaGateway {
    OllamaChatResponse chat(OllamaChatRequest request);

    String listModels();

    void pullModel(Map<String, Object> payload);

    void deleteModel(String name);
}
