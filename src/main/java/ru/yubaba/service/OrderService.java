package ru.yubaba.service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yubaba.controller.dto.CancelInput;
import ru.yubaba.controller.dto.OrderInput;
import ru.yubaba.controller.dto.OrderPageResponse;
import ru.yubaba.controller.dto.OrderPreviewResponse;
import ru.yubaba.controller.dto.PayInput;
import ru.yubaba.controller.dto.ScheduleResponse;
import ru.yubaba.data.entity.AuditEvent;
import ru.yubaba.data.entity.BathOrder;
import ru.yubaba.data.entity.Client;
import ru.yubaba.data.entity.Payment;
import ru.yubaba.data.enums.OrderStatus;
import ru.yubaba.data.enums.Role;
import ru.yubaba.data.repository.AccountRepository;
import ru.yubaba.data.repository.AllocationLockRepository;
import ru.yubaba.data.repository.AuditEventRepository;
import ru.yubaba.data.repository.BathOrderRepository;
import ru.yubaba.data.repository.ClientRepository;
import ru.yubaba.data.repository.IngredientRepository;
import ru.yubaba.data.repository.PaymentRepository;
import ru.yubaba.data.repository.ServiceTemplateRepository;
import ru.yubaba.service.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static ru.yubaba.service.ServiceChecks.*;

@Service
@Transactional(readOnly = true)
public class OrderService {
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final IngredientRepository ingredientRepository;
    private final ServiceTemplateRepository serviceTemplateRepository;
    private final BathOrderRepository bathOrderRepository;
    private final PaymentRepository paymentRepository;
    private final AuditEventRepository auditEventRepository;
    private final AllocationLockRepository allocationLockRepository;
    private final CurrentAccountService currentAccountService;
    private final CatalogService catalogService;
    private final ResourceAvailabilityService resourceAvailabilityService;
    private final InventoryService inventoryService;

    public OrderService(
            AccountRepository accountRepository,
            ClientRepository clientRepository,
            IngredientRepository ingredientRepository,
            ServiceTemplateRepository serviceTemplateRepository,
            BathOrderRepository bathOrderRepository,
            PaymentRepository paymentRepository,
            AuditEventRepository auditEventRepository,
            AllocationLockRepository allocationLockRepository,
            CurrentAccountService currentAccountService,
            CatalogService catalogService,
            ResourceAvailabilityService resourceAvailabilityService,
            InventoryService inventoryService
    ) {
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
        this.ingredientRepository = ingredientRepository;
        this.serviceTemplateRepository = serviceTemplateRepository;
        this.bathOrderRepository = bathOrderRepository;
        this.paymentRepository = paymentRepository;
        this.auditEventRepository = auditEventRepository;
        this.allocationLockRepository = allocationLockRepository;
        this.currentAccountService = currentAccountService;
        this.catalogService = catalogService;
        this.resourceAvailabilityService = resourceAvailabilityService;
        this.inventoryService = inventoryService;
    }

    private BathOrder buildOrder(BathOrder order, OrderInput input) {
        clientRepository.findById(input.clientId()).orElseThrow(ServiceChecks::createNotFoundException);
        if (input.templateId() != null) {
            serviceTemplateRepository.findById(input.templateId()).orElseThrow(ServiceChecks::createNotFoundException);
        }
        var composition = input.composition();
        var recipeLines = catalogService.filterRecipeLines(composition);
        order.clientId = input.clientId();
        order.templateId = input.templateId();
        order.visitors = input.visitors();
        order.priority = input.priority();
        order.serviceName = composition.name();
        order.bathType = composition.bathType();
        order.preferredRoomId = composition.preferredRoomId();
        order.attendants = composition.attendants();
        order.durationMinutes = composition.durationMinutes();
        order.preparationMinutes = composition.preparationMinutes();
        order.breakMinutes = composition.breakMinutes();
        order.temperature = composition.temperature();
        order.steps = composition.steps();
        order.extraServices = composition.extraServices();
        order.basePrice = composition.basePrice();
        order.lines.clear();
        order.lines.addAll(recipeLines);
        order.total = composition.basePrice()
                .add(
                        recipeLines.stream()
                                .map(line -> line.quantity.multiply(line.unitPrice))
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                )
                .setScale(2, RoundingMode.HALF_UP);
        return order;
    }

    public OrderPreviewResponse buildPreview(OrderInput input) {
        var order = buildOrder(new BathOrder(), input);
        return new OrderPreviewResponse(order.total, resourceAvailabilityService.getAvailabilityIssues(order), order);
    }

