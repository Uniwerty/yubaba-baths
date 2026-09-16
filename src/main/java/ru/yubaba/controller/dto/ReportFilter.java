package ru.yubaba.controller.dto;

import ru.yubaba.data.enums.PaymentMethod;
import java.time.LocalDate;

public record ReportFilter(
        LocalDate from,
        LocalDate to,
        Long templateId,
        PaymentMethod method,
        Integer visitors
) {
}
