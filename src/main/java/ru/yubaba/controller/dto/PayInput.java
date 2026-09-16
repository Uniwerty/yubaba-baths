package ru.yubaba.controller.dto;

import ru.yubaba.data.enums.PaymentMethod;

public record PayInput(
        Long version,
        PaymentMethod method
) {
}
