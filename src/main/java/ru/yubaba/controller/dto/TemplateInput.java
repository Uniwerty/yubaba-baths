package ru.yubaba.controller.dto;

public record TemplateInput(
        Composition composition,
        Long version
) {
}
