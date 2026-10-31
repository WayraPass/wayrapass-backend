package com.wayrapass.model;

public enum ReportFormat {
    PDF("pdf"),
    XLSX("xlsx");

    private final String extension;

    ReportFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }
}
