package ru.yubaba.controller.dto;

import java.math.BigDecimal;

public record IngredientInput(
        String name,
        String unit,
        BigDecimal stock,
        BigDecimal threshold,
        BigDecimal price
) {
}
