package ru.yubaba.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.yubaba.controller.Requests.*;
import ru.yubaba.data.enums.OrderStatus;
import ru.yubaba.service.BathService;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final BathService service;

    public ApiController(BathService service) {
        this.service = service;
    }

    @GetMapping("/catalog")
    public Object catalog() {
        return service.catalog();
    }

    @GetMapping("/clients")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Object clients(@RequestParam(defaultValue = "") String q) {
        return service.clients(q);
    }

    @PostMapping("/clients")
    @PreAuthorize("hasRole('ADMIN')")
    public Object addClient(@Valid @RequestBody ClientInput input) {
        return service.saveClient(null, input);
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Object editClient(@PathVariable Long id, @Valid @RequestBody ClientInput input) {
        return service.saveClient(id, input);
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Object addTemplate(@Valid @RequestBody TemplateInput input) {
        return service.saveTemplate(null, input);
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Object editTemplate(@PathVariable Long id, @Valid @RequestBody TemplateInput input) {
        return service.saveTemplate(id, input);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','ATTENDANT','ACCOUNTANT')")
    public Object orders(@RequestParam(defaultValue = "") String q,
                         @RequestParam(required = false) OrderStatus status,
                         @RequestParam(defaultValue = "0") int page) {
        return service.orderPage(q, status, page);
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','ATTENDANT','ACCOUNTANT','BOILER')")
    public Object order(@PathVariable Long id) {
        return service.order(id);
    }

    @GetMapping("/orders/{id}/audit")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Object audit(@PathVariable Long id) {
        return service.audit(id);
    }

    @PostMapping("/orders/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public Object preview(@Valid @RequestBody OrderInput input) {
        return service.preview(input);
    }

    @PostMapping("/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public Object create(@Valid @RequestBody OrderInput input) {
        return service.saveOrder(null, input);
    }

    @PutMapping("/orders/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Object edit(@PathVariable Long id, @Valid @RequestBody OrderInput input) {
        return service.saveOrder(id, input);
    }

    @PostMapping("/orders/{id}/launch")
    @PreAuthorize("hasRole('ADMIN')")
    public Object launch(@PathVariable Long id, @Valid @RequestBody VersionInput input) {
        return service.launch(id, input.version());
    }

    @PostMapping("/orders/{id}/water-ready")
    @PreAuthorize("hasRole('BOILER')")
    public Object water(@PathVariable Long id, @Valid @RequestBody VersionInput input) {
        return service.waterReady(id, input.version());
    }

    @PostMapping("/orders/{id}/start")
    @PreAuthorize("hasRole('ATTENDANT')")
    public Object start(@PathVariable Long id, @Valid @RequestBody VersionInput input) {
        return service.startService(id, input.version());
    }

    @PostMapping("/orders/{id}/complete")
    @PreAuthorize("hasRole('ATTENDANT')")
    public Object complete(@PathVariable Long id, @Valid @RequestBody VersionInput input) {
        return service.complete(id, input.version());
    }

    @PostMapping("/orders/{id}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    public Object pay(@PathVariable Long id, @Valid @RequestBody PayInput input) {
        return service.pay(id, input);
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Object cancel(@PathVariable Long id, @Valid @RequestBody CancelInput input) {
        return service.cancel(id, input);
    }

    @GetMapping("/boiler")
    @PreAuthorize("hasAnyRole('BOILER','MANAGER')")
    public Object queue() {
        return service.queue();
    }

    @GetMapping("/schedule")
    @PreAuthorize("hasRole('ATTENDANT')")
    public Object schedule() {
        return service.schedule();
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasRole('MANAGER')")
    public Object accounts() {
        return service.accounts();
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasRole('MANAGER')")
    public Object addAccount(@Valid @RequestBody AccountInput input) {
        return service.addAccount(input);
    }

    @PatchMapping("/accounts/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public Object block(@PathVariable Long id, @RequestBody BlockInput input) {
        return service.block(id, input.blocked());
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasRole('MANAGER')")
    public Object room(@Valid @RequestBody RoomInput input) {
        return service.addRoom(input);
    }

    @PostMapping("/ingredients")
    @PreAuthorize("hasRole('MANAGER')")
    public Object ingredient(@Valid @RequestBody IngredientInput input) {
        return service.addIngredient(input);
    }

    @GetMapping("/supplies")
    @PreAuthorize("hasRole('MANAGER')")
    public Object supplies() {
        return service.supplyRequests();
    }

    @PostMapping("/supplies/{id}/receive")
    @PreAuthorize("hasRole('MANAGER')")
    public Object receive(@PathVariable Long id) {
        return service.receive(id);
    }

    @GetMapping("/supplies/{id}/export")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<String> export(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=supply-" + id + ".txt")
                .contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .body(service.supplyExport(id));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public Object report(@Valid @ModelAttribute ReportFilter filter) {
        return service.report(filter);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('MANAGER')")
    public Object dashboard(@Valid @ModelAttribute ReportFilter filter) {
        return service.dashboard(filter);
    }

    @GetMapping("/report-templates")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public Object reportTemplates() {
        return service.reportTemplates();
    }

    @PostMapping("/report-templates")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public Object saveReport(@Valid @RequestBody ReportInput input) {
        return service.saveReport(input);
    }
}
