package com.wayrapass.agent;

import com.wayrapass.agent.dto.ChatRequest;
import com.wayrapass.agent.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AgentService service;

    @Test
    void chatWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/agent/chat").contentType("application/json").content("{\"message\":\"hola\"}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRIVER", "COORDINATOR"})
    void driverAndCoordinatorCannotUseWayriaInUs10(String role) throws Exception {
        mockMvc.perform(post("/api/agent/chat").with(user("user@test").roles(role))
                        .contentType("application/json").content("{\"message\":\"hola\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCanChatAndInputIsValidated() throws Exception {
        when(service.chat(new ChatRequest("hola"))).thenReturn(new ChatResponse("Hola", List.of(), null));
        mockMvc.perform(post("/api/agent/chat").with(user("student@test").roles("STUDENT"))
                        .contentType("application/json").content("{\"message\":\"hola\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Hola"));
        mockMvc.perform(post("/api/agent/chat").with(user("student@test").roles("STUDENT"))
                        .contentType("application/json").content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.message").exists());
    }

    @Test
    void familyCanChat() throws Exception {
        when(service.chat(new ChatRequest("estado"))).thenReturn(new ChatResponse("Sin viaje", List.of(), null));
        mockMvc.perform(post("/api/agent/chat").with(user("family@test").roles("FAMILY"))
                        .contentType("application/json").content("{\"message\":\"estado\"}"))
                .andExpect(status().isOk());
    }
}
