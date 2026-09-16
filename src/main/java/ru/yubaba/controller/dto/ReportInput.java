package ru.yubaba.controller.dto;

public record ReportInput(
        String name,
        ReportFilter parameters
) {
}
