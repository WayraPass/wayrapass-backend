package com.wayrapass.service;

import com.wayrapass.dto.response.ReportResponses;
import com.wayrapass.model.ReportFormat;
import com.wayrapass.model.ReportIndicator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportExportServiceTest {
    @Mock ReportService reportService;
    private ReportExportService service;
    private ReportResponses.OperationalReport report;
    private LocalDate from;
    private LocalDate to;

    @BeforeEach
    void setUp() {
        service = new ReportExportService(reportService);
        from = LocalDate.of(2026, 9, 1);
        to = LocalDate.of(2026, 9, 30);
        report = new ReportResponses.OperationalReport(
                new ReportResponses.Period(from, to, "America/Lima"),
                ReportIndicator.PUNCTUALITY,
                Instant.parse("2026-10-01T12:00:00Z"),
                true,
                null,
                new ReportResponses.Punctuality(4, 3, 1, new BigDecimal("75.00")),
                null,
                null,
                null
        );
        when(reportService.generate(from, to, ReportIndicator.PUNCTUALITY)).thenReturn(report);
    }

    @Test
    void xlsxIsValidAndContainsAppliedFilterAndMetric() throws Exception {
        var exported = service.export(from, to, ReportIndicator.PUNCTUALITY, ReportFormat.XLSX);

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", exported.contentType());
        assertTrue(exported.filename().endsWith("-punctuality.xlsx"));
        assertTrue(exported.content().length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported.content()))) {
            var sheet = workbook.getSheet("Reporte operativo");
            assertEquals("Reporte operativo WayraPass", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("PUNCTUALITY", sheet.getRow(3).getCell(1).getStringCellValue());
            assertTrue(findValue(sheet, "Puntualidad (%)").contains("75"));
        }
        verify(reportService).generate(from, to, ReportIndicator.PUNCTUALITY);
    }

    @Test
    void pdfIsParseableAndContainsAppliedFilterAndMetric() throws Exception {
        var exported = service.export(from, to, ReportIndicator.PUNCTUALITY, ReportFormat.PDF);

        assertEquals("application/pdf", exported.contentType());
        assertTrue(exported.filename().endsWith("-punctuality.pdf"));
        assertTrue(exported.content().length > 0);
        try (PDDocument document = Loader.loadPDF(exported.content())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Reporte operativo WayraPass"));
            assertTrue(text.contains("Indicador: PUNCTUALITY"));
            assertTrue(text.contains("Puntualidad: 75.00%"));
            assertFalse(text.contains("Ocupación"));
        }
        verify(reportService).generate(from, to, ReportIndicator.PUNCTUALITY);
    }

    private String findValue(org.apache.poi.ss.usermodel.Sheet sheet, String label) {
        for (org.apache.poi.ss.usermodel.Row row : sheet) {
            if (row.getCell(0) != null && label.equals(row.getCell(0).getStringCellValue())) {
                return row.getCell(1).toString();
            }
        }
        fail("No se encontró la fila " + label);
        return "";
    }
}
