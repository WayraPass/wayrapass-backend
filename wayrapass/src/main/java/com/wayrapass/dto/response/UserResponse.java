package com.wayrapass.dto.response;

import com.wayrapass.model.Role;

import java.time.Instant;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        Role role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        Long institutionId,
        InstitutionSummary institution
) {
    public record InstitutionSummary(Long id, String displayName) {
    }
}
