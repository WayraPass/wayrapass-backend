package com.wayrapass.agent;

import com.wayrapass.agent.dto.ChatRequest;
import com.wayrapass.agent.dto.ChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AgentController {
    private final AgentService agentService;

    @PostMapping("/api/agent/chat")
    @PreAuthorize("hasAnyRole('STUDENT', 'FAMILY')")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return agentService.chat(request);
    }
}
