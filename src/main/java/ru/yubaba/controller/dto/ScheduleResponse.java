package ru.yubaba.controller.dto;

import java.util.List;
import ru.yubaba.data.entity.BathOrder;

public record ScheduleResponse(
        List<BathOrder> orders,
        List<BathOrder> active,
        String restUntil
) {
}
