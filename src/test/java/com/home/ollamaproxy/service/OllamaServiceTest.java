package com.home.ollamaproxy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.home.ollamaproxy.config.ConversationProperties;
import com.home.ollamaproxy.model.ChatRequest;
import com.home.ollamaproxy.model.ChatResponse;
import com.home.ollamaproxy.model.ConversationDetail;
import com.home.ollamaproxy.model.ConversationSummary;
import com.home.ollamaproxy.model.OllamaChatRequest;
import com.home.ollamaproxy.model.OllamaChatResponse;
import com.home.ollamaproxy.model.OllamaMessage;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OllamaServiceTest {

    @Mock
    private OllamaGateway gateway;

    private ConversationService conversationService;
    private OllamaService service;

    private static final ConversationProperties PROPS = new ConversationProperties(
            10, Duration.ofHours(24),
            "Generate a title", "llama3.2:3b"
    );

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(PROPS);
        service = new OllamaService(gateway, conversationService, PROPS, "llama3");
    }

    @Test
    void statefulChatAppendsHistoryAndStoresAssistantReply() {
        String conversationId = UUID.randomUUID().toString();
        // First call = main chat, second call = async naming (captured by times(3) below),
        // third call = second chat message
        when(gateway.chat(any(OllamaChatRequest.class)))
                .thenReturn(new OllamaChatResponse("llama3", new OllamaMessage("assistant", "First reply"), true))
                .thenReturn(new OllamaChatResponse("llama3", new OllamaMessage("assistant", "Naming reply"), true))
                .thenReturn(new OllamaChatResponse("llama3", new OllamaMessage("assistant", "Second reply"), true));

        ChatResponse first = service.chat(new ChatRequest("Hello", conversationId, null));
        ChatResponse second = service.chat(new ChatRequest("How are you?", conversationId, null));

        ArgumentCaptor<OllamaChatRequest> captor = ArgumentCaptor.forClass(OllamaChatRequest.class);
        verify(gateway, times(3)).chat(captor.capture());

        List<OllamaChatRequest> requests = captor.getAllValues();
        // Index 0: first conversation message
        assertThat(requests.get(0).messages()).containsExactly(
                new OllamaMessage("user", "Hello")
        );
        // Index 1: async naming call (system prompt + user message)
        assertThat(requests.get(1).messages()).hasSize(2);
        assertThat(requests.get(1).messages().get(1)).isEqualTo(new OllamaMessage("user", "Hello"));
        // Index 2: second conversation message (with history)
        assertThat(requests.get(2).messages()).containsExactly(
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
        // Auto-naming runs synchronously in test (no AOP proxy) and uses the same mock
        assertThat(response.conversationName()).isEqualTo("Hi");
        assertThat(conversationService.getMessages(response.conversationId())).containsExactly(
                new OllamaMessage("user", "Hello"),
                new OllamaMessage("assistant", "Hi")
        );
    }

    @Test
    void listConversationsReturnsActiveOnly() {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();

        when(gateway.chat(any(OllamaChatRequest.class)))
                .thenReturn(new OllamaChatResponse("llama3", new OllamaMessage("assistant", "Reply"), true));

        service.chat(new ChatRequest("First", id1, null));
        service.chat(new ChatRequest("Second", id2, null));

        List<ConversationSummary> conversations = service.listConversations();
        assertThat(conversations).hasSize(2);
        assertThat(conversations).extracting(ConversationSummary::id).contains(id1, id2);
    }

    @Test
    void renameConversationGeneratesAndStoresName() {
        String conversationId = UUID.randomUUID().toString();
        when(gateway.chat(any(OllamaChatRequest.class)))
                .thenReturn(
                        new OllamaChatResponse("llama3", new OllamaMessage("assistant", "Hi"), true),       // main chat
                        new OllamaChatResponse("llama3", new OllamaMessage("assistant", "Auto name"), true), // async naming
                        new OllamaChatResponse("llama3", new OllamaMessage("assistant", "Greeting chat"), true) // rename
                );

        service.chat(new ChatRequest("Hello", conversationId, null));

        String name = service.renameConversation(conversationId);
        assertThat(name).isEqualTo("Greeting chat");
        assertThat(conversationService.getName(conversationId)).isEqualTo("Greeting chat");
    }

    @Test
    void conversationDetailReturnsIdNameAndMessages() {
        String conversationId = UUID.randomUUID().toString();
        when(gateway.chat(any(OllamaChatRequest.class)))
                .thenReturn(new OllamaChatResponse("llama3", new OllamaMessage("assistant", "Hi"), true));

        service.chat(new ChatRequest("Hello", conversationId, null));

        ConversationDetail detail = service.getConversationDetail(conversationId);
        assertThat(detail.id()).isEqualTo(conversationId);
        assertThat(detail.messages()).hasSize(2);

        ConversationDetail emptyDetail = service.getConversationDetail(UUID.randomUUID().toString());
        assertThat(emptyDetail.messages()).isEmpty();
    }
}