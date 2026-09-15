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
import ru.yubaba.controller.Requests.*;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BathService {
    private final AccountRepository accounts;
    private final ClientRepository clients;
    private final RoomRepository rooms;
    private final IngredientRepository ingredients;
    private final ServiceTemplateRepository templates;
    private final BathOrderRepository orders;
    private final PaymentRepository payments;
    private final AuditEventRepository audits;
    private final ReportTemplateRepository reports;
    private final SupplyRequestRepository supplies;
    private final AllocationLockRepository locks;
    private final PasswordEncoder passwords;
    private final ObjectMapper json;

    public BathService(AccountRepository accounts, ClientRepository clients, RoomRepository rooms, IngredientRepository ingredients,
                       ServiceTemplateRepository templates, BathOrderRepository orders, PaymentRepository payments, AuditEventRepository audits,
                       ReportTemplateRepository reports, SupplyRequestRepository supplies, AllocationLockRepository locks, PasswordEncoder passwords, ObjectMapper json) {
        this.accounts = accounts;
        this.clients = clients;
        this.rooms = rooms;
        this.ingredients = ingredients;
        this.templates = templates;
        this.orders = orders;
        this.payments = payments;
        this.audits = audits;
        this.reports = reports;
        this.supplies = supplies;
        this.locks = locks;
        this.passwords = passwords;
        this.json = json;
    }

    public Account current() {
        return accounts.findByLogin(SecurityContextHolder.getContext().getAuthentication().getName()).orElseThrow();
    }

    private BusinessException missing() {
        return new BusinessException(HttpStatus.NOT_FOUND, "Запись не найдена.");
    }

    private void check(boolean ok, String message) {
        if (!ok) throw new BusinessException(message);
    }

    private void version(long actual, Long expected) {
        check(expected != null && actual == expected, "Данные изменены другим сотрудником. Обновите страницу.");
    }

    private List<BathOrder> active() {
        return orders.findByStatusIn(List.of(OrderStatus.IN_SERVICE, OrderStatus.AWAITING_PAYMENT));
    }

    public Object catalog() {
        return Map.of("templates", templates.findAll(), "rooms", rooms.findAll(), "ingredients", ingredients.findAll());
    }

    public List<Client> clients(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        return clients.findAll().stream()
                .filter(c -> (c.name + " " + c.contact).toLowerCase(Locale.ROOT).contains(q)).sorted(Comparator.comparing(c -> c.name)).toList();
    }

    @Transactional
    public Client saveClient(Long id, ClientInput input) {
        locks.acquire();
        Client c = id == null ? new Client() : clients.findById(id).orElseThrow(this::missing);
        if (id != null) version(c.version, input.version());
        c.name = input.name().trim();
        c.contact = input.contact().trim();
        c.notes = Objects.requireNonNullElse(input.notes(), "").trim();
        check(!c.name.isBlank() && !c.contact.isBlank(), "Укажите имя и контакт клиента.");
        return clients.saveAndFlush(c);
    }

    private List<RecipeLine> lines(Composition c) {
        Set<Long> seen = new HashSet<>();
        List<RecipeLine> result = new ArrayList<>();
        for (var l : c.lines()) {
            check(seen.add(l.ingredientId()), "Ингредиент указан дважды.");
            result.add(new RecipeLine(ingredients.findById(l.ingredientId()).orElseThrow(this::missing), l.quantity()));
        }
        check(rooms.findAll().stream().anyMatch(r -> r.bathType.equals(c.bathType())), "Нет комнат выбранного типа купальни.");
        if (c.preferredRoomId() != null) {
            var r = rooms.findById(c.preferredRoomId()).orElseThrow(this::missing);
            check(r.bathType.equals(c.bathType()), "Комната не соответствует типу купальни.");
        }
        return result;
    }

    @Transactional
    public ServiceTemplate saveTemplate(Long id, TemplateInput input) {
        locks.acquire();
        var t = id == null ? new ServiceTemplate() : templates.findById(id).orElseThrow(this::missing);
        if (id != null) version(t.version, input.version());
        var c = input.composition();
        var ls = lines(c);
        t.name = c.name().trim();
        t.bathType = c.bathType();
        t.preferredRoomId = c.preferredRoomId();
        t.attendants = c.attendants();
        t.durationMinutes = c.durationMinutes();
        t.preparationMinutes = c.preparationMinutes();
        t.breakMinutes = c.breakMinutes();
        t.temperature = c.temperature();
        t.steps = c.steps();
        t.extraServices = c.extraServices();
        t.basePrice = c.basePrice();
        t.lines.clear();
        t.lines.addAll(ls);
        return templates.saveAndFlush(t);
    }

    private BathOrder compose(BathOrder o, OrderInput input) {
        clients.findById(input.clientId()).orElseThrow(this::missing);
        if (input.templateId() != null) templates.findById(input.templateId()).orElseThrow(this::missing);
        var c = input.composition();
        var ls = lines(c);
        o.clientId = input.clientId();
        o.templateId = input.templateId();
        o.visitors = input.visitors();
        o.priority = input.priority();
        o.serviceName = c.name();
        o.bathType = c.bathType();
        o.preferredRoomId = c.preferredRoomId();
        o.attendants = c.attendants();
        o.durationMinutes = c.durationMinutes();
        o.preparationMinutes = c.preparationMinutes();
        o.breakMinutes = c.breakMinutes();
        o.temperature = c.temperature();
        o.steps = c.steps();
        o.extraServices = c.extraServices();
        o.basePrice = c.basePrice();
        o.lines.clear();
        o.lines.addAll(ls);
        o.total = c.basePrice().add(ls.stream().map(l -> l.quantity.multiply(l.unitPrice)).reduce(BigDecimal.ZERO, BigDecimal::add)).setScale(2, RoundingMode.HALF_UP);
        return o;
    }

    private List<Room> freeRooms(BathOrder o) {
        Set<Long> busy = active().stream().map(a -> a.roomId).collect(Collectors.toSet());
        return rooms.findAll().stream().filter(r -> r.bathType.equals(o.bathType) && r.capacity >= o.visitors && !busy.contains(r.id))
                .filter(r -> o.preferredRoomId == null || o.preferredRoomId.equals(r.id)).sorted(Comparator.comparing(r -> r.id)).toList();
    }

    private List<Account> freeAttendants() {
        Set<Long> busy = active().stream().flatMap(a -> a.attendantIds.stream()).collect(Collectors.toSet());
        return accounts.findAll().stream().filter(a -> a.role == Role.ATTENDANT && !a.blocked && !busy.contains(a.id))
                .filter(a -> a.restUntil == null || !a.restUntil.isAfter(Instant.now())).sorted(Comparator.comparing(a -> a.id)).toList();
    }

    private List<String> availability(BathOrder o) {
        List<String> issues = new ArrayList<>();
        if (freeRooms(o).isEmpty())
            issues.add("Нет свободной комнаты выбранного типа и вместимости. Измените комнату или тип купальни.");
        int free = freeAttendants().size();
        if (free < o.attendants)
            issues.add("Свободных банщиков: " + free + ", требуется: " + o.attendants + ". Учтены перерывы.");
        for (var l : o.lines) {
            var i = ingredients.findById(l.ingredientId).orElseThrow(this::missing);
            var available = i.stock.subtract(i.reserved);
            if (available.compareTo(l.quantity) < 0)
                issues.add(i.name + ": доступно " + available.stripTrailingZeros().toPlainString() + " из " + l.quantity + " " + i.unit);
        }
        return issues;
    }

    public Object preview(OrderInput input) {
        var o = compose(new BathOrder(), input);
        return Map.of("total", o.total, "issues", availability(o), "order", o);
    }

    @Transactional
    public BathOrder saveOrder(Long id, OrderInput input) {
        locks.acquire();
        var o = id == null ? new BathOrder() : orders.findById(id).orElseThrow(this::missing);
        if (id != null) {
            version(o.version, input.version());
            check(o.status == OrderStatus.CREATED, "Состав можно менять только до запуска обслуживания.");
        }
        compose(o, input);
        var issues = availability(o);
        check(issues.isEmpty(), String.join(" ", issues));
        orders.saveAndFlush(o);
        audit(o, id == null ? "CREATED" : "EDITED", "Состав и стоимость сохранены: " + o.total);
        return o;
    }

    private void audit(BathOrder o, String action, String details) {
        var e = new AuditEvent();
        e.orderId = o.id;
        e.actorId = current().id;
        e.action = action;
        e.details = details;
        audits.save(e);
    }

    public BathOrder order(Long id) {
        var o = orders.findById(id).orElseThrow(this::missing);
        var a = current();
        if (a.role == Role.ATTENDANT && !o.attendantIds.contains(a.id))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Заказ назначен другому банщику.");
        return o;
    }

    public Object orderPage(String query, OrderStatus status, int page) {
        var a = current();
        String q = query.trim().toLowerCase(Locale.ROOT);
        check(page >= 0, "Некорректная страница.");
        var result = orders.findAll((root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (a.role == Role.ATTENDANT) ps.add(cb.isMember(a.id, root.get("attendantIds")));
            if (!q.isBlank()) {
                var sub = cq.subquery(Long.class);
                var client = sub.from(Client.class);
                sub.select(client.get("id")).where(cb.or(cb.like(cb.lower(client.get("name")), "%" + q + "%"), cb.like(cb.lower(client.get("contact")), "%" + q + "%")));
                List<Predicate> searches = new ArrayList<>(List.of(root.get("clientId").in(sub), cb.like(cb.lower(root.get("serviceName")), "%" + q + "%")));
                if (q.matches("[0-9]{1,18}")) searches.add(cb.equal(root.get("id"), Long.parseLong(q)));
                ps.add(cb.or(searches.toArray(Predicate[]::new)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        }, PageRequest.of(page, 30, Sort.by(Sort.Direction.DESC, "createdAt")));
        return Map.of("items", result.getContent(), "total", result.getTotalElements(), "pages", result.getTotalPages());
    }

    @Transactional
    public BathOrder launch(Long id, Long expected) {
        locks.acquire();
        var o = order(id);
        version(o.version, expected);
        check(o.status == OrderStatus.CREATED, "Заказ уже запущен или завершён.");
        var issues = availability(o);
        check(issues.isEmpty(), String.join(" ", issues));
        o.roomId = freeRooms(o).get(0).id;
        o.attendantIds.addAll(freeAttendants().stream().limit(o.attendants).map(a -> a.id).toList());
        for (var l : o.lines) {
            var i = ingredients.findById(l.ingredientId).orElseThrow();
            i.reserved = i.reserved.add(l.quantity);
        }
        o.launchedAt = Instant.now();
        o.status = OrderStatus.IN_SERVICE;
        audit(o, "LAUNCHED", "Комната и банщики назначены, рецепт передан котельной.");
        generateSupplies();
        return orders.saveAndFlush(o);
    }

    @Transactional
    public BathOrder waterReady(Long id, Long expected) {
        locks.acquire();
        var o = order(id);
        version(o.version, expected);
        check(o.status == OrderStatus.IN_SERVICE && o.waterReadyAt == null, "Задание уже выполнено или отменено.");
        for (var l : o.lines) {
            var i = ingredients.findById(l.ingredientId).orElseThrow();
            i.reserved = i.reserved.subtract(l.quantity);
            i.stock = i.stock.subtract(l.quantity);
        }
        o.waterReadyAt = Instant.now();
        audit(o, "WATER_READY", "Вода готова. Ингредиенты списаны.");
        generateSupplies();
        return orders.saveAndFlush(o);
    }

    @Transactional
    public BathOrder startService(Long id, Long expected) {
        locks.acquire();
        var o = order(id);
        version(o.version, expected);
        check(o.status == OrderStatus.IN_SERVICE && o.waterReadyAt != null, "Вода ещё не готова. Дождитесь котельной.");
        check(o.serviceStartedAt == null, "Услуга уже начата.");
        o.serviceStartedAt = Instant.now();
        audit(o, "SERVICE_STARTED", "Банщик начал обслуживание.");
        return orders.saveAndFlush(o);
    }

    @Transactional
    public BathOrder complete(Long id, Long expected) {
        locks.acquire();
        var o = order(id);
        version(o.version, expected);
        check(o.status == OrderStatus.IN_SERVICE && o.serviceStartedAt != null, "Сначала начните услугу после готовности воды.");
        o.completedAt = Instant.now();
        o.status = OrderStatus.AWAITING_PAYMENT;
        audit(o, "COMPLETED", "Услуга выполнена. Ожидается оплата.");
        return orders.saveAndFlush(o);
    }

    private void release(BathOrder o) {
        if (o.launchedAt != null && o.waterReadyAt == null) for (var l : o.lines) {
            var i = ingredients.findById(l.ingredientId).orElseThrow();
            i.reserved = i.reserved.subtract(l.quantity);
        }
        if (o.serviceStartedAt != null) for (Long id : o.attendantIds) {
            var a = accounts.findById(id).orElseThrow();
            a.restUntil = Instant.now().plusSeconds(o.breakMinutes * 60L);
        }
    }

    @Transactional
    public BathOrder cancel(Long id, CancelInput input) {
        locks.acquire();
        var o = order(id);
        version(o.version, input.version());
        check(o.status == OrderStatus.CREATED || o.status == OrderStatus.IN_SERVICE, "Отмена возможна только до завершения услуги.");
        check(!input.reason().isBlank(), "Укажите причину отмены.");
        release(o);
        o.status = OrderStatus.CANCELLED;
        o.cancellationReason = input.reason().trim();
        o.closedAt = Instant.now();
        audit(o, "CANCELLED", o.cancellationReason);
        return orders.saveAndFlush(o);
    }

    @Transactional
    public BathOrder pay(Long id, PayInput input) {
        locks.acquire();
        var o = order(id);
        version(o.version, input.version());
        check(o.status == OrderStatus.AWAITING_PAYMENT, "Оплата доступна после завершения услуги.");
        var p = new Payment();
        p.orderId = o.id;
        p.amount = o.total;
        p.method = input.method();
        p.recordedBy = current().id;
        payments.save(p);
        release(o);
        o.status = OrderStatus.CLOSED;
        o.closedAt = Instant.now();
        audit(o, "PAID", "Оплата " + p.amount + " (" + p.method + "). Заказ закрыт, ресурсы освобождены.");
        return orders.saveAndFlush(o);
    }

    public List<AuditEvent> audit(Long id) {
        order(id);
        return audits.findByOrderIdOrderByOccurredAtAsc(id);
    }

    public List<BathOrder> queue() {
        return active().stream().filter(o -> o.status == OrderStatus.IN_SERVICE && o.waterReadyAt == null)
                .sorted(Comparator.<BathOrder>comparingInt(o -> o.priority).reversed().thenComparing(o -> o.launchedAt)).toList();
    }

    public Object schedule() {
        var a = current();
        var today = LocalDate.now(ZoneId.of("Europe/Moscow"));
        return Map.of("orders", orders.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(today.atStartOfDay(ZoneId.of("Europe/Moscow")).toInstant(), today.plusDays(1).atStartOfDay(ZoneId.of("Europe/Moscow")).toInstant()).stream()
                .filter(o -> o.attendantIds.contains(a.id)).sorted(Comparator.comparing(o -> o.launchedAt)).toList(), "active", active().stream().filter(o -> o.attendantIds.contains(a.id)).toList(), "restUntil", a.restUntil == null ? "" : a.restUntil.toString());
    }

    public List<Account> accounts() {
        return accounts.findAll();
    }

    @Transactional
    public Account addAccount(AccountInput input) {
        locks.acquire();
        var a = new Account();
        a.login = input.login();
        a.name = input.name().trim();
        a.role = input.role();
        a.password = passwords.encode(input.password());
        return accounts.saveAndFlush(a);
    }

    @Transactional
    public Account block(Long id, boolean blocked) {
        locks.acquire();
        var a = accounts.findById(id).orElseThrow(this::missing);
        check(!a.id.equals(current().id), "Нельзя заблокировать собственную учётную запись.");
        if (blocked)
            check(active().stream().noneMatch(o -> o.attendantIds.contains(a.id)), "У банщика есть активный заказ. Сначала завершите или отмените его.");
        a.blocked = blocked;
        return accounts.saveAndFlush(a);
    }

    @Transactional
    public Room addRoom(RoomInput input) {
        locks.acquire();
        var r = new Room();
        r.name = input.name();
        r.bathType = input.bathType();
        r.capacity = input.capacity();
        return rooms.saveAndFlush(r);
    }

    @Transactional
    public Ingredient addIngredient(IngredientInput input) {
        locks.acquire();
        var i = new Ingredient();
        i.name = input.name();
        i.unit = input.unit();
        i.stock = input.stock();
        i.threshold = input.threshold();
        i.price = input.price();
        ingredients.saveAndFlush(i);
        generateSupplies();
        return i;
    }

    private void generateSupplies() {
        var open = supplies.findAll().stream().filter(s -> s.status.equals("OPEN")).collect(Collectors.toMap(s -> s.ingredientId, s -> s));
        for (var i : ingredients.findAll()) {
            var available = i.stock.subtract(i.reserved);
            if (available.compareTo(i.threshold) <= 0) {
                var quantity = i.threshold.multiply(BigDecimal.valueOf(2)).subtract(available).max(BigDecimal.ONE);
                var s = open.get(i.id);
                if (s == null) {
                    s = new SupplyRequest();
                    s.ingredientId = i.id;
                    s.ingredientName = i.name;
                    s.unit = i.unit;
                    s.quantity = quantity;
                    supplies.save(s);
                } else s.quantity = s.quantity.max(quantity);
            }
        }
    }

    @Transactional
    public List<SupplyRequest> supplyRequests() {
        locks.acquire();
        generateSupplies();
        return supplies.findAll();
    }

    @Transactional
    public SupplyRequest receive(Long id) {
        locks.acquire();
        var s = supplies.findById(id).orElseThrow(this::missing);
        check(s.status.equals("OPEN"), "Поставка уже принята.");
        var i = ingredients.findById(s.ingredientId).orElseThrow();
        i.stock = i.stock.add(s.quantity);
        s.status = "RECEIVED";
        s.receivedAt = Instant.now();
        return supplies.saveAndFlush(s);
    }

    public String supplyExport(Long id) {
        var s = supplies.findById(id).orElseThrow(this::missing);
        return "ЗАЯВКА НА ПОСТАВКУ №" + s.id + "\nКупальни «Юбаба»\nДата: " + s.createdAt.atZone(ZoneId.of("Europe/Moscow")).toLocalDate() +
                "\nПоставщик: ____________________\n\nИнгредиент: " + s.ingredientName + "\nКоличество: " + s.quantity.stripTrailingZeros().toPlainString() + " " + s.unit + "\n\nОтветственный: ____________________\n";
    }

    private Instant[] period(ReportFilter f) {
        var zone = ZoneId.of("Europe/Moscow");
        var from = f.from() == null ? LocalDate.now(zone) : f.from();
        var to = f.to() == null ? LocalDate.now(zone) : f.to();
        check(!to.isBefore(from), "Начало периода должно быть не позже окончания.");
        check(java.time.temporal.ChronoUnit.DAYS.between(from, to) <= 366, "Выберите период не более одного года.");
        return new Instant[]{from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant()};
    }

    public Object report(ReportFilter f) {
        var range = period(f);
        var candidates = orders.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(range[0], range[1]);
        var ps = candidates.isEmpty() ? List.<Payment>of() : payments.findByOrderIdIn(candidates.stream().map(o -> o.id).toList());
        var byOrder = ps.stream().collect(Collectors.toMap(p -> p.orderId, p -> p));
        var all = candidates.stream()
                .filter(o -> f.templateId() == null || f.templateId().equals(o.templateId)).filter(o -> f.visitors() == null || f.visitors() == o.visitors)
                .filter(o -> f.method() == null || byOrder.containsKey(o.id) && byOrder.get(o.id).method == f.method()).toList();
        // Reports use order creation date consistently, including associated payments made later.
        var ids = all.stream().map(o -> o.id).collect(Collectors.toSet());
        var selected = ps.stream().filter(p -> ids.contains(p.orderId)).toList();
        return Map.of("orders", all, "payments", selected, "orderCount", all.size(), "visitors", all.stream().filter(o -> o.status != OrderStatus.CANCELLED).mapToInt(o -> o.visitors).sum(),
                "revenue", selected.stream().map(p -> p.amount).reduce(BigDecimal.ZERO, BigDecimal::add), "empty", all.isEmpty());
    }

    public Object dashboard(ReportFilter f) {
        var range = period(f);
        var selected = orders.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(range[0], range[1]);
        var active = active();
        var paid = payments.findByPaidAtGreaterThanEqualAndPaidAtLessThan(range[0], range[1]);
        var roomState = rooms.findAll().stream().map(r -> Map.of("room", r, "orders", active.stream().filter(o -> r.id.equals(o.roomId)).map(o -> o.id).toList())).toList();
        var staffState = accounts.findAll().stream().filter(a -> a.role == Role.ATTENDANT && !a.blocked).map(a -> Map.of("account", a, "orders", active.stream().filter(o -> o.attendantIds.contains(a.id)).map(o -> o.id).toList())).toList();
        double avg = selected.stream().filter(o -> o.serviceStartedAt != null && o.completedAt != null).mapToLong(o -> Duration.between(o.serviceStartedAt, o.completedAt).getSeconds()).average().orElse(0) / 60.0;
        return Map.of("rooms", roomState, "attendants", staffState, "queue", queue(), "orderCount", selected.size(), "averageMinutes", avg,
                "revenue", paid.stream().map(p -> p.amount).reduce(BigDecimal.ZERO, BigDecimal::add), "empty", selected.isEmpty(), "updatedAt", Instant.now());
    }

    public List<ReportTemplate> reportTemplates() {
        var a = current();
        return reports.findAll().stream().filter(t -> t.ownerId.equals(a.id)).toList();
    }

    @Transactional
    public ReportTemplate saveReport(ReportInput input) {
        period(input.parameters());
        var t = new ReportTemplate();
        t.ownerId = current().id;
        t.name = input.name();
        try {
            t.parameters = json.writeValueAsString(input.parameters());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return reports.save(t);
    }
}
