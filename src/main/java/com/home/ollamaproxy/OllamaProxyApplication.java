package com.home.ollamaproxy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class OllamaProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(OllamaProxyApplication.class, args);
    }

    @Bean
    public RestClient restClient(@Value("${spring.ai.ollama.base-url}") String baseUrl) {
        // Used to communicate with Ollama's native management API directly
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}
