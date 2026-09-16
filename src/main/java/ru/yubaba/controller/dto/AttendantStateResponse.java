package ru.yubaba.controller.dto;

import java.util.List;
import ru.yubaba.data.entity.Account;

public record AttendantStateResponse(
        Account account,
        List<Long> orders
) {
}
