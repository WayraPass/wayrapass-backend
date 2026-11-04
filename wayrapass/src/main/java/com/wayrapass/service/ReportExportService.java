package com.wayrapass.service;

import com.wayrapass.dto.response.ReportResponses;
import com.wayrapass.exception.ReportGenerationException;
import com.wayrapass.model.ReportFormat;
import com.wayrapass.model.ReportIndicator;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ReportExportService {
    private static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReportService reportService;

    public ExportedReport export(LocalDate from, LocalDate to, ReportIndicator indicator, ReportFormat format) {
        ReportResponses.OperationalReport report = reportService.generate(from, to, indicator);
        try {
            byte[] content = switch (format) {
                case PDF -> pdf(report);
                case XLSX -> xlsx(report);
            };
            String filename = "wayrapass-reporte-operativo-%s_%s-%s.%s".formatted(
                    from,
                    to,
                    report.indicator().name().toLowerCase(),
                    format.extension()
            );
            String contentType = format == ReportFormat.PDF ? MediaType.APPLICATION_PDF_VALUE : XLSX_MEDIA_TYPE;
            return new ExportedReport(content, contentType, filename);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ReportGenerationException reportGenerationException) {
                throw reportGenerationException;
            }
            throw new ReportGenerationException(exception);
        }
    }

    private byte[] xlsx(ReportResponses.OperationalReport report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Reporte operativo");
            CellStyle titleStyle = titleStyle(workbook);
            CellStyle sectionStyle = sectionStyle(workbook);
            int rowIndex = 0;

            Row title = sheet.createRow(rowIndex++);
            title.createCell(0).setCellValue("Reporte operativo WayraPass");
            title.getCell(0).setCellStyle(titleStyle);
            rowIndex = pair(sheet, rowIndex, "Periodo", report.period().from() + " al " + report.period().to());
            rowIndex = pair(sheet, rowIndex, "Zona horaria", report.period().zoneId());
            rowIndex = pair(sheet, rowIndex, "Indicador", report.indicator().name());
            rowIndex = pair(sheet, rowIndex, "Generado", report.generatedAt().toString());
            if (!report.hasData()) {
                rowIndex++;
                rowIndex = pair(sheet, rowIndex, "Resultado", report.message());
            }

            if (report.punctuality() != null) {
                rowIndex = section(sheet, rowIndex + 1, "Puntualidad", sectionStyle);
                rowIndex = pair(sheet, rowIndex, "Viajes evaluados", report.punctuality().evaluatedTrips());
                rowIndex = pair(sheet, rowIndex, "Viajes puntuales", report.punctuality().onTimeTrips());
                rowIndex = pair(sheet, rowIndex, "Viajes con retraso", report.punctuality().lateTrips());
                rowIndex = pair(sheet, rowIndex, "Puntualidad (%)", report.punctuality().onTimePercentage());
            }
            if (report.occupancy() != null) {
                rowIndex = section(sheet, rowIndex + 1, "Ocupación", sectionStyle);
                rowIndex = pair(sheet, rowIndex, "Viajes evaluados", report.occupancy().evaluatedTrips());
                rowIndex = pair(sheet, rowIndex, "Abordajes confirmados", report.occupancy().confirmedBoardings());
                rowIndex = pair(sheet, rowIndex, "Capacidad ofrecida", report.occupancy().offeredCapacity());
                rowIndex = pair(sheet, rowIndex, "Ocupación (%)", report.occupancy().occupancyPercentage());
            }
            if (report.incidents() != null) {
                rowIndex = section(sheet, rowIndex + 1, "Incidencias", sectionStyle);
                rowIndex = pair(sheet, rowIndex, "Incidencias registradas", report.incidents().totalIncidents());
            }
            if (report.stopDemand() != null) {
                rowIndex = section(sheet, rowIndex + 1, "Demanda por paradero", sectionStyle);
                Row header = sheet.createRow(rowIndex++);
                header.createCell(0).setCellValue("Paradero");
                header.createCell(1).setCellValue("Abordajes confirmados");
                header.getCell(0).setCellStyle(sectionStyle);
                header.getCell(1).setCellStyle(sectionStyle);
                for (ReportResponses.StopDemand demand : report.stopDemand()) {
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(demand.stopName());
                    row.createCell(1).setCellValue(demand.confirmedBoardings());
                }
            }

            sheet.setColumnWidth(0, 34 * 256);
            sheet.setColumnWidth(1, 28 * 256);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] pdf(ReportResponses.OperationalReport report) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfLines writer = new PdfLines(document);
            writer.line("Reporte operativo WayraPass", true);
            writer.line("Periodo: " + report.period().from() + " al " + report.period().to(), false);
            writer.line("Zona horaria: " + report.period().zoneId(), false);
            writer.line("Indicador: " + report.indicator(), false);
            writer.line("Generado: " + report.generatedAt(), false);
            if (!report.hasData()) {
                writer.line("Resultado: " + report.message(), false);
            }
            if (report.punctuality() != null) {
                writer.section("Puntualidad");
                writer.line("Viajes evaluados: " + report.punctuality().evaluatedTrips(), false);
                writer.line("Viajes puntuales: " + report.punctuality().onTimeTrips(), false);
                writer.line("Viajes con retraso: " + report.punctuality().lateTrips(), false);
                writer.line("Puntualidad: " + percent(report.punctuality().onTimePercentage()), false);
            }
            if (report.occupancy() != null) {
                writer.section("Ocupación");
                writer.line("Viajes evaluados: " + report.occupancy().evaluatedTrips(), false);
                writer.line("Abordajes confirmados: " + report.occupancy().confirmedBoardings(), false);
                writer.line("Capacidad ofrecida: " + report.occupancy().offeredCapacity(), false);
                writer.line("Ocupación: " + percent(report.occupancy().occupancyPercentage()), false);
            }
            if (report.incidents() != null) {
                writer.section("Incidencias");
                writer.line("Incidencias registradas: " + report.incidents().totalIncidents(), false);
            }
            if (report.stopDemand() != null) {
                writer.section("Demanda por paradero");
                for (ReportResponses.StopDemand demand : report.stopDemand()) {
                    writer.line(demand.stopName() + ": " + demand.confirmedBoardings() + " abordajes confirmados", false);
                }
            }
            writer.closePage();
            document.save(output);
            return output.toByteArray();
        }
    }

    private String percent(BigDecimal value) {
        return value.toPlainString() + "%";
    }

    private int pair(Sheet sheet, int rowIndex, String label, Object value) {
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(label);
        if (value instanceof Number number) {
            row.createCell(1).setCellValue(number.doubleValue());
        } else {
            row.createCell(1).setCellValue(String.valueOf(value));
        }
        return rowIndex;
    }

    private int section(Sheet sheet, int rowIndex, String title, CellStyle style) {
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(title);
        row.getCell(0).setCellStyle(style);
        return rowIndex;
    }

    private CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        return style;
    }

    private CellStyle sectionStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    public record ExportedReport(byte[] content, String contentType, String filename) {
    }

    private static final class PdfLines {
        private static final float MARGIN = 54;
        private static final float LINE_HEIGHT = 17;
        private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        private final PDDocument document;
        private PDPageContentStream content;
        private float y;

        private PdfLines(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void section(String text) throws IOException {
            y -= 8;
            line(text, true);
        }

        private void line(String text, boolean bold) throws IOException {
            for (String wrappedLine : wrap(text, 92)) {
                writeLine(wrappedLine, bold);
            }
        }

        private void writeLine(String text, boolean bold) throws IOException {
            if (y < MARGIN + LINE_HEIGHT) {
                closePage();
                newPage();
            }
            content.beginText();
            content.setFont(bold ? BOLD : REGULAR, bold ? 13 : 10);
            content.newLineAtOffset(MARGIN, y);
            content.showText(text);
            content.endText();
            y -= LINE_HEIGHT;
        }

        private java.util.List<String> wrap(String text, int maximumCharacters) {
            if (text.length() <= maximumCharacters) {
                return java.util.List.of(text);
            }
            java.util.List<String> lines = new java.util.ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : text.split(" ")) {
                if (!current.isEmpty() && current.length() + word.length() + 1 > maximumCharacters) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                if (!current.isEmpty()) {
                    current.append(' ');
                }
                current.append(word);
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines;
        }

        private void newPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void closePage() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
        }
    }
}
