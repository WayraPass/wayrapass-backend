package com.wayrapass.service;

import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.WayriaUnavailableException;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/** Performs only speech-to-text. It never invokes AgentService, tools or persistence. */
@Service
@Slf4j
public class WayriaVoiceService {
    private final ObjectProvider<TranscriptionModel> transcriptionModel;

    public WayriaVoiceService(@Qualifier("wayriaTranscriptionModel") ObjectProvider<TranscriptionModel> transcriptionModel) {
        this.transcriptionModel = transcriptionModel;
    }

    public String transcribe(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new BusinessRuleException("Debe adjuntar un archivo de audio válido.");
        }
        TranscriptionModel model = transcriptionModel.getIfAvailable();
        if (model == null) {
            log.warn("WayrIA transcription unavailable stage=model-resolution reason=missing-transcription-model");
            throw new WayriaUnavailableException();
        }
        try {
            byte[] bytes = audio.getBytes();
            if (bytes.length == 0) {
                throw new BusinessRuleException("Debe adjuntar un archivo de audio válido.");
            }
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override public String getFilename() {
                    return audio.getOriginalFilename() == null ? "recording.webm" : audio.getOriginalFilename();
                }
            };
            String transcript = model.call(new AudioTranscriptionPrompt(resource)).getResult().getOutput();
            if (transcript == null || transcript.isBlank()) {
                throw new BusinessRuleException("No se pudo obtener texto del audio enviado.");
            }
            return transcript.trim();
        } catch (BusinessRuleException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            log.warn("WayrIA transcription unavailable stage=provider-call exceptionType={} message={}",
                    exception.getClass().getSimpleName(), exception.getMessage());
            throw new WayriaUnavailableException(exception);
        }
    }
}
