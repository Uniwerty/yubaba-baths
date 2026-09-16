package ru.yubaba.controller.dto;

import ru.yubaba.data.enums.Role;

public record AccountInput(
        String login,
        String password,
        String name,
        Role role
) {
}
