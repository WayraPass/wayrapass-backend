package com.wayrapass.agent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import static org.junit.jupiter.api.Assertions.assertFalse;

class WayriaReassignmentToolsSecurityTest {

    @Test
    void sensitiveReassignmentWritesAreNotExposedToTheLlmToolCatalog() throws Exception {
        assertFalse(WayriaReassignmentTools.class
                .getMethod("proposeReassignment", Long.class, String.class, Long.class)
                .isAnnotationPresent(Tool.class));
        assertFalse(WayriaReassignmentTools.class
                .getMethod("confirmReassignment", Long.class)
                .isAnnotationPresent(Tool.class));
    }
}
