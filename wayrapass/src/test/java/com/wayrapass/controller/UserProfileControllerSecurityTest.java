package com.wayrapass.controller;

import com.wayrapass.dto.response.UserResponse;
import com.wayrapass.model.Role;
import com.wayrapass.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean UserProfileService profiles;

    @Test
    void profileWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentProfileSerializesInstitution() throws Exception {
        when(profiles.me()).thenReturn(new UserResponse(5L, "Student Demo", "student@wayrapass.test",
                null, Role.STUDENT, true, null, null, 25L,
                new UserResponse.InstitutionSummary(25L, "UPC — Monterrico")));

        mockMvc.perform(get("/api/users/me").with(user("student@wayrapass.test").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.institutionId").value(25))
                .andExpect(jsonPath("$.institution.id").value(25))
                .andExpect(jsonPath("$.institution.displayName").value("UPC — Monterrico"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAMILY", "DRIVER", "COORDINATOR"})
    void nonStudentProfilesRemainValidWithNullInstitution(String roleName) throws Exception {
        Role role = Role.valueOf(roleName);
        when(profiles.me()).thenReturn(new UserResponse(9L, "Test User", "user@wayrapass.test",
                null, role, true, null, null, null, null));

        mockMvc.perform(get("/api/users/me").with(user("user@wayrapass.test").roles(roleName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(roleName))
                .andExpect(jsonPath("$.institutionId").doesNotExist())
                .andExpect(jsonPath("$.institution").doesNotExist());
    }
}
