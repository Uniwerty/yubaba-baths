package ru.yubaba.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yubaba.controller.dto.*;
import ru.yubaba.data.entity.*;
import ru.yubaba.data.enums.OrderStatus;
import ru.yubaba.data.enums.Role;
import ru.yubaba.data.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BathService {
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final RoomRepository roomRepository;
    private final IngredientRepository ingredientRepository;
    private final ServiceTemplateRepository serviceTemplateRepository;
    private final BathOrderRepository bathOrderRepository;
    private final PaymentRepository paymentRepository;
    private final AuditEventRepository auditEventRepository;
    private final ReportTemplateRepository reportTemplateRepository;
    private final SupplyRequestRepository supplyRequestRepository;
    private final AllocationLockRepository allocationLockRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public BathService(AccountRepository accountRepository,
                       ClientRepository clientRepository,
                       RoomRepository roomRepository,
                       IngredientRepository ingredientRepository,
                       ServiceTemplateRepository serviceTemplateRepository,
                       BathOrderRepository bathOrderRepository,
                       PaymentRepository paymentRepository,
                       AuditEventRepository auditEventRepository,
                       ReportTemplateRepository reportTemplateRepository,
                       SupplyRequestRepository supplyRequestRepository,
                       AllocationLockRepository allocationLockRepository,
                       PasswordEncoder passwordEncoder,
                       ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
        this.roomRepository = roomRepository;
        this.ingredientRepository = ingredientRepository;
        this.serviceTemplateRepository = serviceTemplateRepository;
        this.bathOrderRepository = bathOrderRepository;
        this.paymentRepository = paymentRepository;
        this.auditEventRepository = auditEventRepository;
        this.reportTemplateRepository = reportTemplateRepository;
        this.supplyRequestRepository = supplyRequestRepository;
        this.allocationLockRepository = allocationLockRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    public Account current() {
        return accountRepository.findByLogin(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow();
    }

    private BusinessException missing() {
        return new BusinessException(HttpStatus.NOT_FOUND, "Запись не найдена.");
    }

    private void check(boolean ok, String message) {
        if (!ok) throw new BusinessException(message);
    }

    private void compareVersion(long actual, Long expected) {
        check(expected != null && actual == expected, "Данные изменены другим сотрудником. Обновите страницу.");
    }

    private List<BathOrder> getActiveOrders() {
        return bathOrderRepository.findByStatusIn(List.of(OrderStatus.IN_SERVICE, OrderStatus.AWAITING_PAYMENT));
    }

    public CatalogResponse getCatalog() {
        return new CatalogResponse(
                serviceTemplateRepository.findAll(),
                roomRepository.findAll(),
                ingredientRepository.findAll()
        );
    }

    public List<Client> getClients(String query) {
        String q = query.toLowerCase().trim();
        return clientRepository.findAll().stream()
                .filter(c -> (c.name + " " + c.contact).toLowerCase().contains(q))
                .sorted(Comparator.comparing(c -> c.name))
                .toList();
    }

    @Transactional
    public Client saveClient(Long id, ClientInput input) {
        allocationLockRepository.acquire();
        Client client = id == null ? new Client() : clientRepository.findById(id).orElseThrow(this::missing);
        if (id != null) {
            compareVersion(client.version, input.version());
        }
        client.name = input.name().trim();
        client.contact = input.contact().trim();
        client.notes = Objects.requireNonNullElse(input.notes(), "").trim();
        check(!client.name.isBlank() && !client.contact.isBlank(), "Укажите имя и контакт клиента.");
        return clientRepository.saveAndFlush(client);
    }

    private List<RecipeLine> filterRecipeLines(Composition composition) {
        Set<Long> seen = new HashSet<>();
        List<RecipeLine> result = new ArrayList<>();
        for (var line : composition.lines()) {
            check(seen.add(line.ingredientId()), "Ингредиент указан дважды.");
            result.add(
                    new RecipeLine(
                            ingredientRepository.findById(line.ingredientId()).orElseThrow(this::missing),
                            line.quantity()
                    )
            );
        }
        check(
                roomRepository.findAll().stream().anyMatch(r -> r.bathType.equals(composition.bathType())),
                "Нет комнат выбранного типа купальни."
        );
        if (composition.preferredRoomId() != null) {
            var r = roomRepository.findById(composition.preferredRoomId()).orElseThrow(this::missing);
            check(r.bathType.equals(composition.bathType()), "Комната не соответствует типу купальни.");
        }
        return result;
    }

    @Transactional
    public ServiceTemplate saveTemplate(Long id, TemplateInput input) {
        allocationLockRepository.acquire();
        var template = id == null ? new ServiceTemplate() : serviceTemplateRepository.findById(id).orElseThrow(this::missing);
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

    private BathOrder buildOrder(BathOrder order, OrderInput input) {
        clientRepository.findById(input.clientId()).orElseThrow(this::missing);
        if (input.templateId() != null) {
            serviceTemplateRepository.findById(input.templateId()).orElseThrow(this::missing);
        }
        var composition = input.composition();
        var recipeLines = filterRecipeLines(composition);
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

    private List<Room> getFreeRooms(BathOrder o) {
        Set<Long> busy = getActiveOrders().stream().map(a -> a.roomId).collect(Collectors.toSet());
        return roomRepository.findAll().stream()
                .filter(r -> r.bathType.equals(o.bathType) && r.capacity >= o.visitors && !busy.contains(r.id))
                .filter(r -> o.preferredRoomId == null || o.preferredRoomId.equals(r.id))
                .sorted(Comparator.comparing(r -> r.id))
                .toList();
    }

    private List<Account> getFreeAttendants() {
        Set<Long> busy = getActiveOrders().stream()
                .flatMap(a -> a.attendantIds.stream())
                .collect(Collectors.toSet());
        return accountRepository.findAll().stream()
                .filter(a -> a.role == Role.ATTENDANT && !a.blocked && !busy.contains(a.id))
                .filter(a -> a.restUntil == null || !a.restUntil.isAfter(Instant.now()))
                .sorted(Comparator.comparing(a -> a.id))
                .toList();
    }

    private List<String> getAvailabilityIssues(BathOrder order) {
        List<String> issues = new ArrayList<>();
        if (getFreeRooms(order).isEmpty()) {
            issues.add("Нет свободной комнаты выбранного типа и вместимости. Измените комнату или тип купальни.");
        }
        int free = getFreeAttendants().size();
        if (free < order.attendants) {
            issues.add("Свободных банщиков: " + free + ", требуется: " + order.attendants + ". Учтены перерывы.");
        }
        for (var line : order.lines) {
            var ingredient = ingredientRepository.findById(line.ingredientId).orElseThrow(this::missing);
            var available = ingredient.stock.subtract(ingredient.reserved);
            if (available.compareTo(line.quantity) < 0) {
                issues.add(ingredient.name + ": доступно " + available.stripTrailingZeros().toPlainString() + " из " + line.quantity + " " + ingredient.unit);
            }
        }
        return issues;
    }

    public OrderPreviewResponse buildPreview(OrderInput input) {
        var order = buildOrder(new BathOrder(), input);
        return new OrderPreviewResponse(order.total, getAvailabilityIssues(order), order);
    }

    @Transactional
    public BathOrder saveOrder(Long id, OrderInput input) {
        allocationLockRepository.acquire();
        var order = id == null ? new BathOrder() : bathOrderRepository.findById(id).orElseThrow(this::missing);
        if (id != null) {
            compareVersion(order.version, input.version());
            check(order.status == OrderStatus.CREATED, "Состав можно менять только до запуска обслуживания.");
        }
        buildOrder(order, input);
        var issues = getAvailabilityIssues(order);
        check(issues.isEmpty(), String.join(" ", issues));
        bathOrderRepository.saveAndFlush(order);
        saveAuditEvent(order, id == null ? "CREATED" : "EDITED", "Состав и стоимость сохранены: " + order.total);
        return order;
    }

    private void saveAuditEvent(BathOrder order, String action, String details) {
        var event = new AuditEvent();
        event.orderId = order.id;
        event.actorId = current().id;
        event.action = action;
        event.details = details;
        auditEventRepository.save(event);
    }

    public BathOrder getOrder(Long id) {
        var order = bathOrderRepository.findById(id).orElseThrow(this::missing);
        var account = current();
        if (account.role == Role.ATTENDANT && !order.attendantIds.contains(account.id))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Заказ назначен другому банщику.");
        return order;
    }

    public OrderPageResponse getOrderPage(String query, OrderStatus status, int page) {
        var account = current();
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
        var issues = getAvailabilityIssues(order);
        check(issues.isEmpty(), String.join(" ", issues));
        order.roomId = getFreeRooms(order).get(0).id;
        order.attendantIds.addAll(getFreeAttendants().stream().limit(order.attendants).map(a -> a.id).toList());
        for (var line : order.lines) {
            var ingredient = ingredientRepository.findById(line.ingredientId).orElseThrow();
            ingredient.reserved = ingredient.reserved.add(line.quantity);
        }
        order.launchedAt = Instant.now();
        order.status = OrderStatus.IN_SERVICE;
        saveAuditEvent(order, "LAUNCHED", "Комната и банщики назначены, рецепт передан котельной.");
        generateSupplies();
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
        generateSupplies();
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
        payment.recordedBy = current().id;
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

    public ScheduleResponse getSchedule() {
        var account = current();
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
                getActiveOrders().stream()
                        .filter(o -> o.attendantIds.contains(account.id))
                        .toList(),
                account.restUntil == null ? "" : account.restUntil.toString()
        );
    }

    public List<Account> getAccounts() {
        return accountRepository.findAll();
    }

    @Transactional
    public Account saveAccount(AccountInput input) {
        allocationLockRepository.acquire();
        var account = new Account();
        account.login = input.login();
        account.name = input.name().trim();
        account.role = input.role();
        account.password = passwordEncoder.encode(input.password());
        return accountRepository.saveAndFlush(account);
    }

    @Transactional
    public Account blockAccount(Long id, boolean blocked) {
        allocationLockRepository.acquire();
        var account = accountRepository.findById(id).orElseThrow(this::missing);
        check(!account.id.equals(current().id), "Нельзя заблокировать собственную учётную запись.");
        if (blocked) {
            check(
                    getActiveOrders().stream().noneMatch(o -> o.attendantIds.contains(account.id)),
                    "У банщика есть активный заказ. Сначала завершите или отмените его."
            );
        }
        account.blocked = blocked;
        return accountRepository.saveAndFlush(account);
    }

    @Transactional
    public Room saveRoom(RoomInput input) {
        allocationLockRepository.acquire();
        var room = new Room();
        room.name = input.name();
        room.bathType = input.bathType();
        room.capacity = input.capacity();
        return roomRepository.saveAndFlush(room);
    }

    @Transactional
    public Ingredient saveIngredient(IngredientInput input) {
        allocationLockRepository.acquire();
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

    private void generateSupplies() {
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
        allocationLockRepository.acquire();
        generateSupplies();
        return supplyRequestRepository.findAll();
    }

    @Transactional
    public SupplyRequest receiveSupply(Long id) {
        allocationLockRepository.acquire();
        var supplyRequest = supplyRequestRepository.findById(id).orElseThrow(this::missing);
        check(supplyRequest.status.equals("OPEN"), "Поставка уже принята.");
        var ingredient = ingredientRepository.findById(supplyRequest.ingredientId).orElseThrow();
        ingredient.stock = ingredient.stock.add(supplyRequest.quantity);
        supplyRequest.status = "RECEIVED";
        supplyRequest.receivedAt = Instant.now();
        return supplyRequestRepository.saveAndFlush(supplyRequest);
    }

    public String supplyExport(Long id) {
        var supplyRequest = supplyRequestRepository.findById(id).orElseThrow(this::missing);
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

    private Instant[] buildReportPeriod(ReportFilter filter) {
        var zone = ZoneId.of("Europe/Moscow");
        var from = filter.from() == null ? LocalDate.now(zone) : filter.from();
        var to = filter.to() == null ? LocalDate.now(zone) : filter.to();
        check(!to.isBefore(from), "Начало периода должно быть не позже окончания.");
        check(ChronoUnit.DAYS.between(from, to) <= 366, "Выберите период не более одного года.");
        return new Instant[]{
                from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant()
        };
    }

    public ReportResponse getReport(ReportFilter filter) {
        var range = buildReportPeriod(filter);
        var candidates = bathOrderRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(range[0], range[1]);
        var payments = candidates.isEmpty()
                ? List.<Payment>of()
                : paymentRepository.findByOrderIdIn(candidates.stream().map(o -> o.id).toList());
        var byOrder = payments.stream().collect(Collectors.toMap(p -> p.orderId, p -> p));
        var selectedOrders = candidates.stream()
                .filter(o -> filter.templateId() == null || filter.templateId().equals(o.templateId))
                .filter(o -> filter.visitors() == null || filter.visitors() == o.visitors)
                .filter(o -> filter.method() == null || byOrder.containsKey(o.id) && byOrder.get(o.id).method == filter.method())
                .toList();
        var ids = selectedOrders.stream().map(o -> o.id).collect(Collectors.toSet());
        var selectedPayments = payments.stream().filter(p -> ids.contains(p.orderId)).toList();
        return new ReportResponse(
                selectedOrders,
                selectedPayments,
                selectedOrders.size(),
                selectedOrders.stream()
                        .filter(o -> o.status != OrderStatus.CANCELLED)
                        .mapToInt(o -> o.visitors)
                        .sum(),
                selectedPayments.stream()
                        .map(p -> p.amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                selectedOrders.isEmpty()
        );
    }

    public DashboardResponse getDashboard(ReportFilter filter) {
        var range = buildReportPeriod(filter);
        var selectedOrders = bathOrderRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(range[0], range[1]);
        var activeOrders = getActiveOrders();
        var paidPayments = paymentRepository.findByPaidAtGreaterThanEqualAndPaidAtLessThan(range[0], range[1]);
        var roomStates = roomRepository.findAll().stream()
                .map(r ->
                        new RoomStateResponse(
                                r,
                                activeOrders.stream()
                                        .filter(o -> r.id.equals(o.roomId))
                                        .map(o -> o.id)
                                        .toList()
                        )
                )
                .toList();
        var staffStates = accountRepository.findAll().stream()
                .filter(a -> a.role == Role.ATTENDANT && !a.blocked)
                .map(a ->
                        new AttendantStateResponse(
                                a,
                                activeOrders.stream()
                                        .filter(o -> o.attendantIds.contains(a.id))
                                        .map(o -> o.id)
                                        .toList()
                        )
                )
                .toList();
        double averageDuration = selectedOrders.stream()
                .filter(o -> o.serviceStartedAt != null && o.completedAt != null)
                .mapToLong(o -> Duration.between(o.serviceStartedAt, o.completedAt).getSeconds())
                .average()
                .orElse(0) / 60.0;
        return new DashboardResponse(
                roomStates,
                staffStates,
                getOrderQueue(),
                selectedOrders.size(),
                averageDuration,
                paidPayments.stream()
                        .map(p -> p.amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                selectedOrders.isEmpty(),
                Instant.now()
        );
    }

    public List<ReportTemplate> getReportTemplates() {
        var account = current();
        return reportTemplateRepository.findAll().stream()
                .filter(t -> t.ownerId.equals(account.id))
                .toList();
    }

    @Transactional
    public ReportTemplate saveReportTemplate(ReportInput input) {
        buildReportPeriod(input.parameters());
        var template = new ReportTemplate();
        template.ownerId = current().id;
        template.name = input.name();
        try {
            template.parameters = objectMapper.writeValueAsString(input.parameters());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return reportTemplateRepository.save(template);
    }
}
