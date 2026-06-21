package com.home.ollamaproxy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.home.ollamaproxy.config.ConversationProperties;
import com.home.ollamaproxy.model.ChatRequest;
import com.home.ollamaproxy.model.ChatResponse;
import com.home.ollamaproxy.model.OllamaChatRequest;
import com.home.ollamaproxy.model.OllamaChatResponse;
import com.home.ollamaproxy.model.OllamaMessage;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class OllamaServiceTest {

    @Mock
    private OllamaGateway gateway;

    private ConversationService conversationService;
    private OllamaService service;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(new ConversationProperties(10, Duration.ofHours(24)));
        service = new OllamaService(gateway, conversationService, new ConversationProperties(10, Duration.ofHours(24)), "llama3");
    }

    @Test
    void statefulChatAppendsHistoryAndStoresAssistantReply() {
        String conversationId = UUID.randomUUID().toString();
        when(gateway.chat(any(OllamaChatRequest.class)))
                .thenReturn(new OllamaChatResponse("llama3", new OllamaMessage("assistant", "First reply"), true))
                .thenReturn(new OllamaChatResponse("llama3", new OllamaMessage("assistant", "Second reply"), true));

        ChatResponse first = service.chat(new ChatRequest("Hello", conversationId, null));
        ChatResponse second = service.chat(new ChatRequest("How are you?", conversationId, null));

        ArgumentCaptor<OllamaChatRequest> captor = ArgumentCaptor.forClass(OllamaChatRequest.class);
        verify(gateway, times(2)).chat(captor.capture());

        List<OllamaChatRequest> requests = captor.getAllValues();
        assertThat(requests.get(0).messages()).containsExactly(
                new OllamaMessage("user", "Hello")
        );
        assertThat(requests.get(1).messages()).containsExactly(
                new OllamaMessage("user", "Hello"),
                new OllamaMessage("assistant", "First reply"),
                new OllamaMessage("user", "How are you?")
        );

        assertThat(first.response()).isEqualTo("First reply");
        assertThat(second.response()).isEqualTo("Second reply");
        assertThat(conversationService.getMessages(conversationId)).containsExactly(
                new OllamaMessage("user", "Hello"),
                new OllamaMessage("assistant", "First reply"),
                new OllamaMessage("user", "How are you?"),
                new OllamaMessage("assistant", "Second reply")
        );
    }

    @Test
    void statelessChatAutoGeneratesConversationId() {
        when(gateway.chat(any(OllamaChatRequest.class)))
                .thenReturn(new OllamaChatResponse("llama3", new OllamaMessage("assistant", "Hi"), true));

        ChatResponse response = service.chat(new ChatRequest("Hello", null, null));

        assertThat(response.conversationId()).isNotNull();
        assertThat(response.response()).isEqualTo("Hi");
        assertThat(conversationService.getMessages(response.conversationId())).containsExactly(
                new OllamaMessage("user", "Hello"),
                new OllamaMessage("assistant", "Hi")
        );
    }
}
