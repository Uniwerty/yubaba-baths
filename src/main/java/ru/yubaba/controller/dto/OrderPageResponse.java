package ru.yubaba.controller.dto;

import java.util.List;
import ru.yubaba.data.entity.BathOrder;

public record OrderPageResponse(
        List<BathOrder> items,
        long total,
        int pages
) {
}
