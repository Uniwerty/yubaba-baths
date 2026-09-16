package ru.yubaba.controller.dto;

public record ClientInput(
        String name,
        String contact,
        String notes,
        Long version
) {
}
