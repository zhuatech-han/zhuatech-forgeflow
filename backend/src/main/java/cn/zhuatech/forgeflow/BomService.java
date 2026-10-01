// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 单层BOM及顺序工艺草稿维护、启用冻结和历史版本保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class BomService {
  final Store db;
  final AccessService access;
  final Clock clock;

  public BomService(Store db, AccessService access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** BOM输入按每一件成品定义用量，不接收客户端成本计算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(
      Long productId,
      Integer versionNumber,
      String description,
      Long revision,
      List<Component> components,
      List<Operation> operations) {}

  /** 物料及每件消耗量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Component(Long itemId, BigDecimal perUnit) {}

  /** 工序依列表顺序执行，标准费率由工位固化。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Operation(Long stationId, String name) {}

  /** 创建或编辑尚未下达使用的草稿，旧版本号拒绝覆盖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BillOfMaterials save(Long id, Input v) {
    access.require("bom.write");
    db.lock(Department.class, 1L);
    var p = db.get(Item.class, v.productId);
    access.department(p.departmentId);
    if (!p.enabled || !p.kind.equals("FINISHED")) throw new Problem(400, "INVALID_PRODUCT");
    if (v.versionNumber == null
        || v.versionNumber < 1
        || v.versionNumber > 9999
        || v.components == null
        || v.components.isEmpty()
        || v.components.size() > 50
        || v.operations == null
        || v.operations.isEmpty()
        || v.operations.size() > 50) throw new Problem(400, "INVALID_BOM");
    var b = id == null ? new BillOfMaterials() : db.get(BillOfMaterials.class, id);
    if (id != null) {
      access.department(b.departmentId);
      checkRevision(b, v.revision);
      if (!b.status.equals("DRAFT")) throw new Problem(409, "BOM_FROZEN");
      if (!b.productId.equals(p.id)) throw new Problem(409, "IDENTITY_FROZEN");
      if (!db.query(ProductionOrder.class, "from ProductionOrder where bomId=?1", b.id).isEmpty())
        throw new Problem(409, "BOM_REFERENCED");
    }
    b.productId = p.id;
    b.departmentId = p.departmentId;
    b.versionNumber = v.versionNumber;
    b.description = optional(v.description, 2000);
    b.updatedAt = clock.instant();
    if (id == null) {
      b.createdAt = b.updatedAt;
      b.createdBy = access.current().username;
      db.save(b);
    } else {
      for (var c : components(id)) db.delete(c);
      for (var c : operations(id)) db.delete(c);
      b.revision++;
    }
    Set<Long> seen = new HashSet<>();
    for (var input : v.components) {
      if (input == null) throw new Problem(400, "INVALID_BOM");
      var x = db.get(Item.class, input.itemId);
      if (!seen.add(x.id)
          || !x.enabled
          || !x.kind.equals("MATERIAL")
          || !x.departmentId.equals(b.departmentId)) throw new Problem(400, "INVALID_COMPONENT");
      var c = new BomComponent();
      c.bomId = b.id;
      c.itemId = x.id;
      c.itemCode = x.code;
      c.itemName = x.name;
      c.unit = x.unit;
      c.perUnit = ProductionPolicy.quantity(input.perUnit, true);
      db.save(c);
    }
    int sequence = 1;
    for (var input : v.operations) {
      if (input == null) throw new Problem(400, "INVALID_BOM");
      var x = db.get(Station.class, input.stationId);
      if (!x.enabled || !x.departmentId.equals(b.departmentId))
        throw new Problem(400, "INVALID_STATION");
      var c = new BomOperation();
      c.bomId = b.id;
      c.sequence = sequence++;
      c.stationId = x.id;
      c.stationName = x.name;
      c.name = AdminService.text(input.name, 120);
      c.hourlyRate = x.hourlyRate;
      db.save(c);
    }
    access.audit("BOM_SAVE", b.id, b.departmentId);
    return b;
  }

  /** 启用使BOM冻结；归档仅阻止新下达，不改已有工单快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BillOfMaterials state(Long id, String action, Long revision) {
    access.require("bom.write");
    db.lock(Department.class, 1L);
    var b = db.get(BillOfMaterials.class, id);
    access.department(b.departmentId);
    checkRevision(b, revision);
    if (action.equals("activate") && b.status.equals("DRAFT")) {
      if (components(id).isEmpty() || operations(id).isEmpty())
        throw new Problem(409, "INCOMPLETE_BOM");
      var p = db.get(Item.class, b.productId);
      if (!p.enabled) throw new Problem(409, "DISABLED_MASTER");
      for (var c : components(id))
        if (!db.get(Item.class, c.itemId).enabled) throw new Problem(409, "DISABLED_MASTER");
      for (var c : operations(id))
        if (!db.get(Station.class, c.stationId).enabled) throw new Problem(409, "DISABLED_MASTER");
      b.status = "ACTIVE";
    } else if (action.equals("archive") && b.status.equals("ACTIVE")) b.status = "ARCHIVED";
    else throw new Problem(409, "INVALID_STATE");
    b.revision++;
    b.updatedAt = clock.instant();
    access.audit("BOM_" + action, id, b.departmentId);
    return b;
  }

  /** 删除未被工单引用的草稿，保留启用版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(Long id) {
    access.require("bom.write");
    db.lock(Department.class, 1L);
    var b = db.get(BillOfMaterials.class, id);
    access.department(b.departmentId);
    if (!b.status.equals("DRAFT")
        || !db.query(ProductionOrder.class, "from ProductionOrder where bomId=?1", id).isEmpty())
      throw new Problem(409, "BOM_REFERENCED");
    for (var c : components(id)) db.delete(c);
    for (var c : operations(id)) db.delete(c);
    access.audit("BOM_DELETE", id, b.departmentId);
    db.delete(b);
  }

  /** 已授权范围内BOM详情，标准工位费率按成本权限屏蔽。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    access.require("bom.read");
    var b = db.get(BillOfMaterials.class, id);
    access.department(b.departmentId);
    var ops =
        operations(id).stream()
            .map(
                x -> {
                  Map<String, Object> r = new LinkedHashMap<>();
                  r.put("id", x.id);
                  r.put("stationId", x.stationId);
                  r.put("sequence", x.sequence);
                  r.put("name", x.name);
                  r.put("stationName", x.stationName);
                  if (access.role().permissions.contains("cost")) r.put("hourlyRate", x.hourlyRate);
                  return r;
                })
            .toList();
    return Map.of(
        "bom",
        b,
        "product",
        db.get(Item.class, b.productId).name,
        "components",
        components(id),
        "operations",
        ops);
  }

  /** 查询BOM组件，使用绑定参数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<BomComponent> components(Long id) {
    return db.query(BomComponent.class, "from BomComponent where bomId=?1 order by id", id);
  }

  /** 查询顺序工艺，工序顺序由后端维护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<BomOperation> operations(Long id) {
    return db.query(BomOperation.class, "from BomOperation where bomId=?1 order by sequence", id);
  }

  private void checkRevision(BillOfMaterials b, Long revision) {
    if (revision == null || b.revision != revision) throw new Problem(409, "STALE_VERSION");
  }

  static String optional(String value, int limit) {
    var s = value == null ? "" : value.trim();
    if (s.length() > limit) throw new Problem(400, "INVALID_INPUT");
    return s;
  }
}
