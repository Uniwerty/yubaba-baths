package ru.yubaba.controller.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import ru.yubaba.data.entity.BathOrder;

public record DashboardResponse(
        List<RoomStateResponse> rooms,
        List<AttendantStateResponse> attendants,
        List<BathOrder> queue,
        int orderCount,
        double averageMinutes,
        BigDecimal revenue,
        boolean empty,
        Instant updatedAt
) {
}
