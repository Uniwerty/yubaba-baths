package ru.yubaba.controller.dto;

import java.math.BigDecimal;
import java.util.List;

public record Composition(
        String name,
        String bathType,
        Long preferredRoomId,
        int attendants,
        int durationMinutes,
        int preparationMinutes,
        int breakMinutes,
        int temperature,
        String steps,
        String extraServices,
        BigDecimal basePrice,
        List<LineInput> lines
) {
}
