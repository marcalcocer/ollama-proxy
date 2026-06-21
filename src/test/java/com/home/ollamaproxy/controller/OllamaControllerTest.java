package com.home.ollamaproxy.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.home.ollamaproxy.model.ChatResponse;
import com.home.ollamaproxy.model.OllamaMessage;
import com.home.ollamaproxy.service.OllamaService;
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
                .thenReturn(new ChatResponse("llama3", new OllamaMessage("assistant", "Hi"), true, null, "Hi"));

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
        when(ollamaService.getConversation(conversationId)).thenReturn(List.of(new OllamaMessage("user", "Hello")));

        mockMvc.perform(get("/conversations/{conversationId}", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].content").value("Hello"));

        mockMvc.perform(delete("/conversations/{conversationId}", conversationId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(ollamaService).clearConversation(conversationId);
    }
}
