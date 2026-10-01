// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 顺序离散制造工单：下达冻结、按工序报工、独立终检及一次入库。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class ProductionService {
  final Store db;
  final AccessService access;
  final ProductionAccess scope;
  final BomService boms;
  final StockService stock;
  final CommandService commands;
  final Clock clock;

  public ProductionService(
      Store db,
      AccessService access,
      ProductionAccess scope,
      BomService boms,
      StockService stock,
      CommandService commands,
      Clock clock) {
    this.db = db;
    this.access = access;
    this.scope = scope;
    this.boms = boms;
    this.stock = stock;
    this.commands = commands;
    this.clock = clock;
  }

  /** 创建草稿或更新尚未下达的计划，产品取自启用BOM。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ProductionOrder save(Long id, Map<String, Object> v) {
    access.require("production.manage");
    db.lock(Department.class, 1L);
    var command = commands.open("ORDER_SAVE_" + id, v);
    if (!command.fresh()) return scope.get(command.stamp().resultId, "production.manage");
    var b = db.get(BillOfMaterials.class, StockService.longValue(v, "bomId"));
    access.department(b.departmentId);
    if (!b.status.equals("ACTIVE")) throw new Problem(409, "BOM_NOT_ACTIVE");
    var p = db.get(Item.class, b.productId);
    if (!p.enabled) throw new Problem(409, "DISABLED_MASTER");
    var o = id == null ? new ProductionOrder() : scope.get(id, "production.manage");
    if (id != null) {
      revision(o, v);
      state(o, "DRAFT");
    }
    o.bomId = b.id;
    o.productId = p.id;
    o.departmentId = p.departmentId;
    o.productCode = p.code;
    o.productName = p.name;
    o.unit = p.unit;
    o.bomVersion = b.versionNumber;
    o.plannedQuantity = StockService.integer(v, "plannedQuantity", true);
    try {
      o.dueDate = LocalDate.parse(StockService.text(v, "dueDate", 10));
    } catch (java.time.format.DateTimeParseException e) {
      throw new Problem(400, "INVALID_DATE");
    }
    o.note = BomService.optional(StockService.string(v, "note"), 2000);
    o.sourceReference = BomService.optional(StockService.string(v, "sourceReference"), 200);
    o.updatedAt = clock.instant();
    if (id == null) {
      o.number =
          "MO-"
              + LocalDate.now(clock)
              + "-"
              + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
      o.createdAt = o.updatedAt;
      o.createdBy = access.current().username;
      db.save(o);
    } else o.revision++;
    command.stamp().resultId = o.id;
    event(o, "PLAN", o.note);
    return o;
  }

  /** 执行管理、终检或入库命令；持久化幂等键及修订号防重复与旧页覆盖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void action(Long id, String action, Map<String, Object> v) {
    String permission =
        action.equals("review")
            ? "quality"
            : action.equals("receive") ? "stock.write" : "production.manage";
    access.require(permission);
    db.lock(Department.class, 1L);
    var o = scope.get(id, permission);
    var command = commands.open("ORDER_" + id + "_" + action, v);
    if (!command.fresh()) return;
    revision(o, v);
    switch (action) {
      case "release" -> {
        state(o, "DRAFT");
        var b = db.get(BillOfMaterials.class, o.bomId);
        if (!b.status.equals("ACTIVE") || !db.get(Item.class, o.productId).enabled)
          throw new Problem(409, "BOM_NOT_ACTIVE");
        for (var c : boms.components(b.id)) {
          var item = db.get(Item.class, c.itemId);
          if (!item.enabled) throw new Problem(409, "DISABLED_MASTER");
          var l = new OrderMaterial();
          l.orderId = id;
          l.itemId = c.itemId;
          l.itemCode = c.itemCode;
          l.itemName = c.itemName;
          l.unit = c.unit;
          l.requiredQuantity =
              ProductionPolicy.quantity(
                  c.perUnit.multiply(new BigDecimal(o.plannedQuantity)), true);
          l.authorizedQuantity = l.requiredQuantity;
          db.save(l);
        }
        for (var c : boms.operations(b.id)) {
          if (!db.get(Station.class, c.stationId).enabled)
            throw new Problem(409, "DISABLED_MASTER");
          var s = new WorkStep();
          s.orderId = id;
          s.sequence = c.sequence;
          s.stationId = c.stationId;
          s.name = c.name;
          s.stationName = c.stationName;
          s.hourlyRate = c.hourlyRate;
          db.save(s);
        }
        if (materials(id).isEmpty() || steps(id).isEmpty())
          throw new Problem(409, "INCOMPLETE_BOM");
        o.status = "RELEASED";
        event(o, "RELEASE", "BOM v" + o.bomVersion);
      }
      case "assign" -> {
        if (!Set.of("RELEASED", "IN_PROGRESS").contains(o.status))
          throw new Problem(409, "INVALID_STATE");
        var s = ownedStep(o, StockService.longValue(v, "stepId"));
        if (s.finished || !reports(s.id).isEmpty()) throw new Problem(409, "STEP_STARTED");
        var a = db.get(Account.class, StockService.longValue(v, "operatorId"));
        if (!a.enabled
            || !a.departmentId.equals(o.departmentId)
            || !db.get(AccessRole.class, a.roleId).permissions.contains("execute"))
          throw new Problem(400, "INVALID_OPERATOR");
        s.operatorId = a.id;
        s.operatorName = a.displayName;
        event(o, "ASSIGN", s.sequence + " · " + a.displayName);
      }
      case "start" -> {
        state(o, "RELEASED");
        for (var s : steps(id)) {
          if (s.operatorId == null || !db.get(Account.class, s.operatorId).enabled)
            throw new Problem(409, "UNASSIGNED_STEP");
        }
        for (var l : materials(id))
          if (stock.netQuantity(l.id).signum() <= 0) throw new Problem(409, "MATERIAL_NOT_ISSUED");
        o.status = "IN_PROGRESS";
        event(o, "START", "");
      }
      case "authorize-material" -> {
        if (!Set.of("RELEASED", "IN_PROGRESS").contains(o.status))
          throw new Problem(409, "INVALID_STATE");
        var l = db.get(OrderMaterial.class, StockService.longValue(v, "materialId"));
        if (!l.orderId.equals(id)) throw new Problem(404, "NOT_FOUND");
        var q = ProductionPolicy.quantity(StockService.decimal(v, "quantity"), true);
        if (q.compareTo(stock.netQuantity(l.id)) < 0) throw new Problem(409, "BELOW_ISSUED");
        l.adjustmentNote = StockService.text(v, "note", 1800);
        l.authorizedQuantity = q;
        event(o, "AUTHORIZE_MATERIAL", l.itemCode + " · " + q + " · " + l.adjustmentNote);
      }
      case "send-review" -> {
        state(o, "IN_PROGRESS");
        for (var s : steps(id)) if (!s.finished) throw new Problem(409, "STEP_NOT_FINISHED");
        boolean variance =
            materials(id).stream()
                .anyMatch(l -> stock.netQuantity(l.id).compareTo(l.requiredQuantity) != 0);
        if (variance) {
          var type = StockService.text(v, "varianceType", 60);
          if (db.query(
                  DictionaryEntry.class,
                  "from DictionaryEntry where type='variance_reason' and code=?1 and enabled=true",
                  type)
              .isEmpty()) throw new Problem(400, "INVALID_VARIANCE_REASON");
          o.materialVarianceNote = type + " · " + StockService.text(v, "note", 1900);
        }
        if (lastGood(o) > 0)
          for (var l : materials(id))
            if (stock.netQuantity(l.id).signum() <= 0)
              throw new Problem(409, "MATERIAL_NOT_ISSUED");
        o.status = "REVIEW";
        event(o, "SEND_REVIEW", o.materialVarianceNote);
      }
      case "review" -> {
        state(o, "REVIEW");
        if (db.query(
                    ProductionReport.class,
                    "from ProductionReport where orderId=?1 and actorId=?2",
                    id,
                    access.current().id)
                .size()
            > 0) throw new Problem(403, "INDEPENDENT_QC_REQUIRED");
        if (!(v.get("passed") instanceof Boolean passed)) throw new Problem(400, "INVALID_INPUT");
        var q = new QualityReview();
        q.orderId = id;
        q.passed = passed;
        q.observedQuantity = lastGood(o);
        q.acceptedQuantity = StockService.integer(v, "acceptedQuantity", false);
        if (q.acceptedQuantity > q.observedQuantity || (!passed && q.acceptedQuantity != 0))
          throw new Problem(400, "INVALID_ACCEPTED_QUANTITY");
        q.note = StockService.text(v, "note", 2000);
        q.actorId = access.current().id;
        q.createdBy = access.current().username;
        q.createdAt = clock.instant();
        db.save(q);
        if (passed) {
          o.acceptedQuantity = q.acceptedQuantity;
          o.status = "READY";
        } else {
          o.status = "IN_PROGRESS";
          steps(id).getLast().finished = false;
        }
        event(o, passed ? "QC_PASS" : "QC_FAIL", q.note);
      }
      case "receive" -> {
        state(o, "READY");
        var ref = StockService.text(v, "reference", 200);
        o.materialCost = ProductionPolicy.money(stock.materialCost(id));
        o.laborCost =
            ProductionPolicy.money(
                db
                    .query(
                        ProductionReport.class,
                        "from ProductionReport where orderId=?1 and voided=false",
                        id)
                    .stream()
                    .map(r -> r.laborCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        o.totalCost = ProductionPolicy.money(o.materialCost.add(o.laborCost));
        o.lossCost = o.acceptedQuantity == 0 ? o.totalCost : BigDecimal.ZERO;
        o.receiptReference = ref;
        stock.productionReceipt(o, ref);
        o.status = "COMPLETE";
        o.finishedAt = clock.instant();
        event(o, "RECEIVE", ref);
      }
      case "cancel" -> {
        if (!Set.of("DRAFT", "RELEASED").contains(o.status))
          throw new Problem(409, "INVALID_STATE");
        for (var l : materials(id))
          if (stock.netQuantity(l.id).signum() != 0)
            throw new Problem(409, "RETURN_MATERIAL_FIRST");
        o.status = "CANCELLED";
        event(o, "CANCEL", StockService.text(v, "note", 2000));
      }
      case "void-report" -> {
        state(o, "IN_PROGRESS");
        var r = db.get(ProductionReport.class, StockService.longValue(v, "reportId"));
        if (!r.orderId.equals(id) || r.voided) throw new Problem(409, "INVALID_REPORT");
        var s = ownedStep(o, r.stepId);
        for (var later : steps(id))
          if (later.sequence > s.sequence && (later.finished || !reports(later.id).isEmpty()))
            throw new Problem(409, "DOWNSTREAM_STARTED");
        r.voided = true;
        r.voidedBy = access.current().id;
        r.voidReason = StockService.text(v, "note", 2000);
        s.finished = false;
        event(o, "VOID_REPORT", r.id + " · " + r.reference);
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    o.revision++;
    o.updatedAt = clock.instant();
    command.stamp().resultId = o.id;
  }

  /** 当前派工人员报工或结束工序，不能跳工序、超量或替他人报工。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void stepAction(Long id, String action, Map<String, Object> v) {
    access.require("execute");
    db.lock(Department.class, 1L);
    var s = db.get(WorkStep.class, id);
    var o = scope.get(s.orderId, "execute");
    if (!Objects.equals(s.operatorId, access.current().id)) throw new Problem(403, "NOT_ASSIGNED");
    var command = commands.open("STEP_" + id + "_" + action, v);
    if (!command.fresh()) return;
    state(o, "IN_PROGRESS");
    if (s.finished) throw new Problem(409, "STEP_FINISHED");
    var incoming = incoming(o, s);
    var active = reports(id);
    int recorded = active.stream().mapToInt(r -> r.goodQuantity + r.rejectedQuantity).sum();
    if (action.equals("report")) {
      int good = StockService.integer(v, "goodQuantity", false),
          rejected = StockService.integer(v, "rejectedQuantity", false);
      var hours = ProductionPolicy.hours(StockService.decimal(v, "hours"));
      if (good == 0 && rejected == 0 && hours.signum() == 0) throw new Problem(400, "EMPTY_REPORT");
      ProductionPolicy.output(incoming, recorded, good, rejected);
      var r = new ProductionReport();
      r.orderId = o.id;
      r.stepId = id;
      r.goodQuantity = good;
      r.rejectedQuantity = rejected;
      r.hours = hours;
      r.laborCost = ProductionPolicy.money(hours.multiply(s.hourlyRate));
      r.actorId = access.current().id;
      r.createdBy = access.current().username;
      r.reference = StockService.text(v, "reference", 200);
      r.note = BomService.optional(StockService.string(v, "note"), 2000);
      r.createdAt = clock.instant();
      db.save(r);
      command.stamp().resultId = r.id;
      event(o, "REPORT", s.sequence + " · " + r.reference);
    } else if (action.equals("finish")) {
      if (recorded != incoming) throw new Problem(409, "UNACCOUNTED_OUTPUT");
      s.finished = true;
      event(o, "FINISH_STEP", String.valueOf(s.sequence));
      command.stamp().resultId = s.id;
    } else throw new Problem(404, "NOT_FOUND");
    o.revision++;
    o.updatedAt = clock.instant();
  }

  /** 已授权工单清单，操作员仅见派给自己的工单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> list() {
    access.require("production.read");
    return db.all(ProductionOrder.class).reversed().stream()
        .filter(scope::visible)
        .map(this::view)
        .toList();
  }

  /** 工单快照、工序实绩、领退凭证和独立终检历史；成本单独授权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    var o = scope.get(id, "production.read");
    var ms =
        materials(id).stream()
            .map(
                l -> {
                  Map<String, Object> r = new LinkedHashMap<>();
                  r.put("id", l.id);
                  r.put("itemId", l.itemId);
                  r.put("itemCode", l.itemCode);
                  r.put("itemName", l.itemName);
                  r.put("unit", l.unit);
                  r.put("requiredQuantity", l.requiredQuantity);
                  r.put("authorizedQuantity", l.authorizedQuantity);
                  r.put("netQuantity", stock.netQuantity(l.id));
                  r.put("adjustmentNote", l.adjustmentNote);
                  return r;
                })
            .toList();
    var ss =
        steps(id).stream()
            .map(
                s -> {
                  var rs = reports(s.id);
                  Map<String, Object> r = new LinkedHashMap<>();
                  r.put("id", s.id);
                  r.put("sequence", s.sequence);
                  r.put("name", s.name);
                  r.put("stationName", s.stationName);
                  r.put("operatorId", s.operatorId);
                  r.put("operatorName", s.operatorName);
                  r.put("finished", s.finished);
                  r.put(
                      "incoming",
                      s.sequence == 1 ? o.plannedQuantity : good(steps(id).get(s.sequence - 2).id));
                  r.put("goodQuantity", good(s.id));
                  r.put("rejectedQuantity", rs.stream().mapToInt(z -> z.rejectedQuantity).sum());
                  r.put(
                      "hours",
                      rs.stream().map(z -> z.hours).reduce(BigDecimal.ZERO, BigDecimal::add));
                  if (scope.cost()) {
                    r.put("hourlyRate", s.hourlyRate);
                    r.put(
                        "laborCost",
                        rs.stream().map(z -> z.laborCost).reduce(BigDecimal.ZERO, BigDecimal::add));
                  }
                  return r;
                })
            .toList();
    var rs =
        db
            .query(ProductionReport.class, "from ProductionReport where orderId=?1 order by id", id)
            .stream()
            .map(
                r -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("id", r.id);
                  m.put("stepId", r.stepId);
                  m.put("goodQuantity", r.goodQuantity);
                  m.put("rejectedQuantity", r.rejectedQuantity);
                  m.put("hours", r.hours);
                  m.put("createdBy", r.createdBy);
                  m.put("createdAt", r.createdAt);
                  m.put("reference", r.reference);
                  m.put("note", r.note);
                  m.put("voided", r.voided);
                  m.put("voidReason", r.voidReason);
                  m.put("voidedBy", r.voidedBy);
                  if (scope.cost()) m.put("laborCost", r.laborCost);
                  return m;
                })
            .toList();
    return Map.of(
        "order",
        view(o),
        "materials",
        ms,
        "steps",
        ss,
        "reports",
        rs,
        "reviews",
        db.query(QualityReview.class, "from QualityReview where orderId=?1 order by id", id),
        "events",
        db.query(BusinessEvent.class, "from BusinessEvent where orderId=?1 order by id", id),
        "movements",
        db
            .query(StockMovement.class, "from StockMovement where orderId=?1 order by id", id)
            .stream()
            .map(stock::view)
            .toList());
  }

  /** 安全摘要按成本权限组合，不直接序列化带成本实体。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> view(ProductionOrder o) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("id", o.id);
    r.put("number", o.number);
    r.put("bomId", o.bomId);
    r.put("bomVersion", o.bomVersion);
    r.put("departmentId", o.departmentId);
    r.put("productId", o.productId);
    r.put("productCode", o.productCode);
    r.put("productName", o.productName);
    r.put("unit", o.unit);
    r.put("plannedQuantity", o.plannedQuantity);
    r.put("acceptedQuantity", o.acceptedQuantity);
    r.put("dueDate", o.dueDate);
    r.put("status", o.status);
    r.put("revision", o.revision);
    r.put("note", o.note);
    r.put("sourceReference", o.sourceReference);
    r.put("createdBy", o.createdBy);
    r.put("createdAt", o.createdAt);
    r.put("updatedAt", o.updatedAt);
    r.put("finishedAt", o.finishedAt);
    r.put("receiptReference", o.receiptReference);
    r.put("materialVarianceNote", o.materialVarianceNote);
    if (scope.cost()) {
      r.put("materialCost", o.materialCost);
      r.put("laborCost", o.laborCost);
      r.put("totalCost", o.totalCost);
      r.put("lossCost", o.lossCost);
    }
    return r;
  }

  /** 固定查询工单组件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<OrderMaterial> materials(Long id) {
    return db.query(OrderMaterial.class, "from OrderMaterial where orderId=?1 order by id", id);
  }

  /** 固定查询顺序工序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<WorkStep> steps(Long id) {
    return db.query(WorkStep.class, "from WorkStep where orderId=?1 order by sequence", id);
  }

  private List<ProductionReport> reports(Long step) {
    return db.query(
        ProductionReport.class, "from ProductionReport where stepId=?1 and voided=false", step);
  }

  private int good(Long step) {
    return reports(step).stream().mapToInt(r -> r.goodQuantity).sum();
  }

  private int lastGood(ProductionOrder o) {
    return good(steps(o.id).getLast().id);
  }

  private int incoming(ProductionOrder o, WorkStep s) {
    if (s.sequence == 1) return o.plannedQuantity;
    var prev = steps(o.id).get(s.sequence - 2);
    if (!prev.finished) throw new Problem(409, "PREVIOUS_STEP_OPEN");
    return good(prev.id);
  }

  private WorkStep ownedStep(ProductionOrder o, Long id) {
    var s = db.get(WorkStep.class, id);
    if (!s.orderId.equals(o.id)) throw new Problem(404, "NOT_FOUND");
    return s;
  }

  private void revision(ProductionOrder o, Map<String, Object> v) {
    if (!Objects.equals(o.revision, StockService.longValue(v, "revision")))
      throw new Problem(409, "STALE_VERSION");
  }

  private void state(ProductionOrder o, String expected) {
    if (!o.status.equals(expected)) throw new Problem(409, "INVALID_STATE");
  }

  private void event(ProductionOrder o, String kind, String note) {
    var e = new BusinessEvent();
    e.orderId = o.id;
    e.kind = kind;
    e.note = note;
    e.actorId = access.current().id;
    e.createdBy = access.current().username;
    e.createdAt = clock.instant();
    db.save(e);
    access.audit("PRODUCTION_" + kind, o.id, o.departmentId);
  }
}
