package ru.yubaba.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yubaba.controller.dto.AttendantStateResponse;
import ru.yubaba.controller.dto.DashboardResponse;
import ru.yubaba.controller.dto.ReportFilter;
import ru.yubaba.controller.dto.ReportInput;
import ru.yubaba.controller.dto.ReportResponse;
import ru.yubaba.controller.dto.RoomStateResponse;
import ru.yubaba.data.entity.Payment;
import ru.yubaba.data.entity.ReportTemplate;
import ru.yubaba.data.enums.OrderStatus;
import ru.yubaba.data.enums.Role;
import ru.yubaba.data.repository.AccountRepository;
import ru.yubaba.data.repository.BathOrderRepository;
import ru.yubaba.data.repository.PaymentRepository;
import ru.yubaba.data.repository.ReportTemplateRepository;
import ru.yubaba.data.repository.RoomRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import static ru.yubaba.service.ServiceChecks.*;

@Service
@Transactional(readOnly = true)
public class ReportService {
    private final AccountRepository accountRepository;
    private final RoomRepository roomRepository;
    private final BathOrderRepository bathOrderRepository;
    private final PaymentRepository paymentRepository;
    private final ReportTemplateRepository reportTemplateRepository;
    private final ObjectMapper objectMapper;
    private final CurrentAccountService currentAccountService;
    private final ResourceAvailabilityService resourceAvailabilityService;

    public ReportService(
            AccountRepository accountRepository,
            RoomRepository roomRepository,
            BathOrderRepository bathOrderRepository,
            PaymentRepository paymentRepository,
            ReportTemplateRepository reportTemplateRepository,
            ObjectMapper objectMapper,
            CurrentAccountService currentAccountService,
            ResourceAvailabilityService resourceAvailabilityService
    ) {
        this.accountRepository = accountRepository;
        this.roomRepository = roomRepository;
        this.bathOrderRepository = bathOrderRepository;
        this.paymentRepository = paymentRepository;
        this.reportTemplateRepository = reportTemplateRepository;
        this.objectMapper = objectMapper;
        this.currentAccountService = currentAccountService;
        this.resourceAvailabilityService = resourceAvailabilityService;
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
        var activeOrders = resourceAvailabilityService.getActiveOrders();
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
                resourceAvailabilityService.getOrderQueue(),
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
        var account = currentAccountService.getCurrent();
        return reportTemplateRepository.findAll().stream()
                .filter(t -> t.ownerId.equals(account.id))
                .toList();
    }

    @Transactional
    public ReportTemplate saveReportTemplate(ReportInput input) {
        buildReportPeriod(input.parameters());
        var template = new ReportTemplate();
        template.ownerId = currentAccountService.getCurrent().id;
        template.name = input.name();
        try {
            template.parameters = objectMapper.writeValueAsString(input.parameters());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return reportTemplateRepository.save(template);
    }
}
