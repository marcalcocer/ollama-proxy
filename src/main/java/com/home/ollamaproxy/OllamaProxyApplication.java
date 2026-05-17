package com.home.ollamaproxy;

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
    public RestClient restClient() {
        // Used to communicate with Ollama's native management API directly
        return RestClient.builder().baseUrl("http://172.21.144.1:11434").build();
    }
}
