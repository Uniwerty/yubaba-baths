package ru.yubaba.controller.dto;

import java.math.BigDecimal;

public record LineInput(
        Long ingredientId,
        BigDecimal quantity
) {
}
