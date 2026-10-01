// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 仓库实际收发、按授权领料、原成本退料及带快照的盘点。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class StockService {
  final Store db;
  final AccessService access;
  final ProductionAccess scope;
  final CommandService commands;
  final Clock clock;

  public StockService(
      Store db,
      AccessService access,
      ProductionAccess scope,
      CommandService commands,
      Clock clock) {
    this.db = db;
    this.access = access;
    this.scope = scope;
    this.commands = commands;
    this.clock = clock;
  }

  /** 期初/收货、成品发出或盘点；余额不能通过主数据改写。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void itemAction(Long id, String action, Map<String, Object> v) {
    access.require("stock.write");
    db.lock(Department.class, 1L);
    var x = db.get(Item.class, id);
    access.department(x.departmentId);
    var command = commands.open("ITEM_" + id + "_" + action, v);
    if (!command.fresh()) return;
    if (!x.enabled) throw new Problem(409, "DISABLED_MASTER");
    var reference = text(v, "reference", 200);
    var note = BomService.optional(string(v, "note"), 2000);
    BigDecimal q, value, delta;
    switch (action) {
      case "receive" -> {
        q = ProductionPolicy.quantity(decimal(v, "quantity"), true);
        if (x.kind.equals("FINISHED") && q.stripTrailingZeros().scale() > 0)
          throw new Problem(400, "INVALID_PIECES");
        value = ProductionPolicy.money(q.multiply(ProductionPolicy.rate(decimal(v, "price"))));
        delta = q;
      }
      case "dispatch" -> {
        if (!x.kind.equals("FINISHED")) throw new Problem(400, "FINISHED_ONLY");
        q = ProductionPolicy.quantity(decimal(v, "quantity"), true);
        if (q.stripTrailingZeros().scale() > 0) throw new Problem(400, "INVALID_PIECES");
        value = ProductionPolicy.cost(x.quantity, x.inventoryValue, q).negate();
        delta = q.negate();
      }
      case "count" -> {
        q = ProductionPolicy.quantity(decimal(v, "quantity"), false);
        if (x.kind.equals("FINISHED") && q.stripTrailingZeros().scale() > 0)
          throw new Problem(400, "INVALID_PIECES");
        var book = ProductionPolicy.quantity(decimal(v, "bookQuantity"), false);
        if (book.compareTo(x.quantity) != 0) throw new Problem(409, "STALE_STOCK_COUNT");
        note = text(v, "note", 2000);
        delta = q.subtract(x.quantity);
        q = delta.abs();
        if (delta.signum() == 0) throw new Problem(400, "NO_CHANGE");
        value =
            delta.signum() < 0
                ? ProductionPolicy.cost(x.quantity, x.inventoryValue, q).negate()
                : ProductionPolicy.money(
                    x.quantity.signum() == 0
                        ? q.multiply(ProductionPolicy.rate(decimal(v, "price")))
                        : x.inventoryValue.multiply(q).divide(x.quantity, 2, RoundingMode.HALF_UP));
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    var m =
        movement(
            x, action.toUpperCase(Locale.ROOT), q, delta, value, reference, note, null, null, null);
    command.stamp().resultId = m.id;
  }

  /** 领料不得超可领上限；退料引用原领料并恢复原成本尾差。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void materialAction(Long id, String action, Map<String, Object> v) {
    access.require("stock.write");
    db.lock(Department.class, 1L);
    var line = db.get(OrderMaterial.class, id);
    var o = scope.get(line.orderId, "stock.write");
    var c = commands.open("MATERIAL_" + id + "_" + action, v);
    if (!c.fresh()) return;
    if (!Set.of("RELEASED", "IN_PROGRESS").contains(o.status))
      throw new Problem(409, "INVALID_STATE");
    var x = db.get(Item.class, line.itemId);
    if (!x.enabled) throw new Problem(409, "DISABLED_MASTER");
    var q = ProductionPolicy.quantity(decimal(v, "quantity"), true);
    var ref = text(v, "reference", 200);
    var note = BomService.optional(string(v, "note"), 2000);
    StockMovement m;
    if (action.equals("issue")) {
      if (netQuantity(id).add(q).compareTo(line.authorizedQuantity) > 0)
        throw new Problem(409, "ISSUE_EXCEEDS_AUTHORIZATION");
      m =
          movement(
              x,
              "ISSUE",
              q,
              q.negate(),
              ProductionPolicy.cost(x.quantity, x.inventoryValue, q).negate(),
              ref,
              note,
              o.id,
              id,
              null);
    } else if (action.equals("return")) {
      var source = db.get(StockMovement.class, longValue(v, "sourceId"));
      if (!source.kind.equals("ISSUE")
          || !Objects.equals(source.materialId, id)
          || !Objects.equals(source.orderId, o.id)) throw new Problem(409, "INVALID_RETURN_SOURCE");
      var returns =
          db.query(
              StockMovement.class,
              "from StockMovement where sourceId=?1 and kind='RETURN'",
              source.id);
      var returned = returns.stream().map(z -> z.quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
      var restored =
          returns.stream().map(z -> z.valueDelta).reduce(BigDecimal.ZERO, BigDecimal::add);
      var value =
          ProductionPolicy.returnCost(
              source.quantity, source.valueDelta.abs(), returned, restored, q);
      m = movement(x, "RETURN", q, q, value, ref, note, o.id, id, source.id);
    } else throw new Problem(404, "NOT_FOUND");
    o.revision++;
    o.updatedAt = clock.instant();
    c.stamp().resultId = m.id;
  }

  /** 终检后一次成品入库；零良品结单不产生虚假入库。调用方已加锁、验证并计算总成本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void productionReceipt(ProductionOrder o, String reference) {
    access.require("stock.write");
    var x = db.get(Item.class, o.productId);
    access.department(x.departmentId);
    if (!x.enabled) throw new Problem(409, "DISABLED_MASTER");
    if (o.acceptedQuantity > 0) {
      var q = new BigDecimal(o.acceptedQuantity);
      movement(x, "PRODUCE", q, q, o.totalCost, reference, "", o.id, null, null);
    }
  }

  /** 从不可变领退流水汇总工单净用料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BigDecimal netQuantity(Long materialId) {
    return db
        .query(StockMovement.class, "from StockMovement where materialId=?1", materialId)
        .stream()
        .map(x -> x.quantityDelta.negate())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** 净领料成本使用原领料和退料价值，不用当前采购价重算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BigDecimal materialCost(Long orderId) {
    return db
        .query(
            StockMovement.class,
            "from StockMovement where orderId=?1 and kind in ('ISSUE','RETURN')",
            orderId)
        .stream()
        .map(x -> x.valueDelta.negate())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** 库存流水响应按单独成本权限屏蔽价值，保留凭证与原单引用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> view(StockMovement x) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("id", x.id);
    r.put("itemId", x.itemId);
    r.put("orderId", x.orderId);
    r.put("materialId", x.materialId);
    r.put("sourceId", x.sourceId);
    r.put("kind", x.kind);
    r.put("quantity", x.quantity);
    r.put("quantityDelta", x.quantityDelta);
    r.put("balanceQuantity", x.balanceQuantity);
    r.put("reference", x.reference);
    r.put("note", x.note);
    r.put("createdBy", x.createdBy);
    r.put("createdAt", x.createdAt);
    if (scope.cost()) {
      r.put("valueDelta", x.valueDelta);
      r.put("balanceValue", x.balanceValue);
    }
    return r;
  }

  private StockMovement movement(
      Item x,
      String kind,
      BigDecimal q,
      BigDecimal delta,
      BigDecimal value,
      String reference,
      String note,
      Long orderId,
      Long materialId,
      Long sourceId) {
    var newQuantity = ProductionPolicy.quantity(x.quantity.add(delta), false);
    var newValue = ProductionPolicy.money(x.inventoryValue.add(value));
    if (newQuantity.signum() == 0 && newValue.signum() != 0)
      throw new Problem(409, "STOCK_VALUE_MISMATCH");
    x.quantity = newQuantity;
    x.inventoryValue = newValue;
    var m = new StockMovement();
    m.itemId = x.id;
    m.departmentId = x.departmentId;
    m.orderId = orderId;
    m.materialId = materialId;
    m.sourceId = sourceId;
    m.kind = kind;
    m.quantity = q;
    m.quantityDelta = delta;
    m.valueDelta = value;
    m.balanceQuantity = x.quantity;
    m.balanceValue = x.inventoryValue;
    m.reference = reference;
    m.note = note;
    m.actorId = access.current().id;
    m.createdBy = access.current().username;
    m.createdAt = clock.instant();
    db.save(m);
    access.audit("STOCK_" + kind, m.id, x.departmentId);
    return m;
  }

  static String string(Map<String, Object> v, String key) {
    var x = v.get(key);
    return x == null ? null : x instanceof String s ? s : throwInput();
  }

  private static String throwInput() {
    throw new Problem(400, "INVALID_INPUT");
  }

  static String text(Map<String, Object> v, String key, int limit) {
    return AdminService.text(string(v, key), limit);
  }

  static BigDecimal decimal(Map<String, Object> v, String key) {
    try {
      return new BigDecimal(String.valueOf(v.get(key)));
    } catch (NumberFormatException e) {
      throw new Problem(400, "INVALID_INPUT");
    }
  }

  static Long longValue(Map<String, Object> v, String key) {
    try {
      return Long.valueOf(String.valueOf(v.get(key)));
    } catch (NumberFormatException e) {
      throw new Problem(400, "INVALID_INPUT");
    }
  }

  static int integer(Map<String, Object> v, String key, boolean positive) {
    try {
      return ProductionPolicy.pieces(Integer.valueOf(String.valueOf(v.get(key))), positive);
    } catch (NumberFormatException e) {
      throw new Problem(400, "INVALID_INPUT");
    }
  }
}
