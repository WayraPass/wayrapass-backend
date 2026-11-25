package com.wayrapass.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class WayriaDelayAlertComposer {
    private final ChatClient chatClient;

    public WayriaDelayAlertComposer(@Qualifier("studentFamilyChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }
    public String compose(String routeCode, int delayMinutes, List<String> descriptions) {
        String fallback = "La ruta " + routeCode + " presenta un retraso aproximado de " + delayMinutes + " minutos debido a una incidencia reportada. La hora estimada de llegada fue actualizada.";
        try {
            String facts = "Ruta: " + routeCode + "\nRetraso confirmado: " + delayMinutes + " minutos\nIncidencias: " + String.join("; ", descriptions);
            String content = chatClient.prompt().system("""
                    Eres WayrIA. Redacta una alerta breve en español usando exclusivamente los hechos proporcionados.
                    No inventes ni cambies ruta, minutos, causas u horarios. Máximo tres oraciones, texto plano, sin Markdown ni emojis.
                    """).user(facts).call().content();
            if (content == null || content.isBlank() || content.length() > 600 || !content.contains(routeCode) || !content.contains(String.valueOf(delayMinutes))) return fallback;
            return content.replace("**", "").replace("#", "").trim();
        } catch (RuntimeException ignored) { return fallback; }
    }
}
