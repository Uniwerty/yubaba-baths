package ru.yubaba.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.yubaba.controller.dto.*;
import ru.yubaba.data.enums.OrderStatus;
import ru.yubaba.data.entity.*;

import ru.yubaba.service.ClientService;
import ru.yubaba.service.CatalogService;
import ru.yubaba.service.ResourceAvailabilityService;
import ru.yubaba.service.InventoryService;
import ru.yubaba.service.AccountService;
import ru.yubaba.service.ReportService;
import ru.yubaba.service.OrderService;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final ClientService clientService;
    private final CatalogService catalogService;
    private final ResourceAvailabilityService resourceAvailabilityService;
    private final InventoryService inventoryService;
    private final AccountService accountService;
    private final ReportService reportService;
    private final OrderService orderService;

    public ApiController(
            ClientService clientService,
            CatalogService catalogService,
            ResourceAvailabilityService resourceAvailabilityService,
            InventoryService inventoryService,
            AccountService accountService,
            ReportService reportService,
            OrderService orderService
    ) {
        this.clientService = clientService;
        this.catalogService = catalogService;
        this.resourceAvailabilityService = resourceAvailabilityService;
        this.inventoryService = inventoryService;
        this.accountService = accountService;
        this.reportService = reportService;
        this.orderService = orderService;
    }

    @GetMapping("/catalog")
    public CatalogResponse getCatalog() {
        return catalogService.getCatalog();
    }

    @GetMapping("/clients")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<Client> getClients(@RequestParam(defaultValue = "") String q) {
        return clientService.getClients(q);
    }

    @PostMapping("/clients")
    @PreAuthorize("hasRole('ADMIN')")
    public Client saveClient(@RequestBody ClientInput input) {
        return clientService.saveClient(null, input);
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Client editClient(@PathVariable Long id, @RequestBody ClientInput input) {
        return clientService.saveClient(id, input);
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ServiceTemplate saveTemplate(@RequestBody TemplateInput input) {
        return catalogService.saveTemplate(null, input);
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ServiceTemplate editTemplate(@PathVariable Long id, @RequestBody TemplateInput input) {
        return catalogService.saveTemplate(id, input);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','ATTENDANT','ACCOUNTANT')")
    public OrderPageResponse getOrders(@RequestParam(defaultValue = "") String q,
                                       @RequestParam(required = false) OrderStatus status,
                                       @RequestParam(defaultValue = "0") int page) {
        return orderService.getOrderPage(q, status, page);
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','ATTENDANT','ACCOUNTANT','BOILER')")
    public BathOrder getOrder(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    @GetMapping("/orders/{id}/audit")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<AuditEvent> getOrderAudit(@PathVariable Long id) {
        return orderService.getOrderAudit(id);
    }

    @PostMapping("/orders/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderPreviewResponse previewOrder(@RequestBody OrderInput input) {
        return orderService.buildPreview(input);
    }

    @PostMapping("/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public BathOrder saveOrder(@RequestBody OrderInput input) {
        return orderService.saveOrder(null, input);
    }

    @PutMapping("/orders/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BathOrder updateOrder(@PathVariable Long id, @RequestBody OrderInput input) {
        return orderService.saveOrder(id, input);
    }

    @PostMapping("/orders/{id}/launch")
    @PreAuthorize("hasRole('ADMIN')")
    public BathOrder launchOrder(@PathVariable Long id, @RequestBody VersionInput input) {
        return orderService.launchOrder(id, input.version());
    }

    @PostMapping("/orders/{id}/water-ready")
    @PreAuthorize("hasRole('BOILER')")
    public BathOrder setWaterReady(@PathVariable Long id, @RequestBody VersionInput input) {
        return orderService.setWaterReady(id, input.version());
    }

    @PostMapping("/orders/{id}/start")
    @PreAuthorize("hasRole('ATTENDANT')")
    public BathOrder startService(@PathVariable Long id, @RequestBody VersionInput input) {
        return orderService.startService(id, input.version());
    }

    @PostMapping("/orders/{id}/complete")
    @PreAuthorize("hasRole('ATTENDANT')")
    public BathOrder completeService(@PathVariable Long id, @RequestBody VersionInput input) {
        return orderService.completeService(id, input.version());
    }

    @PostMapping("/orders/{id}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    public BathOrder payOrder(@PathVariable Long id, @RequestBody PayInput input) {
        return orderService.payOrder(id, input);
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public BathOrder cancelOrder(@PathVariable Long id, @RequestBody CancelInput input) {
        return orderService.cancelOrder(id, input);
    }

    @GetMapping("/boiler")
    @PreAuthorize("hasAnyRole('BOILER','MANAGER')")
    public List<BathOrder> getOrderQueue() {
        return resourceAvailabilityService.getOrderQueue();
    }

    @GetMapping("/schedule")
    @PreAuthorize("hasRole('ATTENDANT')")
    public ScheduleResponse getSchedule() {
        return orderService.getSchedule();
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasRole('MANAGER')")
    public List<Account> getAccounts() {
        return accountService.getAccounts();
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasRole('MANAGER')")
    public Account saveAccount(@RequestBody AccountInput input) {
        return accountService.saveAccount(input);
    }

    @PatchMapping("/accounts/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public Account blockAccount(@PathVariable Long id, @RequestBody BlockInput input) {
        return accountService.blockAccount(id, input.blocked());
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasRole('MANAGER')")
    public Room saveRoom(@RequestBody RoomInput input) {
        return catalogService.saveRoom(input);
    }

    @PostMapping("/ingredients")
    @PreAuthorize("hasRole('MANAGER')")
    public Ingredient saveIngredient(@RequestBody IngredientInput input) {
        return inventoryService.saveIngredient(input);
    }

    @GetMapping("/supplies")
    @PreAuthorize("hasRole('MANAGER')")
    public List<SupplyRequest> getSupplyRequests() {
        return inventoryService.getSupplyRequests();
    }

    @PostMapping("/supplies/{id}/receive")
    @PreAuthorize("hasRole('MANAGER')")
    public SupplyRequest receiveSupply(@PathVariable Long id) {
        return inventoryService.receiveSupply(id);
    }

    @GetMapping("/supplies/{id}/export")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<String> supplyExport(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=supply-" + id + ".txt")
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(inventoryService.supplyExport(id));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ReportResponse getReport(@ModelAttribute ReportFilter filter) {
        return reportService.getReport(filter);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('MANAGER')")
    public DashboardResponse getDashboard(@ModelAttribute ReportFilter filter) {
        return reportService.getDashboard(filter);
    }

    @GetMapping("/report-templates")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public List<ReportTemplate> getReportTemplates() {
        return reportService.getReportTemplates();
    }

    @PostMapping("/report-templates")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ReportTemplate saveReportTemplate(@RequestBody ReportInput input) {
        return reportService.saveReportTemplate(input);
    }
}
