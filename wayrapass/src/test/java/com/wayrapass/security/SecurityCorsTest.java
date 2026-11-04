package com.wayrapass.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityCorsTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void registerPreflightAllowsAngularLocalOriginAndRequiredHeaders() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Authorization, Content-Type, Accept"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("Authorization")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("Content-Type")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("Accept")));
    }
}
