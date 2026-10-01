// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP验收用料、顺序守恒、独立质检、回滚、幂等与权限隔离。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductionIntegrationTest {
  static final String PASSWORD = "Test" + UUID.randomUUID() + "Aa9";

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:forgeflow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("forgeflow.admin-password", () -> PASSWORD);
    r.add("forgeflow.seed-demo", () -> false);
  }

  @Autowired MockMvc mvc;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession session, operator;
  long material, product, station, bom, order, op;
  String suffix;

  @BeforeEach
  void setup() throws Exception {
    session = login("admin", PASSWORD);
    suffix = UUID.randomUUID().toString().substring(0, 8);
    material = ok("/master/items", item("M" + suffix, "MATERIAL", 1)).get("id").asLong();
    product = ok("/master/items", item("F" + suffix, "FINISHED", 1)).get("id").asLong();
    station =
        ok(
                "/master/stations",
                Map.of(
                    "code",
                    "S" + suffix,
                    "name",
                    "Cutting",
                    "departmentId",
                    1,
                    "enabled",
                    true,
                    "hourlyRate",
                    "60"))
            .get("id")
            .asLong();
    bom = ok("/boms", bomInput(1)).get("id").asLong();
    ok("/boms/" + bom + "/activate", Map.of("revision", 0));
    order = ok("/orders", plan()).get("id").asLong();
    op = user("op" + suffix, "操作员 / Operator", 1);
    operator = login("op" + suffix, PASSWORD);
  }

  Map<String, Object> item(String code, String kind, long dep) {
    return Map.of(
        "code",
        code,
        "name",
        code,
        "kind",
        kind,
        "unit",
        kind.equals("MATERIAL") ? "kg" : "pc",
        "departmentId",
        dep,
        "enabled",
        true,
        "reorderLevel",
        0);
  }

  Map<String, Object> bomInput(int version) {
    return Map.of(
        "productId",
        product,
        "versionNumber",
        version,
        "description",
        "Actual routing",
        "components",
        List.of(Map.of("itemId", material, "perUnit", "0.012390")),
        "operations",
        List.of(
            Map.of("stationId", station, "name", "Cut"),
            Map.of("stationId", station, "name", "Assemble")));
  }

  Map<String, Object> plan() {
    return Map.of(
        "bomId",
        bom,
        "plannedQuantity",
        10,
        "dueDate",
        "2026-10-30",
        "sourceReference",
        "EXT-" + suffix,
        "requestKey",
        UUID.randomUUID().toString());
  }

  MockHttpSession login(String user, String password) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(Map.of("username", user, "password", password))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  JsonNode sendAs(MockHttpSession s, String path, Object body, int status) throws Exception {
    var r =
        mvc.perform(
                post("/api" + path)
                    .session(s)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andReturn();
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  JsonNode ok(String path, Object body) throws Exception {
    return sendAs(session, path, body, 200);
  }

  JsonNode readAs(MockHttpSession s, String path, int status) throws Exception {
    var r = mvc.perform(get("/api" + path).session(s)).andReturn();
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  JsonNode read(String path) throws Exception {
    return readAs(session, path, 200);
  }

  JsonNode detail() throws Exception {
    return read("/orders/" + order);
  }

  long user(String name, String role, long department) throws Exception {
    long rid = 0;
    for (var r : read("/lists/roles?size=100").get("items"))
      if (r.get("name").asString().equals(role)) rid = r.get("id").asLong();
    return ok(
            "/admin/users",
            Map.of(
                "username",
                name,
                "displayName",
                name,
                "password",
                PASSWORD,
                "roleId",
                rid,
                "departmentId",
                department,
                "enabled",
                true))
        .get("id")
        .asLong();
  }

  Map<String, Object> move(String qty) {
    return new HashMap<>(
        Map.of(
            "quantity",
            qty,
            "reference",
            "Warehouse receipt",
            "requestKey",
            UUID.randomUUID().toString()));
  }

  void act(String action, Map<String, Object> input, int status) throws Exception {
    var v = new HashMap<>(input);
    v.put("revision", detail().get("order").get("revision").asLong());
    v.put("requestKey", UUID.randomUUID().toString());
    sendAs(session, "/orders/" + order + "/" + action, v, status);
  }

  void release() throws Exception {
    act("release", Map.of(), 200);
    for (var s : detail().get("steps"))
      act("assign", Map.of("stepId", s.get("id").asLong(), "operatorId", op), 200);
  }

  long line() throws Exception {
    return detail().get("materials").get(0).get("id").asLong();
  }

  long step(int n) throws Exception {
    return detail().get("steps").get(n).get("id").asLong();
  }

  void stock(String qty, String price) throws Exception {
    var v = move(qty);
    v.put("price", price);
    ok("/items/" + material + "/receive", v);
  }

  void start() throws Exception {
    release();
    stock("1", "100");
    ok("/materials/" + line() + "/issue", move("0.1239"));
    act("start", Map.of(), 200);
  }

  void output(int seq, int good, int scrap, String hours) throws Exception {
    var v =
        Map.of(
            "goodQuantity",
            good,
            "rejectedQuantity",
            scrap,
            "hours",
            hours,
            "reference",
            "REPORT-" + seq,
            "requestKey",
            UUID.randomUUID().toString());
    if (good != 0 || scrap != 0 || !hours.equals("0"))
      sendAs(operator, "/steps/" + step(seq) + "/report", v, 200);
    sendAs(
        operator,
        "/steps/" + step(seq) + "/finish",
        Map.of("requestKey", UUID.randomUUID().toString()),
        200);
  }

  void ready() throws Exception {
    start();
    output(0, 9, 1, "1");
    output(1, 8, 1, "2");
    act("send-review", Map.of(), 200);
    act(
        "review",
        Map.of("passed", true, "acceptedQuantity", 8, "note", "Dimensions accepted"),
        200);
  }

  JsonNode itemBalance(long id) throws Exception {
    for (var x : read("/catalog").get("items")) if (x.get("id").asLong() == id) return x;
    throw new AssertionError();
  }

  @Test
  void fullProductionConservesQuantityAndCost() throws Exception {
    ready();
    act("receive", Map.of("reference", "FIN-001"), 200);
    assertEquals("COMPLETE", detail().get("order").get("status").asString());
    assertEquals(192.39, detail().get("order").get("totalCost").asDouble(), .00001);
    assertEquals(.8761, itemBalance(material).get("quantity").asDouble(), .00000001);
    assertEquals(87.61, itemBalance(material).get("inventoryValue").asDouble(), .00001);
    assertEquals(8, itemBalance(product).get("quantity").asInt());
    assertEquals(192.39, itemBalance(product).get("inventoryValue").asDouble(), .00001);
  }

  @Test
  void sixDecimalBomQuantityIsNotRounded() throws Exception {
    release();
    assertEquals(
        .1239, detail().get("materials").get(0).get("requiredQuantity").asDouble(), .00000001);
  }

  @Test
  void duplicateIssueHasOneLedgerEntry() throws Exception {
    release();
    stock("1", "100");
    var v = move("0.1");
    ok("/materials/" + line() + "/issue", v);
    ok("/materials/" + line() + "/issue", v);
    assertEquals(1, detail().get("movements").size());
    assertEquals(.9, itemBalance(material).get("quantity").asDouble(), .0000001);
    v.put("quantity", "0.11");
    assertEquals(
        "RETRY_CONTENT_CHANGED",
        sendAs(session, "/materials/" + line() + "/issue", v, 409).get("code").asString());
  }

  @Test
  void duplicateCreationReturnsSameOrder() throws Exception {
    var v = plan();
    var a = ok("/orders", v);
    var b = ok("/orders", v);
    assertEquals(a.get("id").asLong(), b.get("id").asLong());
  }

  @Test
  void issueBeyondAuthorizationRollsBackStock() throws Exception {
    release();
    stock("1", "100");
    sendAs(session, "/materials/" + line() + "/issue", move("0.124"), 409);
    assertEquals(1, itemBalance(material).get("quantity").asDouble());
    assertEquals(0, detail().get("movements").size());
  }

  @Test
  void shortageCannotMakeNegativeStock() throws Exception {
    release();
    sendAs(session, "/materials/" + line() + "/issue", move("0.1"), 409);
    assertEquals(0, itemBalance(material).get("quantity").asDouble());
  }

  @Test
  void concurrentIssuesCannotExceedCap() throws Exception {
    release();
    stock("1", "100");
    String path = "/materials/" + line() + "/issue";
    var executor = Executors.newFixedThreadPool(2);
    try {
      var a = executor.submit(() -> rawStatus(session, path, move("0.08")));
      var b = executor.submit(() -> rawStatus(session, path, move("0.08")));
      var statuses = List.of(a.get(), b.get());
      assertTrue(statuses.contains(200));
      assertTrue(statuses.contains(409));
      assertEquals(.08, detail().get("materials").get(0).get("netQuantity").asDouble(), .000001);
    } finally {
      executor.shutdownNow();
    }
  }

  int rawStatus(MockHttpSession s, String path, Object body) throws Exception {
    return mvc.perform(
            post("/api" + path)
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @Test
  void originalReturnCostSurvivesLaterPurchases() throws Exception {
    release();
    stock("1", "100");
    ok("/materials/" + line() + "/issue", move("0.1"));
    long source = detail().get("movements").get(0).get("id").asLong();
    stock("1", "200");
    var v = move("0.1");
    v.put("sourceId", source);
    ok("/materials/" + line() + "/return", v);
    assertEquals(300, itemBalance(material).get("inventoryValue").asDouble());
    sendAs(session, "/materials/" + line() + "/return", move("0.1"), 400);
  }

  @Test
  void returnsCannotExceedOriginalIssue() throws Exception {
    release();
    stock("1", "100");
    ok("/materials/" + line() + "/issue", move("0.1"));
    var v = move("0.11");
    v.put("sourceId", detail().get("movements").get(0).get("id").asLong());
    sendAs(session, "/materials/" + line() + "/return", v, 409);
    assertEquals(.9, itemBalance(material).get("quantity").asDouble(), .000001);
  }

  @Test
  void cancelRequiresMaterialsReturned() throws Exception {
    release();
    stock("1", "100");
    ok("/materials/" + line() + "/issue", move("0.1"));
    act("cancel", Map.of("note", "Cancelled"), 409);
    var v = move("0.1");
    v.put("sourceId", detail().get("movements").get(0).get("id").asLong());
    ok("/materials/" + line() + "/return", v);
    act("cancel", Map.of("note", "Cancelled"), 200);
    assertEquals("CANCELLED", detail().get("order").get("status").asString());
  }

  @Test
  void cannotStartWithoutIssue() throws Exception {
    release();
    act("start", Map.of(), 409);
  }

  @Test
  void cannotReportAheadOfPreviousOperation() throws Exception {
    start();
    sendAs(
        operator,
        "/steps/" + step(1) + "/report",
        Map.of(
            "goodQuantity",
            1,
            "rejectedQuantity",
            0,
            "hours",
            "1",
            "reference",
            "R",
            "requestKey",
            UUID.randomUUID().toString()),
        409);
  }

  @Test
  void cannotOverproduceOrFinishUnaccountedOutput() throws Exception {
    start();
    sendAs(
        operator,
        "/steps/" + step(0) + "/report",
        Map.of(
            "goodQuantity",
            11,
            "rejectedQuantity",
            0,
            "hours",
            "1",
            "reference",
            "R",
            "requestKey",
            UUID.randomUUID().toString()),
        409);
    sendAs(
        operator,
        "/steps/" + step(0) + "/finish",
        Map.of("requestKey", UUID.randomUUID().toString()),
        409);
  }

  @Test
  void adminCannotReportAsAssignedOperator() throws Exception {
    start();
    sendAs(
        session,
        "/steps/" + step(0) + "/report",
        Map.of(
            "goodQuantity",
            10,
            "rejectedQuantity",
            0,
            "hours",
            "1",
            "reference",
            "R",
            "requestKey",
            UUID.randomUUID().toString()),
        403);
  }

  @Test
  void operatorCannotInspectOwnProductionEvenWithQcGranted() throws Exception {
    start();
    output(0, 10, 0, "1");
    output(1, 10, 0, "1");
    act("send-review", Map.of(), 200);
    long role =
        ok(
                "/admin/roles",
                Map.of(
                    "name",
                    "ReporterQc" + suffix,
                    "scope",
                    "DEPARTMENT",
                    "permissions",
                    List.of("execute", "quality", "production.read")))
            .get("id")
            .asLong();
    var r =
        mvc.perform(
                put("/api/admin/users/" + op)
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "username",
                                "op" + suffix,
                                "displayName",
                                "Operator",
                                "roleId",
                                role,
                                "departmentId",
                                1,
                                "enabled",
                                true))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus());
    var v =
        new HashMap<>(
            Map.<String, Object>of(
                "revision",
                detail().get("order").get("revision").asLong(),
                "passed",
                true,
                "acceptedQuantity",
                10,
                "note",
                "QC",
                "requestKey",
                UUID.randomUUID().toString()));
    sendAs(operator, "/orders/" + order + "/review", v, 403);
  }

  @Test
  void qcReworkKeepsOldReportsAndAddsLabor() throws Exception {
    start();
    output(0, 10, 0, "1");
    output(1, 10, 0, "1");
    act("send-review", Map.of(), 200);
    act("review", Map.of("passed", false, "acceptedQuantity", 0, "note", "Rework required"), 200);
    output(1, 0, 0, "0.5");
    act("send-review", Map.of(), 200);
    act("review", Map.of("passed", true, "acceptedQuantity", 10, "note", "Recheck pass"), 200);
    act("receive", Map.of("reference", "REC"), 200);
    assertEquals(3, detail().get("reports").size());
    assertEquals(2, detail().get("reviews").size());
    assertEquals(150, detail().get("order").get("laborCost").asDouble());
  }

  @Test
  void zeroGoodClosesAsLossWithoutReceipt() throws Exception {
    start();
    output(0, 0, 10, "1");
    output(1, 0, 0, "0");
    act("send-review", Map.of(), 200);
    act("review", Map.of("passed", true, "acceptedQuantity", 0, "note", "All scrapped"), 200);
    act("receive", Map.of("reference", "LOSS"), 200);
    assertEquals(0, itemBalance(product).get("quantity").asInt());
    assertEquals(72.39, detail().get("order").get("lossCost").asDouble(), .000001);
    for (var m : detail().get("movements")) assertNotEquals("PRODUCE", m.get("kind").asString());
  }

  @Test
  void materialVarianceRequiresReasonBeforeQc() throws Exception {
    start();
    act(
        "authorize-material",
        Map.of("materialId", line(), "quantity", "0.2", "note", "Cutting waste"),
        200);
    ok("/materials/" + line() + "/issue", move("0.01"));
    output(0, 10, 0, "1");
    output(1, 10, 0, "1");
    act("send-review", Map.of(), 400);
    act("send-review", Map.of("varianceType", "material", "note", "Extra cutting material"), 200);
  }

  @Test
  void frozenBomCannotBeEdited() throws Exception {
    var v = new HashMap<>(bomInput(1));
    v.put("revision", 1);
    var r =
        mvc.perform(
                put("/api/boms/" + bom)
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(v)))
            .andReturn();
    assertEquals(409, r.getResponse().getStatus());
  }

  @Test
  void bomArchiveDoesNotAlterReleasedOrder() throws Exception {
    release();
    ok("/boms/" + bom + "/archive", Map.of("revision", 1));
    assertEquals(2, detail().get("steps").size());
    sendAs(session, "/orders", plan(), 409);
  }

  @Test
  void staleRevisionRejected() throws Exception {
    release();
    sendAs(
        session,
        "/orders/" + order + "/start",
        Map.of("revision", 0, "requestKey", UUID.randomUUID().toString()),
        409);
  }

  @Test
  void voidBlockedAfterDownstreamWork() throws Exception {
    start();
    output(0, 10, 0, "1");
    output(1, 10, 0, "1");
    act(
        "void-report",
        Map.of("reportId", detail().get("reports").get(0).get("id").asLong(), "note", "Correction"),
        409);
  }

  @Test
  void currentReportVoidKeepsTraceAndAllowsCorrection() throws Exception {
    start();
    output(0, 9, 1, "1");
    long rid = detail().get("reports").get(0).get("id").asLong();
    act("void-report", Map.of("reportId", rid, "note", "Count corrected"), 200);
    output(0, 10, 0, "1");
    assertEquals(2, detail().get("reports").size());
    assertTrue(detail().get("reports").get(0).get("voided").asBoolean());
  }

  @Test
  void operatorScopeHidesOtherOrdersAndCosts() throws Exception {
    start();
    long other = ok("/orders", plan()).get("id").asLong();
    readAs(operator, "/orders/" + other, 403);
    var safe = readAs(operator, "/orders/" + order, 200).toString();
    assertFalse(safe.contains("laborCost"));
    assertFalse(safe.contains("hourlyRate"));
    assertFalse(safe.contains("valueDelta"));
    assertFalse(readAs(operator, "/catalog", 200).toString().contains("inventoryValue"));
    readAs(operator, "/lists/users", 403);
  }

  @Test
  void departmentScopeBlocksAnotherWorkshop() throws Exception {
    long d = ok("/admin/departments", Map.of("name", "Other" + suffix)).get("id").asLong();
    user("view" + suffix, "只读 / Viewer", d);
    var viewer = login("view" + suffix, PASSWORD);
    readAs(viewer, "/orders/" + order, 403);
    assertEquals(0, readAs(viewer, "/lists/items", 200).get("total").asInt());
  }

  @Test
  void csvAndReportsHideCostsForViewer() throws Exception {
    ready();
    act("receive", Map.of("reference", "REC"), 200);
    user("view" + suffix, "只读 / Viewer", 1);
    var viewer = login("view" + suffix, PASSWORD);
    var report = readAs(viewer, "/reports", 200).toString();
    assertFalse(report.contains("totalCost"));
    var r = mvc.perform(get("/api/reports.csv").session(viewer)).andReturn();
    assertEquals(200, r.getResponse().getStatus());
    assertFalse(r.getResponse().getContentAsString().contains("materialCost"));
  }

  @Test
  void invalidImportRollsBackWholeBatch() throws Exception {
    var first = item("I" + suffix, "MATERIAL", 1);
    var invalid = item("J" + suffix, "INVALID", 1);
    sendAs(session, "/master/items/import", List.of(first, invalid), 400);
    assertEquals(0, read("/lists/items?search=I" + suffix).get("total").asInt());
  }

  @Test
  void stockCountRejectsChangedBookBalance() throws Exception {
    stock("1", "100");
    var v = move("2");
    v.put("bookQuantity", "0");
    v.put("note", "Count");
    sendAs(session, "/items/" + material + "/count", v, 409);
    assertEquals(1, itemBalance(material).get("quantity").asDouble());
  }

  @Test
  void fractionalFinishedReceiptRejected() throws Exception {
    var v = move("0.5");
    v.put("price", "10");
    sendAs(session, "/items/" + product + "/receive", v, 400);
  }

  @Test
  void receiptRetryAfterCompleteDoesNotDuplicateStock() throws Exception {
    ready();
    var v =
        Map.of(
            "revision",
            detail().get("order").get("revision").asLong(),
            "reference",
            "REC",
            "requestKey",
            UUID.randomUUID().toString());
    ok("/orders/" + order + "/receive", v);
    ok("/orders/" + order + "/receive", v);
    assertEquals(8, itemBalance(product).get("quantity").asInt());
  }

  @Test
  void securityRequiresLoginAndCsrf() throws Exception {
    assertEquals(
        401, mvc.perform(get("/api/orders/" + order)).andReturn().getResponse().getStatus());
    assertEquals(
        403,
        mvc.perform(
                post("/api/orders")
                    .session(session)
                    .contentType("application/json")
                    .content(json.writeValueAsString(plan())))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void disabledAccountSessionImmediatelyRevoked() throws Exception {
    var role = read("/lists/users?search=op" + suffix).get("items").get(0).get("roleId").asLong();
    var r =
        mvc.perform(
                put("/api/admin/users/" + op)
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "username",
                                "op" + suffix,
                                "displayName",
                                "Operator",
                                "roleId",
                                role,
                                "departmentId",
                                1,
                                "enabled",
                                false))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus());
    readAs(operator, "/auth/me", 401);
  }

  @Test
  void lastGlobalAdminRoleCannotLoseAdminPermission() throws Exception {
    long id = read("/auth/me").get("id").asLong();
    var a = read("/lists/users?search=admin").get("items").get(0);
    long rid = a.get("roleId").asLong();
    var r =
        mvc.perform(
                put("/api/admin/roles/" + rid)
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "name",
                                "Administrator",
                                "scope",
                                "ALL",
                                "permissions",
                                List.of("dashboard")))))
            .andReturn();
    assertEquals(409, r.getResponse().getStatus());
    assertEquals(id, read("/auth/me").get("id").asLong());
    read("/lists/users");
  }

  @Test
  void passwordChangeRevokesEveryOldSession() throws Exception {
    var second = login("op" + suffix, PASSWORD);
    String fresh = "Aa9" + UUID.randomUUID();
    sendAs(operator, "/auth/password", Map.of("oldPassword", PASSWORD, "newPassword", fresh), 200);
    readAs(second, "/auth/me", 401);
    login("op" + suffix, fresh);
  }

  @Test
  void rolePermissionRevocationTakesImmediateEffect() throws Exception {
    long role =
        ok(
                "/admin/roles",
                Map.of(
                    "name",
                    "Custom" + suffix,
                    "scope",
                    "DEPARTMENT",
                    "permissions",
                    List.of("production.read")))
            .get("id")
            .asLong();
    var v =
        Map.of(
            "username",
            "op" + suffix,
            "displayName",
            "Operator",
            "roleId",
            role,
            "departmentId",
            1,
            "enabled",
            true);
    var r =
        mvc.perform(
                put("/api/admin/users/" + op)
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(v)))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus());
    sendAs(operator, "/steps/1/report", Map.of("requestKey", UUID.randomUUID().toString()), 403);
  }

  @Test
  void weakNewAccountPasswordRejected() throws Exception {
    sendAs(
        session,
        "/admin/users",
        Map.of(
            "username",
            "weak" + suffix,
            "displayName",
            "Weak",
            "password",
            "123456",
            "roleId",
            1,
            "departmentId",
            1,
            "enabled",
            true),
        400);
  }

  @Test
  void finalAcceptanceCannotExceedLastGoodOutput() throws Exception {
    start();
    output(0, 10, 0, "1");
    output(1, 8, 2, "1");
    act("send-review", Map.of(), 200);
    act("review", Map.of("passed", true, "acceptedQuantity", 9, "note", "Incorrect"), 400);
    assertEquals("REVIEW", detail().get("order").get("status").asString());
    assertEquals(0, detail().get("reviews").size());
  }

  @Test
  void allReturnedOriginalValuePreservesTailCents() throws Exception {
    release();
    stock("0.1239", "0.1");
    ok("/materials/" + line() + "/issue", move("0.1239"));
    long source = detail().get("movements").get(0).get("id").asLong();
    for (String q : List.of("0.04", "0.04", "0.0439")) {
      var v = move(q);
      v.put("sourceId", source);
      ok("/materials/" + line() + "/return", v);
    }
    assertEquals(.01, itemBalance(material).get("inventoryValue").asDouble(), .0000001);
    assertEquals(.1239, itemBalance(material).get("quantity").asDouble(), .0000001);
  }

  @Test
  void finishedGoodsDispatchUsesRecordedCost() throws Exception {
    ready();
    act("receive", Map.of("reference", "REC"), 200);
    ok("/items/" + product + "/dispatch", move("8"));
    assertEquals(0, itemBalance(product).get("quantity").asInt());
    assertEquals(0, itemBalance(product).get("inventoryValue").asDouble());
  }

  @Test
  void duplicateReportCannotIncreaseQuantitiesOrHours() throws Exception {
    start();
    var v =
        Map.of(
            "goodQuantity",
            10,
            "rejectedQuantity",
            0,
            "hours",
            "1",
            "reference",
            "DUP",
            "requestKey",
            UUID.randomUUID().toString());
    sendAs(operator, "/steps/" + step(0) + "/report", v, 200);
    sendAs(operator, "/steps/" + step(0) + "/report", v, 200);
    assertEquals(1, detail().get("reports").size());
    assertEquals(10, detail().get("steps").get(0).get("goodQuantity").asInt());
  }
}
