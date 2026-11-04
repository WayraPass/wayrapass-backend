package com.wayrapass.controller;

import com.wayrapass.dto.request.OperationalRequests;
import com.wayrapass.dto.response.OperationalResponses;
import com.wayrapass.service.EnrollmentStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/enrollments/me")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class EnrollmentStatusController {
    private final EnrollmentStatusService service;

    @GetMapping
    public OperationalResponses.EnrollmentStatusDetails current() {
        return service.current();
    }

    @PutMapping("/suspension")
    public OperationalResponses.EnrollmentStatusDetails suspend(
            @Valid @RequestBody OperationalRequests.Suspension request
    ) {
        return service.suspend(request);
    }

    @PutMapping("/reactivation")
    public OperationalResponses.EnrollmentStatusDetails reactivate() {
        return service.reactivate();
    }
}
