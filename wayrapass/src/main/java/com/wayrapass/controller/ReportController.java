package com.wayrapass.controller;

import com.wayrapass.dto.response.ReportResponses;
import com.wayrapass.model.ReportFormat;
import com.wayrapass.model.ReportIndicator;
import com.wayrapass.service.ReportExportService;
import com.wayrapass.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports/operations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COORDINATOR')")
public class ReportController {
    private final ReportService reports;
    private final ReportExportService exports;

    @GetMapping
    public ReportResponses.OperationalReport operations(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ALL") ReportIndicator indicator
    ) {
        return reports.generate(from, to, indicator);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ALL") ReportIndicator indicator,
            @RequestParam ReportFormat format
    ) {
        ReportExportService.ExportedReport file = exports.export(from, to, indicator, format);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CONTENT_TYPE, file.contentType())
                .body(file.content());
    }
}
