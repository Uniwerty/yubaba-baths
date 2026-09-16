package ru.yubaba.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yubaba.data.entity.Account;
import ru.yubaba.data.entity.BathOrder;
import ru.yubaba.data.entity.Room;
import ru.yubaba.data.enums.OrderStatus;
import ru.yubaba.data.enums.Role;
import ru.yubaba.data.repository.AccountRepository;
import ru.yubaba.data.repository.BathOrderRepository;
import ru.yubaba.data.repository.IngredientRepository;
import ru.yubaba.data.repository.RoomRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ResourceAvailabilityService {
    private final AccountRepository accountRepository;
    private final RoomRepository roomRepository;
    private final IngredientRepository ingredientRepository;
    private final BathOrderRepository bathOrderRepository;

    public ResourceAvailabilityService(
            AccountRepository accountRepository,
            RoomRepository roomRepository,
            IngredientRepository ingredientRepository,
            BathOrderRepository bathOrderRepository
    ) {
        this.accountRepository = accountRepository;
        this.roomRepository = roomRepository;
        this.ingredientRepository = ingredientRepository;
        this.bathOrderRepository = bathOrderRepository;
    }

    public List<BathOrder> getActiveOrders() {
        return bathOrderRepository.findByStatusIn(List.of(OrderStatus.IN_SERVICE, OrderStatus.AWAITING_PAYMENT));
    }

    public List<Room> getFreeRooms(BathOrder o) {
        Set<Long> busy = getActiveOrders().stream().map(a -> a.roomId).collect(Collectors.toSet());
        return roomRepository.findAll().stream()
                .filter(r -> r.bathType.equals(o.bathType) && r.capacity >= o.visitors && !busy.contains(r.id))
                .filter(r -> o.preferredRoomId == null || o.preferredRoomId.equals(r.id))
                .sorted(Comparator.comparing(r -> r.id))
                .toList();
    }

    public List<Account> getFreeAttendants() {
        return accountRepository.findAll().stream()
                .filter(a -> a.role == Role.ATTENDANT && !a.blocked && a.activeOrderId == null)
                .filter(a -> a.restUntil == null || !a.restUntil.isAfter(Instant.now()))
                .sorted(Comparator.comparing(a -> a.id))
                .toList();
    }

    public List<String> getAvailabilityIssues(BathOrder order) {
        List<String> issues = new ArrayList<>();
        if (getFreeRooms(order).isEmpty()) {
            issues.add("Нет свободной комнаты выбранного типа и вместимости. Измените комнату или тип купальни.");
        }
        int free = getFreeAttendants().size();
        if (free < order.attendants) {
            issues.add("Свободных банщиков: " + free + ", требуется: " + order.attendants + ". Учтены перерывы.");
        }
        for (var line : order.lines) {
            var ingredient = ingredientRepository.findById(line.ingredientId).orElseThrow(ServiceChecks::createNotFoundException);
            var available = ingredient.stock.subtract(ingredient.reserved);
            if (available.compareTo(line.quantity) < 0) {
                issues.add(ingredient.name + ": доступно " + available.stripTrailingZeros().toPlainString() + " из " + line.quantity + " " + ingredient.unit);
            }
        }
        return issues;
    }

    public List<BathOrder> getOrderQueue() {
        return getActiveOrders().stream()
                .filter(o -> o.status == OrderStatus.IN_SERVICE && o.waterReadyAt == null)
                .sorted(
                        Comparator.<BathOrder>comparingInt(o -> o.priority)
                                .reversed()
                                .thenComparing(o -> o.launchedAt)
                )
                .toList();
    }
}
