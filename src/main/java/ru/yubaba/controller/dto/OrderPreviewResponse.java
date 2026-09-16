package ru.yubaba.controller.dto;

import java.math.BigDecimal;
import java.util.List;
import ru.yubaba.data.entity.BathOrder;

public record OrderPreviewResponse(
        BigDecimal total,
        List<String> issues,
        BathOrder order
) {
}
