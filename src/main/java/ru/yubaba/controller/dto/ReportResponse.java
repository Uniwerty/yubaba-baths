package ru.yubaba.controller.dto;

import java.math.BigDecimal;
import java.util.List;
import ru.yubaba.data.entity.BathOrder;
import ru.yubaba.data.entity.Payment;

public record ReportResponse(
        List<BathOrder> orders,
        List<Payment> payments,
        int orderCount,
        int visitors,
        BigDecimal revenue,
        boolean empty
) {
}
