package ru.yubaba.controller.dto;

public record RoomInput(
        String name,
        String bathType,
        int capacity
) {
}
