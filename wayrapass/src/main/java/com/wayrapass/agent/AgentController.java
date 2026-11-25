package com.wayrapass.agent;

import com.wayrapass.agent.dto.ChatRequest;
import com.wayrapass.agent.dto.ChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AgentController {
    private final AgentService agentService;
    private final com.wayrapass.service.WayriaProactiveMessageService proactiveMessages;

    @PostMapping("/api/agent/chat")
    @PreAuthorize("hasAnyRole('STUDENT', 'FAMILY', 'COORDINATOR')")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return agentService.chat(request);
    }

    @GetMapping("/api/agent/proactive-messages")
    @PreAuthorize("hasAnyRole('STUDENT', 'FAMILY')")
    public List<com.wayrapass.agent.dto.ProactiveMessageResponse> proactiveMessages() { return proactiveMessages.mine(); }
}
