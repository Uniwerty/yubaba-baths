package ru.yubaba.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.yubaba.controller.dto.*;
import ru.yubaba.data.enums.OrderStatus;
import ru.yubaba.data.entity.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import ru.yubaba.service.BathService;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final BathService service;

    public ApiController(BathService service) {
        this.service = service;
    }

    @GetMapping("/catalog")
    public CatalogResponse getCatalog() {
        return service.getCatalog();
    }

    @GetMapping("/clients")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<Client> getClients(@RequestParam(defaultValue = "") String q) {
        return service.getClients(q);
    }

    @PostMapping("/clients")
    @PreAuthorize("hasRole('ADMIN')")
    public Client saveClient(@RequestBody ClientInput input) {
        return service.saveClient(null, input);
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Client editClient(@PathVariable Long id, @RequestBody ClientInput input) {
        return service.saveClient(id, input);
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ServiceTemplate saveTemplate(@RequestBody TemplateInput input) {
        return service.saveTemplate(null, input);
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ServiceTemplate editTemplate(@PathVariable Long id, @RequestBody TemplateInput input) {
        return service.saveTemplate(id, input);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','ATTENDANT','ACCOUNTANT')")
    public OrderPageResponse getOrders(@RequestParam(defaultValue = "") String q,
                                       @RequestParam(required = false) OrderStatus status,
                                       @RequestParam(defaultValue = "0") int page) {
        return service.getOrderPage(q, status, page);
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','ATTENDANT','ACCOUNTANT','BOILER')")
    public BathOrder getOrder(@PathVariable Long id) {
        return service.getOrder(id);
    }

    @GetMapping("/orders/{id}/audit")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<AuditEvent> getOrderAudit(@PathVariable Long id) {
        return service.getOrderAudit(id);
    }

    @PostMapping("/orders/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderPreviewResponse previewOrder(@RequestBody OrderInput input) {
        return service.buildPreview(input);
    }

    @PostMapping("/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public BathOrder saveOrder(@RequestBody OrderInput input) {
        return service.saveOrder(null, input);
    }

    @PutMapping("/orders/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BathOrder updateOrder(@PathVariable Long id, @RequestBody OrderInput input) {
        return service.saveOrder(id, input);
    }

    @PostMapping("/orders/{id}/launch")
    @PreAuthorize("hasRole('ADMIN')")
    public BathOrder launchOrder(@PathVariable Long id, @RequestBody VersionInput input) {
        return service.launchOrder(id, input.version());
    }

    @PostMapping("/orders/{id}/water-ready")
    @PreAuthorize("hasRole('BOILER')")
    public BathOrder setWaterReady(@PathVariable Long id, @RequestBody VersionInput input) {
        return service.setWaterReady(id, input.version());
    }

    @PostMapping("/orders/{id}/start")
    @PreAuthorize("hasRole('ATTENDANT')")
    public BathOrder startService(@PathVariable Long id, @RequestBody VersionInput input) {
        return service.startService(id, input.version());
    }

    @PostMapping("/orders/{id}/complete")
    @PreAuthorize("hasRole('ATTENDANT')")
    public BathOrder completeService(@PathVariable Long id, @RequestBody VersionInput input) {
        return service.completeService(id, input.version());
    }

    @PostMapping("/orders/{id}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    public BathOrder payOrder(@PathVariable Long id, @RequestBody PayInput input) {
        return service.payOrder(id, input);
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public BathOrder cancelOrder(@PathVariable Long id, @RequestBody CancelInput input) {
        return service.cancelOrder(id, input);
    }

    @GetMapping("/boiler")
    @PreAuthorize("hasAnyRole('BOILER','MANAGER')")
    public List<BathOrder> getOrderQueue() {
        return service.getOrderQueue();
    }

    @GetMapping("/schedule")
    @PreAuthorize("hasRole('ATTENDANT')")
    public ScheduleResponse getSchedule() {
        return service.getSchedule();
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasRole('MANAGER')")
    public List<Account> getAccounts() {
        return service.getAccounts();
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasRole('MANAGER')")
    public Account saveAccount(@RequestBody AccountInput input) {
        return service.saveAccount(input);
    }

    @PatchMapping("/accounts/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public Account blockAccount(@PathVariable Long id, @RequestBody BlockInput input) {
        return service.blockAccount(id, input.blocked());
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasRole('MANAGER')")
    public Room saveRoom(@RequestBody RoomInput input) {
        return service.saveRoom(input);
    }

    @PostMapping("/ingredients")
    @PreAuthorize("hasRole('MANAGER')")
    public Ingredient saveIngredient(@RequestBody IngredientInput input) {
        return service.saveIngredient(input);
    }

    @GetMapping("/supplies")
    @PreAuthorize("hasRole('MANAGER')")
    public List<SupplyRequest> getSupplyRequests() {
        return service.getSupplyRequests();
    }

    @PostMapping("/supplies/{id}/receive")
    @PreAuthorize("hasRole('MANAGER')")
    public SupplyRequest receiveSupply(@PathVariable Long id) {
        return service.receiveSupply(id);
    }

    @GetMapping("/supplies/{id}/export")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<String> supplyExport(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=supply-" + id + ".txt")
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(service.supplyExport(id));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ReportResponse getReport(@ModelAttribute ReportFilter filter) {
        return service.getReport(filter);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('MANAGER')")
    public DashboardResponse getDashboard(@ModelAttribute ReportFilter filter) {
        return service.getDashboard(filter);
    }

    @GetMapping("/report-templates")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public List<ReportTemplate> getReportTemplates() {
        return service.getReportTemplates();
    }

    @PostMapping("/report-templates")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ReportTemplate saveReportTemplate(@RequestBody ReportInput input) {
        return service.saveReportTemplate(input);
    }
}
