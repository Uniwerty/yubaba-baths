package ru.yubaba.controller.dto;

import java.util.List;
import ru.yubaba.data.entity.Ingredient;
import ru.yubaba.data.entity.Room;
import ru.yubaba.data.entity.ServiceTemplate;

public record CatalogResponse(
        List<ServiceTemplate> templates,
        List<Room> rooms,
        List<Ingredient> ingredients
) {
}
