package ru.yubaba.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yubaba.controller.dto.CatalogResponse;
import ru.yubaba.controller.dto.Composition;
import ru.yubaba.controller.dto.RoomInput;
import ru.yubaba.controller.dto.TemplateInput;
import ru.yubaba.data.entity.RecipeLine;
import ru.yubaba.data.entity.Room;
import ru.yubaba.data.entity.ServiceTemplate;
import ru.yubaba.data.repository.IngredientRepository;
import ru.yubaba.data.repository.RoomRepository;
import ru.yubaba.data.repository.ServiceTemplateRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ru.yubaba.service.ServiceChecks.*;

@Service
@Transactional(readOnly = true)
public class CatalogService {
    private final RoomRepository roomRepository;
    private final IngredientRepository ingredientRepository;
    private final ServiceTemplateRepository serviceTemplateRepository;

    public CatalogService(
            RoomRepository roomRepository,
            IngredientRepository ingredientRepository,
            ServiceTemplateRepository serviceTemplateRepository
    ) {
        this.roomRepository = roomRepository;
        this.ingredientRepository = ingredientRepository;
        this.serviceTemplateRepository = serviceTemplateRepository;
    }

    public CatalogResponse getCatalog() {
        return new CatalogResponse(
                serviceTemplateRepository.findAll(),
                roomRepository.findAll(),
                ingredientRepository.findAll()
        );
    }

    public List<RecipeLine> filterRecipeLines(Composition composition) {
        Set<Long> seen = new HashSet<>();
        List<RecipeLine> result = new ArrayList<>();
        for (var line : composition.lines()) {
            check(seen.add(line.ingredientId()), "Ингредиент указан дважды.");
            result.add(
                    new RecipeLine(
                            ingredientRepository.findById(line.ingredientId()).orElseThrow(ServiceChecks::createNotFoundException),
                            line.quantity()
                    )
            );
        }
        check(
                roomRepository.findAll().stream().anyMatch(r -> r.bathType.equals(composition.bathType())),
                "Нет комнат выбранного типа купальни."
        );
        if (composition.preferredRoomId() != null) {
            var r = roomRepository.findById(composition.preferredRoomId()).orElseThrow(ServiceChecks::createNotFoundException);
            check(r.bathType.equals(composition.bathType()), "Комната не соответствует типу купальни.");
        }
        return result;
    }

    @Transactional
    public ServiceTemplate saveTemplate(Long id, TemplateInput input) {
        var template = id == null ? new ServiceTemplate() : serviceTemplateRepository.findById(id).orElseThrow(ServiceChecks::createNotFoundException);
        if (id != null) {
            compareVersion(template.version, input.version());
        }
        var composition = input.composition();
        var recipeLines = filterRecipeLines(composition);
        template.name = composition.name().trim();
        template.bathType = composition.bathType();
        template.preferredRoomId = composition.preferredRoomId();
        template.attendants = composition.attendants();
        template.durationMinutes = composition.durationMinutes();
        template.preparationMinutes = composition.preparationMinutes();
        template.breakMinutes = composition.breakMinutes();
        template.temperature = composition.temperature();
        template.steps = composition.steps();
        template.extraServices = composition.extraServices();
        template.basePrice = composition.basePrice();
        template.lines.clear();
        template.lines.addAll(recipeLines);
        return serviceTemplateRepository.saveAndFlush(template);
    }

    @Transactional
    public Room saveRoom(RoomInput input) {
        var room = new Room();
        room.name = input.name();
        room.bathType = input.bathType();
        room.capacity = input.capacity();
        return roomRepository.saveAndFlush(room);
    }
}
