package ru.yubaba.controller.dto;

public record CancelInput(
        Long version,
        String reason
) {
}
