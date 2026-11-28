package com.wayrapass.agent;

import com.wayrapass.agent.dto.ChatRequest;
import com.wayrapass.agent.dto.ChatResponse;
import com.wayrapass.agent.dto.TranscriptionResponse;
import com.wayrapass.service.WayriaVoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AgentController {
    private final AgentService agentService;
    private final com.wayrapass.service.WayriaProactiveMessageService proactiveMessages;
    private final WayriaVoiceService voiceService;

    @PostMapping("/api/agent/chat")
    @PreAuthorize("hasAnyRole('STUDENT', 'FAMILY', 'COORDINATOR')")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return agentService.chat(request);
    }

    @PostMapping(value = "/api/agent/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STUDENT', 'FAMILY', 'COORDINATOR')")
    public TranscriptionResponse transcribe(@RequestParam("audio") MultipartFile audio) {
        return new TranscriptionResponse(voiceService.transcribe(audio));
    }

    @GetMapping("/api/agent/proactive-messages")
    @PreAuthorize("hasAnyRole('STUDENT', 'FAMILY')")
    public List<com.wayrapass.agent.dto.ProactiveMessageResponse> proactiveMessages() { return proactiveMessages.mine(); }
}
