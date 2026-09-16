package ru.yubaba.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import ru.yubaba.controller.dto.IngredientInput;
import ru.yubaba.data.entity.Ingredient;
import ru.yubaba.data.entity.SupplyRequest;
import ru.yubaba.data.repository.IngredientRepository;
import ru.yubaba.data.repository.SupplyRequestRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import static ru.yubaba.service.ServiceChecks.*;

@Service
@Transactional(readOnly = true)
public class InventoryService {
    private final IngredientRepository ingredientRepository;
    private final SupplyRequestRepository supplyRequestRepository;

    public InventoryService(
            IngredientRepository ingredientRepository,
            SupplyRequestRepository supplyRequestRepository
    ) {
        this.ingredientRepository = ingredientRepository;
        this.supplyRequestRepository = supplyRequestRepository;
    }

    @Transactional
    public Ingredient saveIngredient(IngredientInput input) {
        var ingredient = new Ingredient();
        ingredient.name = input.name();
        ingredient.unit = input.unit();
        ingredient.stock = input.stock();
        ingredient.threshold = input.threshold();
        ingredient.price = input.price();
        ingredientRepository.saveAndFlush(ingredient);
        generateSupplies();
        return ingredient;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void generateSupplies() {
        var openRequests = supplyRequestRepository.findAll()
                .stream()
                .filter(s -> s.status.equals("OPEN"))
                .collect(Collectors.toMap(s -> s.ingredientId, s -> s));
        for (var ingredient : ingredientRepository.findAll()) {
            var available = ingredient.stock.subtract(ingredient.reserved);
            if (available.compareTo(ingredient.threshold) <= 0) {
                var maxQuantity = ingredient.threshold
                        .multiply(BigDecimal.valueOf(2))
                        .subtract(available)
                        .max(BigDecimal.ONE);
                var request = openRequests.get(ingredient.id);
                if (request == null) {
                    request = new SupplyRequest();
                    request.ingredientId = ingredient.id;
                    request.ingredientName = ingredient.name;
                    request.unit = ingredient.unit;
                    request.quantity = maxQuantity;
                    supplyRequestRepository.save(request);
                } else {
                    request.quantity = request.quantity.max(maxQuantity);
                }
            }
        }
    }

    @Transactional
    public List<SupplyRequest> getSupplyRequests() {
        generateSupplies();
        return supplyRequestRepository.findAll();
    }

    @Transactional
    public SupplyRequest receiveSupply(Long id) {
        var supplyRequest = supplyRequestRepository.findById(id).orElseThrow(ServiceChecks::createNotFoundException);
        check(supplyRequest.status.equals("OPEN"), "Поставка уже принята.");
        var ingredient = ingredientRepository.findById(supplyRequest.ingredientId).orElseThrow();
        ingredient.stock = ingredient.stock.add(supplyRequest.quantity);
        supplyRequest.status = "RECEIVED";
        supplyRequest.receivedAt = Instant.now();
        return supplyRequestRepository.saveAndFlush(supplyRequest);
    }

    public String supplyExport(Long id) {
        var supplyRequest = supplyRequestRepository.findById(id).orElseThrow(ServiceChecks::createNotFoundException);
        return "ЗАЯВКА НА ПОСТАВКУ №" + supplyRequest.id
                + "\nКупальни «Юбаба»\nДата: "
                + supplyRequest.createdAt.atZone(ZoneId.of("Europe/Moscow")).toLocalDate()
                + "\nПоставщик: ____________________\n\nИнгредиент: "
                + supplyRequest.ingredientName
                + "\nКоличество: "
                + supplyRequest.quantity.stripTrailingZeros().toPlainString() + " "
                + supplyRequest.unit
                + "\n\nОтветственный: ____________________\n";
    }
}
