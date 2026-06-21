package com.home.ollamaproxy;

import com.home.ollamaproxy.config.ConversationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ConversationProperties.class)
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
