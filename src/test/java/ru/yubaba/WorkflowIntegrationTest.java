package ru.yubaba;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        String db = jdbc.queryForObject("select current_database()", String.class);
        assertTrue(db.endsWith("_test"), "Use a dedicated _test database");
        jdbc.execute("TRUNCATE order_attendants,order_lines,audit_events,payments,bath_orders,report_templates,supply_requests RESTART IDENTITY CASCADE");
        jdbc.update("UPDATE accounts SET blocked=false,rest_until=null,active_order_id=null");
        jdbc.update("UPDATE ingredients SET stock=10000,reserved=0,threshold=100");
    }

    String role(String login) {
        return switch (login) {
            case "haku" -> "MANAGER";
            case "lin" -> "ADMIN";
            case "kamaji" -> "BOILER";
            case "zeniba" -> "ACCOUNTANT";
            default -> "ATTENDANT";
        };
    }

    MvcResult call(String login, String method, String path, Object body) throws Exception {
        MockHttpServletRequestBuilder b = switch (method) {
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "PATCH" -> patch(path);
            default -> get(path);
        };
        if (login != null) b.with(user(login).roles(role(login)));
        if (body != null) b.contentType("application/json").content(json.writeValueAsBytes(body));
        return mvc.perform(b).andReturn();
    }

    JsonNode ok(String login, String method, String path, Object body) throws Exception {
        var r = call(login, method, path, body);
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return json.readTree(r.getResponse().getContentAsString());
    }

    ObjectNode input() throws Exception {
        var cat = ok("lin", "GET", "/api/catalog", null);
        var t = cat.get("templates").get(0);
        var c = (ObjectNode) t.deepCopy();
        c.remove(List.of("id", "version"));
        for (var l : c.withArray("lines")) ((ObjectNode) l).remove(List.of("name", "unit", "unitPrice"));
        c.put("breakMinutes", 0);
        var in = json.createObjectNode();
        in.put("clientId", jdbc.queryForObject("select min(id) from clients", Long.class));
        in.put("templateId", t.get("id").asLong());
        in.put("visitors", 1);
        in.put("priority", 0);
        in.set("composition", c);
        return in;
    }

    JsonNode create() throws Exception {
        return ok("lin", "POST", "/api/orders", input());
    }

    JsonNode step(String actor, JsonNode order, String action) throws Exception {
        return ok(actor, "POST", "/api/orders/" + order.get("id").asLong() + "/" + action, Map.of("version", order.get("version").asLong()));
    }

    @Test
    void completeLifecycleAndAudit() throws Exception {
        var o = create();
        assertEquals("CREATED", o.path("status").asText());
        assertEquals(1715, o.path("total").asDouble());
        o = step("lin", o, "launch");
        assertEquals(1, ok("kamaji", "GET", "/api/boiler", null).size());
        assertEquals(409, call("chihiro", "POST", "/api/orders/" + o.get("id") + "/start", Map.of("version", o.get("version").asLong())).getResponse().getStatus());
        o = step("kamaji", o, "water-ready");
        assertEquals(9950, jdbc.queryForObject("select stock from ingredients order by id limit 1", Double.class));
        o = step("chihiro", o, "start");
        o = step("chihiro", o, "complete");
        assertEquals("AWAITING_PAYMENT", o.path("status").asText());
        assertEquals(409, call("lin", "POST", "/api/orders/" + o.get("id") + "/pay", Map.of("version", o.get("version").asLong())).getResponse().getStatus());
        o = ok("lin", "POST", "/api/orders/" + o.get("id") + "/pay", Map.of("version", o.get("version").asLong(), "method", "CARD"));
        assertEquals("CLOSED", o.path("status").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from payments", Integer.class));
        assertEquals(6, ok("lin", "GET", "/api/orders/" + o.get("id") + "/audit", null).size());
        assertEquals(0, ok("kamaji", "GET", "/api/boiler", null).size());
        assertEquals(1715, ok("haku", "GET", "/api/dashboard", null).path("revenue").asDouble());
    }

    @Test
    void cancelReleasesReservationsButDoesNotReturnConsumedStock() throws Exception {
        var o = step("lin", create(), "launch");
        assertTrue(jdbc.queryForObject("select sum(reserved) from ingredients", Double.class) > 0);
        ok("lin", "POST", "/api/orders/" + o.get("id") + "/cancel", Map.of("version", o.get("version").asLong(), "reason", "Гость передумал"));
        assertEquals(0, jdbc.queryForObject("select sum(reserved) from ingredients", Double.class));
        assertEquals(10000, jdbc.queryForObject("select stock from ingredients order by id limit 1", Double.class));
        o = step("kamaji", step("lin", create(), "launch"), "water-ready");
        ok("haku", "POST", "/api/orders/" + o.get("id") + "/cancel", Map.of("version", o.get("version").asLong(), "reason", "Отмена после подготовки"));
        assertEquals(9950, jdbc.queryForObject("select stock from ingredients order by id limit 1", Double.class));
    }

    @Test
    void validatesCompositionShortageAndStaleVersion() throws Exception {
        var in = input();
        ((ObjectNode) in.get("composition")).put("attendants", 3);
        assertEquals(409, call("lin", "POST", "/api/orders", in).getResponse().getStatus());
        assertEquals(0, jdbc.queryForObject("select count(*) from bath_orders", Integer.class));
        in = input();
        ((ObjectNode) in.get("composition").get("lines").get(0)).put("quantity", 20000);
        assertEquals(409, call("lin", "POST", "/api/orders", in).getResponse().getStatus());
        var o = create();
        in = input();
        in.put("version", o.get("version").asLong());
        in.put("visitors", 2);
        var updated = ok("lin", "PUT", "/api/orders/" + o.get("id"), in);
        assertEquals(2, updated.path("visitors").asInt());
        assertEquals(409, call("lin", "POST", "/api/orders/" + o.get("id") + "/launch", Map.of("version", o.get("version").asLong())).getResponse().getStatus());
    }

    @Test
    void concurrentLaunchCannotDoubleBook() throws Exception {
        var first = create();
        var second = create();
        var gate = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (var o : List.of(first, second))
                results.add(pool.submit(() -> {
                    gate.await();
                    return call("lin", "POST", "/api/orders/" + o.get("id") + "/launch", Map.of("version", o.get("version").asLong())).getResponse().getStatus();
                }));
            gate.countDown();
            var codes = new ArrayList<Integer>();
            for (var result : results) codes.add(result.get(10, TimeUnit.SECONDS));
            Collections.sort(codes);
            assertEquals(List.of(200, 409), codes);
            assertEquals(1, jdbc.queryForObject("select count(*) from bath_orders where status='IN_SERVICE'", Integer.class));
            assertEquals(50, jdbc.queryForObject("select reserved from ingredients order by id limit 1", Double.class));
            long waiting = jdbc.queryForObject("select id from bath_orders where status='CREATED'", Long.class);
            var waitingOrder = ok("lin", "GET", "/api/orders/" + waiting, null);
            var replacement = input();
            replacement.put("version", waitingOrder.path("version").asLong());
            ((ObjectNode) replacement.get("composition")).putNull("preferredRoomId");
            var edited = ok("lin", "PUT", "/api/orders/" + waiting, replacement);
            step("lin", edited, "launch");
            assertEquals(2, jdbc.queryForObject("select count(distinct room_id) from bath_orders where status='IN_SERVICE'", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentOrdersInDifferentRoomsCannotShareAttendant() throws Exception {
        jdbc.update("UPDATE accounts SET blocked=true WHERE login='rin'");
        var firstInput = input();
        ((ObjectNode) firstInput.get("composition")).putArray("lines");
        var secondInput = firstInput.deepCopy();
        long firstRoom = firstInput.path("composition").path("preferredRoomId").asLong();
        String bathType = firstInput.path("composition").path("bathType").asText();
        long secondRoom = jdbc.queryForObject(
                "select id from rooms where id<>? and bath_type=? order by id limit 1", Long.class, firstRoom, bathType);
        ((ObjectNode) secondInput.get("composition")).put("preferredRoomId", secondRoom);
        var first = ok("lin", "POST", "/api/orders", firstInput);
        var second = ok("lin", "POST", "/api/orders", secondInput);
        var gate = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (var order : List.of(first, second)) {
                results.add(pool.submit(() -> {
                    gate.await();
                    return call("lin", "POST", "/api/orders/" + order.path("id").asLong() + "/launch",
                            Map.of("version", order.path("version").asLong())).getResponse().getStatus();
                }));
            }
            gate.countDown();
            var codes = new ArrayList<Integer>();
            for (var result : results) codes.add(result.get(10, TimeUnit.SECONDS));
            Collections.sort(codes);
            assertEquals(List.of(200, 409), codes);
            assertEquals(1, jdbc.queryForObject("select count(*) from bath_orders where status='IN_SERVICE'", Integer.class));
            assertEquals(1, jdbc.queryForObject("select count(*) from accounts where active_order_id is not null", Integer.class));
            assertEquals(1, jdbc.queryForObject("select count(*) from audit_events where action='LAUNCHED'", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentPaymentIsRecordedExactlyOnce() throws Exception {
        var o = step("chihiro", step("chihiro", step("kamaji", step("lin", create(), "launch"), "water-ready"), "start"), "complete");
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            List<Future<Integer>> rs = new ArrayList<>();
            for (int i = 0; i < 2; i++)
                rs.add(pool.submit(() -> {
                    gate.await();
                    return call("lin", "POST", "/api/orders/" + o.get("id") + "/pay", Map.of("version", o.get("version").asLong(), "method", "CASH")).getResponse().getStatus();
                }));
            gate.countDown();
            var codes = new ArrayList<Integer>();
            for (var r : rs) codes.add(r.get(10, TimeUnit.SECONDS));
            Collections.sort(codes);
            assertEquals(List.of(200, 409), codes);
            assertEquals(1, jdbc.queryForObject("select count(*) from payments", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rolesAndAssignedAttendantAreEnforced() throws Exception {
        assertEquals(401, call(null, "GET", "/api/catalog", null).getResponse().getStatus());
        assertEquals(403, call("kamaji", "GET", "/api/clients", null).getResponse().getStatus());
        assertEquals(403, call("lin", "GET", "/api/accounts", null).getResponse().getStatus());
        assertEquals(403, call("haku", "POST", "/api/orders", input()).getResponse().getStatus());
        var o = step("lin", create(), "launch");
        assertEquals(403, call("rin", "GET", "/api/orders/" + o.get("id"), null).getResponse().getStatus());
        assertEquals(0, ok("rin", "GET", "/api/orders", null).path("total").asInt());
        assertEquals(1, ok("chihiro", "GET", "/api/schedule", null).path("active").size());
    }

    @Test
    void reportFiltersAndSavedConfiguration() throws Exception {
        var o = step("chihiro", step("chihiro", step("kamaji", step("lin", create(), "launch"), "water-ready"), "start"), "complete");
        ok("lin", "POST", "/api/orders/" + o.get("id") + "/pay", Map.of("version", o.get("version").asLong(), "method", "TRANSFER"));
        var r = ok("zeniba", "GET", "/api/reports?method=TRANSFER&visitors=1", null);
        assertEquals(1, r.path("orderCount").asInt());
        assertEquals(1715, r.path("revenue").asDouble());
        assertTrue(ok("zeniba", "GET", "/api/reports?method=CARD", null).path("empty").asBoolean());
        var f = Map.of("from", LocalDate.now().toString(), "to", LocalDate.now().toString(), "method", "TRANSFER");
        var t = ok("zeniba", "POST", "/api/report-templates", Map.of("name", "Переводы", "parameters", f));
        assertTrue(t.path("parameters").asText().contains("TRANSFER"));
        assertEquals(1, ok("zeniba", "GET", "/api/report-templates", null).size());
        assertTrue(ok("haku", "GET", "/api/dashboard?from=2020-01-01&to=2020-01-02", null).path("empty").asBoolean());
    }

    @Test
    void automaticSupplyAndSingleReceipt() throws Exception {
        jdbc.update("UPDATE ingredients SET stock=100,threshold=100 WHERE id=(select min(id) from ingredients)");
        var ss = ok("haku", "GET", "/api/supplies", null);
        assertEquals(1, ss.size());
        assertEquals(1, ok("haku", "GET", "/api/supplies", null).size());
        var id = ss.get(0).get("id");
        var response = call("haku", "GET", "/api/supplies/" + id + "/export", null);
        assertEquals(200, response.getResponse().getStatus());
        assertTrue(response.getResponse().getContentAsString().contains("ЗАЯВКА"));
        ok("haku", "POST", "/api/supplies/" + id + "/receive", null);
        assertEquals(409, call("haku", "POST", "/api/supplies/" + id + "/receive", null).getResponse().getStatus());
        assertEquals(200, jdbc.queryForObject("select stock from ingredients order by id limit 1", Double.class));
    }

    @Test
    void jwtLoginAndImmediateBlocking() throws Exception {
        var response = call(null, "POST", "/api/auth/login", Map.of("login", "rin", "password", "Test-password-2026"));
        assertEquals(200, response.getResponse().getStatus());
        var token = json.readTree(response.getResponse().getContentAsString()).path("token").asText();
        assertEquals(200, mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus());
        var id = jdbc.queryForObject("select id from accounts where login='rin'", Long.class);
        ok("haku", "PATCH", "/api/accounts/" + id, Map.of("blocked", true));
        assertEquals(401, mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus());
        assertEquals(401, call(null, "POST", "/api/auth/login", Map.of("login", "rin", "password", "Test-password-2026")).getResponse().getStatus());
    }

    @Test
    void clientTemplateAndResourcesCanBeCreatedAndEdited() throws Exception {
        String unique = UUID.randomUUID().toString();
        var c = ok("lin", "POST", "/api/clients", Map.of("name", "Новый гость", "contact", unique, "notes", ""));
        ok("lin", "PUT", "/api/clients/" + c.get("id"), Map.of("name", "Уточнённый гость", "contact", unique, "notes", "Чай", "version", c.get("version").asLong()));
        assertEquals(1, ok("lin", "GET", "/api/clients?q=" + unique, null).size());
        var composition = input().get("composition");
        var t = ok("haku", "POST", "/api/templates", Map.of("composition", composition));
        ok("haku", "PUT", "/api/templates/" + t.get("id"), Map.of("composition", composition, "version", t.get("version").asLong()));
        var o = input();
        o.put("clientId", c.path("id").asLong());
        o.put("templateId", t.path("id").asLong());
        assertEquals(200, call("lin", "POST", "/api/orders", o).getResponse().getStatus());
    }
}
