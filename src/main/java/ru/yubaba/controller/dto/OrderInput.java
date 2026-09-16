package ru.yubaba.controller.dto;

public record OrderInput(
        Long clientId,
        Long templateId,
        int visitors,
        int priority,
        Composition composition,
        Long version
) {
}
