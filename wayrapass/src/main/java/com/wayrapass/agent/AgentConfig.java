package com.wayrapass.agent;

import com.wayrapass.agent.tools.WayriaTools;
import com.wayrapass.agent.tools.WayriaCoordinatorTools;
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

    @Bean("studentFamilyChatClient")
    ChatClient studentFamilyChatClient(ChatClient.Builder builder, WayriaTools tools, ChatMemory wayriaChatMemory) throws IOException {
        String systemPrompt = StreamUtils.copyToString(wayriaSystemPrompt.getInputStream(), StandardCharsets.UTF_8);
        return builder.defaultSystem(systemPrompt)
                .defaultTools(tools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(wayriaChatMemory).build())
                .build();
    }

    @Bean("coordinatorChatClient")
    ChatClient coordinatorChatClient(ChatClient.Builder builder, WayriaCoordinatorTools tools, ChatMemory wayriaChatMemory) {
        String coordinatorPrompt = """
                Eres WayrIA para COORDINATOR de WayraPass. Responde siempre en español.
                Puedes explicar que generas reportes operativos por periodo y ruta, con puntualidad, ocupación,
                incidencias y demanda por paradero. Si solo preguntan por tus capacidades, responde sin usar Tools.
                Para datos reales usa solo las Tools registradas de coordinador; nunca inventes cifras ni rutas.
                Si faltan parámetros, pide el dato faltante. Rechaza temas ajenos a WayraPass amablemente.
                Las respuestas visibles son texto plano: no uses Markdown, #, **, separadores --- ni emojis decorativos.
                """;
        return builder.defaultSystem(coordinatorPrompt)
                .defaultTools(tools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(wayriaChatMemory).build())
                .build();
    }
}