    @Transactional
    public BathOrder saveOrder(Long id, OrderInput input) {
        allocationLockRepository.acquire();
        var order = id == null ? new BathOrder() : bathOrderRepository.findById(id).orElseThrow(ServiceChecks::createNotFoundException);
        if (id != null) {
            compareVersion(order.version, input.version());
            check(order.status == OrderStatus.CREATED, "Состав можно менять только до запуска обслуживания.");
        }
        buildOrder(order, input);
        var issues = resourceAvailabilityService.getAvailabilityIssues(order);
        check(issues.isEmpty(), String.join(" ", issues));
        bathOrderRepository.saveAndFlush(order);
        saveAuditEvent(order, id == null ? "CREATED" : "EDITED", "Состав и стоимость сохранены: " + order.total);
        return order;
    }

    private void saveAuditEvent(BathOrder order, String action, String details) {
        var event = new AuditEvent();
        event.orderId = order.id;
        event.actorId = currentAccountService.getCurrent().id;
        event.action = action;
        event.details = details;
        auditEventRepository.save(event);
    }

    public BathOrder getOrder(Long id) {
        var order = bathOrderRepository.findById(id).orElseThrow(ServiceChecks::createNotFoundException);
        var account = currentAccountService.getCurrent();
        if (account.role == Role.ATTENDANT && !order.attendantIds.contains(account.id))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Заказ назначен другому банщику.");
        return order;
    }

