package com.wayrapass.service;

import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.WayriaUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WayriaVoiceServiceTest {
    @Mock ObjectProvider<TranscriptionModel> modelProvider;
    @Mock TranscriptionModel model;

    @Test
    void validAudioReturnsOnlyTrimmedTranscript() {
        when(modelProvider.getIfAvailable()).thenReturn(model);
        when(model.call(any(AudioTranscriptionPrompt.class)))
                .thenReturn(new AudioTranscriptionResponse(new AudioTranscription("  Consulta por voz  ")));

        String transcript = new WayriaVoiceService(modelProvider)
                .transcribe(new MockMultipartFile("audio", "voice.webm", "audio/webm", "bytes".getBytes()));

        assertEquals("Consulta por voz", transcript);
        verify(model).call(any(AudioTranscriptionPrompt.class));
    }

    @Test
    void emptyAudioAndEmptyTranscriptAreControlledClientErrors() {
        WayriaVoiceService service = new WayriaVoiceService(modelProvider);
        assertThrows(BusinessRuleException.class, () -> service.transcribe(new MockMultipartFile("audio", new byte[0])));

        when(modelProvider.getIfAvailable()).thenReturn(model);
        when(model.call(any(AudioTranscriptionPrompt.class)))
                .thenReturn(new AudioTranscriptionResponse(new AudioTranscription("   ")));
        assertThrows(BusinessRuleException.class,
                () -> service.transcribe(new MockMultipartFile("audio", "voice.webm", "audio/webm", "bytes".getBytes())));
    }

    @Test
    void unavailableOrFailedProviderReturnsControlledWayriaError() {
        WayriaVoiceService service = new WayriaVoiceService(modelProvider);
        when(modelProvider.getIfAvailable()).thenReturn(null);
        assertThrows(WayriaUnavailableException.class,
                () -> service.transcribe(new MockMultipartFile("audio", "voice.webm", "audio/webm", "bytes".getBytes())));

        when(modelProvider.getIfAvailable()).thenReturn(model);
        when(model.call(any(AudioTranscriptionPrompt.class))).thenThrow(new IllegalStateException("provider down"));
        assertThrows(WayriaUnavailableException.class,
                () -> service.transcribe(new MockMultipartFile("audio", "voice.webm", "audio/webm", "bytes".getBytes())));
    }
}
