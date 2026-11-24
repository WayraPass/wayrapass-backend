package com.wayrapass.agent;

import com.wayrapass.agent.tools.WayriaTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class AgentConfig {
    private final Resource wayriaSystemPrompt;

    public AgentConfig(@org.springframework.beans.factory.annotation.Value("classpath:prompts/wayria-system-prompt.md") Resource wayriaSystemPrompt) {
        this.wayriaSystemPrompt = wayriaSystemPrompt;
    }

    @Bean
    ChatMemory wayriaChatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
    }

    @Bean
    ChatClient wayriaChatClient(ChatClient.Builder builder, WayriaTools tools, ChatMemory wayriaChatMemory) throws IOException {
        String systemPrompt = StreamUtils.copyToString(wayriaSystemPrompt.getInputStream(), StandardCharsets.UTF_8);
        return builder.defaultSystem(systemPrompt)
                .defaultTools(tools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(wayriaChatMemory).build())
                .build();
    }
}