    public OrderPageResponse getOrderPage(String query, OrderStatus status, int page) {
        var account = currentAccountService.getCurrent();
        String q = query.trim().toLowerCase(Locale.ROOT);
        check(page >= 0, "Некорректная страница.");
        var result = bathOrderRepository.findAll(
                (root, cq, cb) -> {
                    List<Predicate> ps = new ArrayList<>();
                    if (status != null) {
                        ps.add(cb.equal(root.get("status"), status));
                    }
                    if (account.role == Role.ATTENDANT) {
                        ps.add(cb.isMember(account.id, root.get("attendantIds")));
                    }
                    if (!q.isBlank()) {
                        var sub = cq.subquery(Long.class);
                        var client = sub.from(Client.class);
                        sub.select(client.get("id"))
                                .where(
                                        cb.or(
                                                cb.like(cb.lower(client.get("name")), "%" + q + "%"),
                                                cb.like(cb.lower(client.get("contact")), "%" + q + "%")
                                        )
                                );
                        List<Predicate> searchPredicates = new ArrayList<>(
                                List.of(
                                        root.get("clientId").in(sub),
                                        cb.like(cb.lower(root.get("serviceName")), "%" + q + "%")
                                )
                        );
                        if (q.matches("[0-9]{1,18}")) {
                            searchPredicates.add(cb.equal(root.get("id"), Long.parseLong(q)));
                        }
                        ps.add(cb.or(searchPredicates.toArray(Predicate[]::new)));
                    }
                    return cb.and(ps.toArray(Predicate[]::new));
                },
                PageRequest.of(page, 30, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new OrderPageResponse(result.getContent(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public BathOrder launchOrder(Long id, Long expected) {
        allocationLockRepository.acquire();
        var order = getOrder(id);
        compareVersion(order.version, expected);
        check(order.status == OrderStatus.CREATED, "Заказ уже запущен или завершён.");
        var issues = resourceAvailabilityService.getAvailabilityIssues(order);
        check(issues.isEmpty(), String.join(" ", issues));
        order.roomId = resourceAvailabilityService.getFreeRooms(order).get(0).id;
        order.attendantIds.addAll(resourceAvailabilityService.getFreeAttendants().stream().limit(order.attendants).map(a -> a.id).toList());
        for (var line : order.lines) {
            var ingredient = ingredientRepository.findById(line.ingredientId).orElseThrow();
            ingredient.reserved = ingredient.reserved.add(line.quantity);
        }
        order.launchedAt = Instant.now();
        order.status = OrderStatus.IN_SERVICE;
        saveAuditEvent(order, "LAUNCHED", "Комната и банщики назначены, рецепт передан котельной.");
        inventoryService.generateSupplies();
        return bathOrderRepository.saveAndFlush(order);
    }

    @Transactional
    public BathOrder setWaterReady(Long id, Long expected) {
        allocationLockRepository.acquire();
        var order = getOrder(id);
        compareVersion(order.version, expected);
        check(order.status == OrderStatus.IN_SERVICE && order.waterReadyAt == null, "Задание уже выполнено или отменено.");
        for (var line : order.lines) {
            var ingredient = ingredientRepository.findById(line.ingredientId).orElseThrow();
            ingredient.reserved = ingredient.reserved.subtract(line.quantity);
            ingredient.stock = ingredient.stock.subtract(line.quantity);
        }
        order.waterReadyAt = Instant.now();
        saveAuditEvent(order, "WATER_READY", "Вода готова. Ингредиенты списаны.");
        inventoryService.generateSupplies();
        return bathOrderRepository.saveAndFlush(order);
    }

    @Transactional
    public BathOrder startService(Long id, Long expected) {
        allocationLockRepository.acquire();
        var order = getOrder(id);
        compareVersion(order.version, expected);
        check(order.status == OrderStatus.IN_SERVICE && order.waterReadyAt != null, "Вода ещё не готова. Дождитесь котельной.");
        check(order.serviceStartedAt == null, "Услуга уже начата.");
        order.serviceStartedAt = Instant.now();
        saveAuditEvent(order, "SERVICE_STARTED", "Банщик начал обслуживание.");
        return bathOrderRepository.saveAndFlush(order);
    }

    @Transactional
    public BathOrder completeService(Long id, Long expected) {
        allocationLockRepository.acquire();
        var order = getOrder(id);
        compareVersion(order.version, expected);
        check(order.status == OrderStatus.IN_SERVICE && order.serviceStartedAt != null, "Сначала начните услугу после готовности воды.");
        order.completedAt = Instant.now();
        order.status = OrderStatus.AWAITING_PAYMENT;
        saveAuditEvent(order, "COMPLETED", "Услуга выполнена. Ожидается оплата.");
        return bathOrderRepository.saveAndFlush(order);
    }

    private void releaseResources(BathOrder order) {
        if (order.launchedAt != null && order.waterReadyAt == null) {
            for (var line : order.lines) {
                var ingredient = ingredientRepository.findById(line.ingredientId).orElseThrow();
                ingredient.reserved = ingredient.reserved.subtract(line.quantity);
            }
        }
        if (order.serviceStartedAt != null) {
            for (Long id : order.attendantIds) {
                var account = accountRepository.findById(id).orElseThrow();
                account.restUntil = Instant.now().plusSeconds(order.breakMinutes * 60L);
            }
        }
    }

    @Transactional
    public BathOrder cancelOrder(Long id, CancelInput input) {
        allocationLockRepository.acquire();
        var order = getOrder(id);
        compareVersion(order.version, input.version());
        check(order.status == OrderStatus.CREATED || order.status == OrderStatus.IN_SERVICE, "Отмена возможна только до завершения услуги.");
        check(!input.reason().isBlank(), "Укажите причину отмены.");
        releaseResources(order);
        order.status = OrderStatus.CANCELLED;
        order.cancellationReason = input.reason().trim();
        order.closedAt = Instant.now();
        saveAuditEvent(order, "CANCELLED", order.cancellationReason);
        return bathOrderRepository.saveAndFlush(order);
    }

    @Transactional
    public BathOrder payOrder(Long id, PayInput input) {
        allocationLockRepository.acquire();
        var order = getOrder(id);
        compareVersion(order.version, input.version());
        check(order.status == OrderStatus.AWAITING_PAYMENT, "Оплата доступна после завершения услуги.");
        var payment = new Payment();
        payment.orderId = order.id;
        payment.amount = order.total;
        payment.method = input.method();
        payment.recordedBy = currentAccountService.getCurrent().id;
        paymentRepository.save(payment);
        releaseResources(order);
        order.status = OrderStatus.CLOSED;
        order.closedAt = Instant.now();
        saveAuditEvent(order, "PAID", "Оплата " + payment.amount + " (" + payment.method + "). Заказ закрыт, ресурсы освобождены.");
        return bathOrderRepository.saveAndFlush(order);
    }

    public List<AuditEvent> getOrderAudit(Long id) {
        getOrder(id);
        return auditEventRepository.findByOrderIdOrderByOccurredAtAsc(id);
    }

    public ScheduleResponse getSchedule() {
        var account = currentAccountService.getCurrent();
        var today = LocalDate.now(ZoneId.of("Europe/Moscow"));
        List<BathOrder> todayOrders = bathOrderRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        today.atStartOfDay(ZoneId.of("Europe/Moscow")).toInstant(),
                        today.plusDays(1).atStartOfDay(ZoneId.of("Europe/Moscow")).toInstant()
                )
                .stream()
                .filter(o -> o.attendantIds.contains(account.id))
                .sorted(Comparator.comparing(o -> o.launchedAt))
                .toList();
        return new ScheduleResponse(
                todayOrders,
                resourceAvailabilityService.getActiveOrders().stream()
                        .filter(o -> o.attendantIds.contains(account.id))
                        .toList(),
                account.restUntil == null ? "" : account.restUntil.toString()
        );
    }
}
