// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 工单、主数据和系统管理接口，读写均验证实时权限与范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final Store db;
  final AccessService access;
  final MasterService masters;
  final AdminService admin;
  final BomService boms;
  final ProductionService work;
  final ProductionAccess scope;
  final StockService stock;

  public ApiController(
      Store db,
      AccessService access,
      MasterService masters,
      AdminService admin,
      BomService boms,
      ProductionService work,
      ProductionAccess scope,
      StockService stock) {
    this.db = db;
    this.access = access;
    this.masters = masters;
    this.admin = admin;
    this.boms = boms;
    this.work = work;
    this.scope = scope;
    this.stock = stock;
  }

  /** 参考目录不返回账号密码或成本越权字段，BOM选择遵循读取权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/catalog")
  @Transactional(readOnly = true)
  public Map<String, Object> catalog() {
    access.current();
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("settings", db.all(SystemSetting.class));
    r.put("dictionaries", db.all(DictionaryEntry.class));
    r.put(
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList());
    if (can("master.read")) {
      r.put("items", masters.list("items"));
      r.put("stations", masters.list("stations"));
    }
    if (can("bom.read")) r.put("boms", bomRows());
    if (can("production.manage"))
      r.put(
          "operators",
          db.all(Account.class).stream()
              .filter(
                  a ->
                      a.enabled
                          && access.visible(a.departmentId)
                          && db.get(AccessRole.class, a.roleId).permissions.contains("execute"))
              .map(
                  a ->
                      Map.of(
                          "id", a.id, "displayName", a.displayName, "departmentId", a.departmentId))
              .toList());
    if (can("admin")) {
      access.require("admin");
      r.put("roles", admin.list("roles"));
      r.put("permissions", admin.list("permissions"));
    }
    return r;
  }

  /** 有界搜索、白名单排序、过滤与分页；成本权限字段不参与无权搜索。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/lists/{resource}")
  @Transactional(readOnly = true)
  public Map<String, Object> list(
      @PathVariable String resource,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "id") String sort,
      @RequestParam(defaultValue = "true") boolean desc,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (page < 0
        || size < 1
        || size > 100
        || search.length() > 200
        || !Set.of("id", "name", "code", "number", "createdAt", "dueDate").contains(sort))
      throw new Problem(400, "INVALID_PAGE");
    String q = search.toLowerCase(Locale.ROOT);
    var rows =
        rows(resource).stream()
            .filter(r -> searchText(r).toLowerCase(Locale.ROOT).contains(q))
            .filter(
                r ->
                    status.isBlank()
                        || status.equals(field(r, "status"))
                        || status.equals(field(r, "kind")))
            .sorted(
                (a, b) -> {
                  int n =
                      sort.equals("id")
                          ? Long.compare(
                              ((Number) field(a, "id")).longValue(),
                              ((Number) field(b, "id")).longValue())
                          : String.valueOf(field(a, sort))
                              .compareTo(String.valueOf(field(b, sort)));
                  return desc ? -n : n;
                })
            .toList();
    return Map.of(
        "items",
        rows.stream().skip((long) page * size).limit(size).toList(),
        "total",
        rows.size(),
        "page",
        page,
        "size",
        size);
  }

  /** 新建物料或工位。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/master/{type}")
  public Object createMaster(@PathVariable String type, @RequestBody MasterService.Input v) {
    return masters.save(type, null, v);
  }

  /** 更新主数据，不直接修改库存。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/master/{type}/{id}")
  public Object updateMaster(
      @PathVariable String type, @PathVariable Long id, @RequestBody MasterService.Input v) {
    return masters.save(type, id, v);
  }

  /** 删除受外键保护的主数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/master/{type}/{id}")
  public Object deleteMaster(@PathVariable String type, @PathVariable Long id) {
    masters.delete(type, id);
    return Map.of("ok", true);
  }

  /** 原子导入最多500条物料JSON。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/master/items/import")
  public Object importItems(@RequestBody List<MasterService.Input> v) {
    return masters.importItems(v);
  }

  /** 新增管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object createAdmin(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 修改管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object updateAdmin(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 删除管理资源并保护最后管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object deleteAdmin(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }

  /** 创建BOM草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/boms")
  public Object createBom(@RequestBody BomService.Input v) {
    return boms.save(null, v);
  }

  /** 更新未使用的BOM草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/boms/{id}")
  public Object updateBom(@PathVariable Long id, @RequestBody BomService.Input v) {
    return boms.save(id, v);
  }

  /** BOM详情按成本权限脱敏。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/boms/{id}")
  public Object bom(@PathVariable Long id) {
    return boms.detail(id);
  }

  /** 冻结启用或归档BOM。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/boms/{id}/{action}")
  public Object bomState(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> v) {
    return boms.state(id, action, StockService.longValue(v, "revision"));
  }

  /** 删除未使用草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/boms/{id}")
  public Object deleteBom(@PathVariable Long id) {
    boms.delete(id);
    return Map.of("ok", true);
  }

  /** 新建生产计划，返回已脱敏摘要。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders")
  @Transactional
  public Object createOrder(@RequestBody Map<String, Object> v) {
    return work.view(work.save(null, v));
  }

  /** 更新生产草稿计划。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/orders/{id}")
  @Transactional
  public Object updateOrder(@PathVariable Long id, @RequestBody Map<String, Object> v) {
    return work.view(work.save(id, v));
  }

  /** 工单完整快照及履历。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/orders/{id}")
  public Object order(@PathVariable Long id) {
    return work.detail(id);
  }

  /** 计划下达、派工、质检、入库等生命周期操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders/{id}/{action}")
  public Object orderAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> v) {
    work.action(id, action, v);
    return Map.of("ok", true);
  }

  /** 工序报工或完工，验证实际派工人员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/steps/{id}/{action}")
  public Object stepAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> v) {
    work.stepAction(id, action, v);
    return Map.of("ok", true);
  }

  /** 工单物料领退，原单关联及原成本恢复。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/materials/{id}/{action}")
  public Object materialAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> v) {
    stock.materialAction(id, action, v);
    return Map.of("ok", true);
  }

  /** 库存收货、成品发出和盘点。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/items/{id}/{action}")
  public Object itemAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> v) {
    stock.itemAction(id, action, v);
    return Map.of("ok", true);
  }

  /** 当前范围内实际工单与低库存，操作员首页仅自己的派工。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var jobs = can("production.read") ? work.list() : List.<Map<String, Object>>of();
    var counts =
        jobs.stream()
            .collect(
                Collectors.groupingBy(o -> String.valueOf(o.get("status")), Collectors.counting()));
    long low =
        can("stock.read")
            ? db.all(Item.class).stream()
                .filter(
                    x ->
                        access.visible(x.departmentId)
                            && x.enabled
                            && x.quantity.compareTo(x.reorderLevel) <= 0)
                .count()
            : 0;
    return Map.of("counts", counts, "recent", jobs.stream().limit(12).toList(), "lowStock", low);
  }

  /** 按实际入库日期统计完结工单，数量与损耗分开，成本另授权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  @Transactional(readOnly = true)
  public Map<String, Object> reports(
      @RequestParam(defaultValue = "") String from, @RequestParam(defaultValue = "") String to) {
    access.require("report");
    access.require("production.read");
    LocalDate start, end;
    try {
      start = from.isBlank() ? LocalDate.of(1970, 1, 1) : LocalDate.parse(from);
      end = to.isBlank() ? LocalDate.of(9999, 12, 31) : LocalDate.parse(to);
    } catch (java.time.format.DateTimeParseException e) {
      throw new Problem(400, "INVALID_DATE");
    }
    if (end.isBefore(start)) throw new Problem(400, "INVALID_DATE");
    var zone =
        ZoneId.of(
            db.query(SystemSetting.class, "from SystemSetting where code='timezone'")
                .getFirst()
                .value);
    var items =
        db.all(ProductionOrder.class).stream()
            .filter(scope::visible)
            .filter(o -> o.status.equals("COMPLETE"))
            .filter(
                o -> {
                  var date = o.finishedAt.atZone(zone).toLocalDate();
                  return !date.isBefore(start) && !date.isAfter(end);
                })
            .map(
                o -> {
                  var r = work.view(o);
                  r.put("scrappedQuantity", o.plannedQuantity - o.acceptedQuantity);
                  r.put(
                      "hours",
                      db
                          .query(
                              ProductionReport.class,
                              "from ProductionReport where orderId=?1 and voided=false",
                              o.id)
                          .stream()
                          .map(x -> x.hours)
                          .reduce(BigDecimal.ZERO, BigDecimal::add));
                  return r;
                })
            .toList();
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("items", items);
    r.put("from", from);
    r.put("to", to);
    r.put("completed", items.size());
    for (var key : List.of("plannedQuantity", "acceptedQuantity", "scrappedQuantity", "hours"))
      r.put(key, sum(items, key));
    if (scope.cost())
      for (var key : List.of("materialCost", "laborCost", "totalCost", "lossCost"))
        r.put(key, sum(items, key));
    return r;
  }

  /** CSV使用授权后的相同字段并防电子表格公式注入，无推广载荷。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping(value = "/reports.csv", produces = "text/csv")
  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> csv(
      @RequestParam(defaultValue = "") String from, @RequestParam(defaultValue = "") String to) {
    var report = reports(from, to);
    var keys =
        new ArrayList<>(
            List.of(
                "number",
                "productCode",
                "productName",
                "finishedAt",
                "plannedQuantity",
                "acceptedQuantity",
                "scrappedQuantity",
                "hours"));
    if (scope.cost()) keys.addAll(List.of("materialCost", "laborCost", "totalCost", "lossCost"));
    var b = new StringBuilder("\uFEFF" + String.join(",", keys) + "\r\n");
    for (var item : (List<?>) report.get("items")) {
      var m = (Map<?, ?>) item;
      b.append(
              keys.stream()
                  .map(k -> ProductionPolicy.csv(m.get(k)))
                  .collect(Collectors.joining(",")))
          .append("\r\n");
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=production.csv")
        .body(b.toString().getBytes(StandardCharsets.UTF_8));
  }

  private BigDecimal sum(List<Map<String, Object>> items, String key) {
    return items.stream()
        .map(r -> new BigDecimal(String.valueOf(r.get(key))))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private boolean can(String p) {
    return access.role().permissions.contains(p);
  }

  private List<?> bomRows() {
    access.require("bom.read");
    return db.all(BillOfMaterials.class).stream()
        .filter(b -> access.visible(b.departmentId))
        .map(
            b ->
                Map.of(
                    "id",
                    b.id,
                    "productId",
                    b.productId,
                    "productName",
                    db.get(Item.class, b.productId).name,
                    "versionNumber",
                    b.versionNumber,
                    "status",
                    b.status,
                    "revision",
                    b.revision,
                    "description",
                    b.description,
                    "createdAt",
                    b.createdAt))
        .toList();
  }

  private List<?> rows(String type) {
    return switch (type) {
      case "orders" -> work.list();
      case "boms" -> bomRows();
      case "items", "stations" -> masters.list(type);
      case "movements" -> {
        access.require("stock.read");
        yield db.all(StockMovement.class).stream()
            .filter(
                m ->
                    access.visible(m.departmentId)
                        && (m.orderId == null
                            || (can("production.read")
                                && scope.visible(db.get(ProductionOrder.class, m.orderId)))))
            .map(stock::view)
            .toList();
      }
      case "audit" -> {
        access.require("audit");
        yield db.all(AuditEvent.class).stream()
            .filter(a -> access.visible(a.departmentId))
            .toList();
      }
      default -> admin.list(type);
    };
  }

  private Object field(Object o, String key) {
    if (o instanceof Map<?, ?> m) return m.containsKey(key) ? m.get(key) : "";
    try {
      return o.getClass().getField(key).get(o);
    } catch (ReflectiveOperationException e) {
      return "";
    }
  }

  private String searchText(Object o) {
    return List.of(
            "number",
            "productName",
            "productCode",
            "name",
            "code",
            "username",
            "displayName",
            "reference",
            "note",
            "actor",
            "action",
            "objectId",
            "description",
            "createdAt")
        .stream()
        .map(k -> String.valueOf(field(o, k)))
        .collect(Collectors.joining(" "));
  }
}
