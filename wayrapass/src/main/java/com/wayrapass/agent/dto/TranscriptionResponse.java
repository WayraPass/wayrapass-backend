package com.wayrapass.agent.dto;

/** Text obtained from an audio clip. It is deliberately not submitted to the agent. */
public record TranscriptionResponse(String transcript) {
}
