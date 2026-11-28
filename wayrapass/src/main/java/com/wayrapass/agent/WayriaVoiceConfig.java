package com.wayrapass.agent;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Creates a transcription-only client when a Whisper/OpenAI-compatible provider is
 * explicitly configured. It must not share the Ollama/Qwen chat connection.
 */
@Configuration
@Slf4j
class WayriaVoiceConfig {

    @Bean("wayriaTranscriptionModel")
    @ConditionalOnProperty(prefix = "wayria.voice.transcription", name = "enabled", havingValue = "true")
    TranscriptionModel wayriaTranscriptionModel(
            @Value("${wayria.voice.transcription.base-url}") String baseUrl,
            @Value("${wayria.voice.transcription.api-key}") String apiKey,
            @Value("${wayria.voice.transcription.model}") String model,
            @Value("${wayria.voice.transcription.language:es}") String language) {
        log.info("WayrIA transcription configured baseUrl={} model={} language={}", baseUrl, model, language);
        OpenAIClient client = OpenAiSetup.setupSyncClient(baseUrl, apiKey, null, null, null, null,
                false, false, model, Duration.ofSeconds(60), 0, null, Map.of(),
                ObservationRegistry.NOOP, null, List.of());
        OpenAIClientAsync asyncClient = OpenAiSetup.setupAsyncClient(baseUrl, apiKey, null, null, null, null,
                false, false, model, Duration.ofSeconds(60), 0, null, Map.of(),
                ObservationRegistry.NOOP, null, List.of());

        return OpenAiAudioTranscriptionModel.builder()
                .openAiClient(client)
                .openAiClientAsync(asyncClient)
                .options(OpenAiAudioTranscriptionOptions.builder().model(model).language(language).build())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }
}
