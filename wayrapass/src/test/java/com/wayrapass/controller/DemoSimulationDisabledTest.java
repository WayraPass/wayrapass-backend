package com.wayrapass.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoSimulationDisabledTest {
    @Autowired MockMvc mockMvc;

    @Test
    void demoEndpointDoesNotExistWhenSimulatorIsDisabled() throws Exception {
        mockMvc.perform(get("/api/demo/trips/30/simulation")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR")))
                .andExpect(status().isNotFound());
    }
}
