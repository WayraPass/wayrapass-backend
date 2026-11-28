package com.wayrapass.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class WayriaVoiceConfigTest {

    @Test
    void createsAnIndependentOpenAiCompatibleTranscriptionModelWithoutCallingTheProvider() {
        assertNotNull(new WayriaVoiceConfig().wayriaTranscriptionModel(
                "https://voice.example.test/v1", "test-key", "whisper-1", "es"));
    }
}
