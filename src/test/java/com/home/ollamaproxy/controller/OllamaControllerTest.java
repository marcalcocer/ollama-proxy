package com.home.ollamaproxy.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.home.ollamaproxy.model.ChatResponse;
import com.home.ollamaproxy.model.ConversationDetail;
import com.home.ollamaproxy.model.ConversationSummary;
import com.home.ollamaproxy.model.OllamaMessage;
import com.home.ollamaproxy.service.OllamaService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OllamaController.class)
class OllamaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OllamaService ollamaService;

    @Test
    void chatReturnsServiceResponse() throws Exception {
        when(ollamaService.chat(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ChatResponse("llama3", new OllamaMessage("assistant", "Hi"), true, null, null, "Hi"));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Hi"))
                .andExpect(jsonPath("$.message.role").value("assistant"));
    }

    @Test
    void conversationEndpointsWork() throws Exception {
        String conversationId = UUID.randomUUID().toString();

        when(ollamaService.getConversationDetail(conversationId))
                .thenReturn(new ConversationDetail(conversationId, "Test chat", List.of(new OllamaMessage("user", "Hello"))));

        mockMvc.perform(get("/conversations/{conversationId}", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversationId))
                .andExpect(jsonPath("$.name").value("Test chat"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("Hello"));

        mockMvc.perform(delete("/conversations/{conversationId}", conversationId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(ollamaService).clearConversation(conversationId);
    }

    @Test
    void listConversationsReturnsSummaries() throws Exception {
        when(ollamaService.listConversations()).thenReturn(List.of(
                new ConversationSummary("id-1", "Chat 1", 5, Instant.now()),
                new ConversationSummary("id-2", "Chat 2", 3, Instant.now())
        ));

        mockMvc.perform(get("/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversations").isArray())
                .andExpect(jsonPath("$.conversations.length()").value(2))
                .andExpect(jsonPath("$.conversations[0].id").value("id-1"))
                .andExpect(jsonPath("$.conversations[0].name").value("Chat 1"))
                .andExpect(jsonPath("$.conversations[1].name").value("Chat 2"));
    }

    @Test
    void renameConversationReturnsNewName() throws Exception {
        String conversationId = UUID.randomUUID().toString();
        when(ollamaService.renameConversation(conversationId)).thenReturn("Renamed chat");

        mockMvc.perform(post("/conversations/{conversationId}/rename", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversationId))
                .andExpect(jsonPath("$.name").value("Renamed chat"));
    }
}