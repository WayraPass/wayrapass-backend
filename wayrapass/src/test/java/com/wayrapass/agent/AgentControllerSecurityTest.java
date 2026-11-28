package com.wayrapass.agent;

import com.wayrapass.agent.dto.ChatRequest;
import com.wayrapass.agent.dto.ChatResponse;
import com.wayrapass.service.WayriaVoiceService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AgentService service;
    @MockitoBean WayriaVoiceService voiceService;

    @Test
    void chatWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/agent/chat").contentType("application/json").content("{\"message\":\"hola\"}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRIVER"})
    void driverCannotUseWayria(String role) throws Exception {
        mockMvc.perform(post("/api/agent/chat").with(user("user@test").roles(role))
                        .contentType("application/json").content("{\"message\":\"hola\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordinatorCanUseWayriaForUs25() throws Exception {
        when(service.chat(new ChatRequest("puntualidad"))).thenReturn(new ChatResponse("Resumen", List.of(), null));
        mockMvc.perform(post("/api/agent/chat").with(user("coordinator@test").roles("COORDINATOR"))
                        .contentType("application/json").content("{\"message\":\"puntualidad\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Resumen"));
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

    @Test
    void transcriptionRequiresAuthenticationAndRejectsDriver() throws Exception {
        var audio = new org.springframework.mock.web.MockMultipartFile("audio", "question.webm", "audio/webm", "audio".getBytes());
        mockMvc.perform(multipart("/api/agent/transcribe").file(audio))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/agent/transcribe").file(audio).with(user("driver@test").roles("DRIVER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorizedUserReceivesOnlyTranscriptAndNeverChatsAutomatically() throws Exception {
        when(voiceService.transcribe(org.mockito.ArgumentMatchers.any())).thenReturn("¿Cuáles son mis rutas disponibles?");
        var audio = new org.springframework.mock.web.MockMultipartFile("audio", "question.webm", "audio/webm", "audio".getBytes());
        mockMvc.perform(multipart("/api/agent/transcribe").file(audio).with(user("student@test").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript").value("¿Cuáles son mis rutas disponibles?"));
        org.mockito.Mockito.verifyNoInteractions(service);
    }
}
