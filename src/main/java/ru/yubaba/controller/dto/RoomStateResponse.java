package ru.yubaba.controller.dto;

import java.util.List;
import ru.yubaba.data.entity.Room;

public record RoomStateResponse(
        Room room,
        List<Long> orders
) {
}
