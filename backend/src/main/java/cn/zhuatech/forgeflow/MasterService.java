// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 物料、成品和工位维护；不允许通过档案编辑覆盖库存。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class MasterService {
  final Store db;
  final AccessService access;

  public MasterService(Store db, AccessService access) {
    this.db = db;
    this.access = access;
  }

  /** 档案输入，不接收库存余额或历史成本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(
      String code,
      String name,
      String kind,
      String unit,
      String specification,
      Long departmentId,
      Boolean enabled,
      BigDecimal reorderLevel,
      BigDecimal hourlyRate) {}

  /** 按部门返回物料和工位；成本需单独权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<?> list(String type) {
    access.require("master.read");
    return switch (type) {
      case "items" ->
          db.all(Item.class).stream()
              .filter(x -> access.visible(x.departmentId))
              .map(this::itemView)
              .toList();
      case "stations" ->
          db.all(Station.class).stream()
              .filter(x -> access.visible(x.departmentId))
              .map(this::stationView)
              .toList();
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 新建或修改主数据，引用后不允许改单位、类型或部门。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(String type, Long id, Input v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    access.require("master.write");
    db.lock(Department.class, 1L);
    db.get(Department.class, v.departmentId);
    access.department(v.departmentId);
    var code = AdminService.text(v.code, 60);
    var name = AdminService.text(v.name, 120);
    if (type.equals("items")) {
      var x = id == null ? new Item() : db.get(Item.class, id);
      if (id != null) access.department(x.departmentId);
      var unit = AdminService.text(v.unit, 20);
      if (v.kind == null || !Set.of("MATERIAL", "FINISHED").contains(v.kind))
        throw new Problem(400, "INVALID_KIND");
      if (id != null
          && (!x.departmentId.equals(v.departmentId)
              || !x.kind.equals(v.kind)
              || !x.unit.equals(unit))) throw new Problem(409, "IDENTITY_FROZEN");
      x.code = code;
      x.name = name;
      x.kind = v.kind;
      x.unit = unit;
      x.departmentId = v.departmentId;
      x.enabled = Boolean.TRUE.equals(v.enabled);
      x.reorderLevel = ProductionPolicy.quantity(v.reorderLevel, false);
      x.specification = v.specification == null ? "" : v.specification.trim();
      if (x.specification.length() > 400) throw new Problem(400, "INVALID_INPUT");
      if (id == null) db.save(x);
      access.audit("ITEM_SAVE", x.id, x.departmentId);
      return itemView(x);
    }
    if (type.equals("stations")) {
      access.require("cost");
      var x = id == null ? new Station() : db.get(Station.class, id);
      if (id != null) {
        access.department(x.departmentId);
        if (!x.departmentId.equals(v.departmentId)) throw new Problem(409, "IDENTITY_FROZEN");
      }
      x.code = code;
      x.name = name;
      x.departmentId = v.departmentId;
      x.hourlyRate = ProductionPolicy.rate(v.hourlyRate);
      x.enabled = Boolean.TRUE.equals(v.enabled);
      if (id == null) db.save(x);
      access.audit("STATION_SAVE", x.id, x.departmentId);
      return stationView(x);
    }
    throw new Problem(404, "NOT_FOUND");
  }

  /** 删除未被引用档案；历史引用由数据库外键保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(String type, Long id) {
    access.require("master.write");
    db.lock(Department.class, 1L);
    if (type.equals("items")) {
      var x = db.get(Item.class, id);
      access.department(x.departmentId);
      access.audit("ITEM_DELETE", id, x.departmentId);
      db.delete(x);
    } else if (type.equals("stations")) {
      var x = db.get(Station.class, id);
      access.department(x.departmentId);
      access.audit("STATION_DELETE", id, x.departmentId);
      db.delete(x);
    } else throw new Problem(404, "NOT_FOUND");
  }

  /** 最多500条物料导入；任一无效则整批回滚。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Integer> importItems(List<Input> rows) {
    access.require("master.write");
    if (rows == null || rows.isEmpty() || rows.size() > 500)
      throw new Problem(400, "INVALID_IMPORT");
    for (var row : rows) save("items", null, row);
    return Map.of("imported", rows.size());
  }

  /** 返回安全物料响应，不向无成本权限者泄露库存价值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> itemView(Item x) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("id", x.id);
    r.put("code", x.code);
    r.put("name", x.name);
    r.put("kind", x.kind);
    r.put("unit", x.unit);
    r.put("specification", x.specification);
    r.put("departmentId", x.departmentId);
    r.put("enabled", x.enabled);
    r.put("quantity", x.quantity);
    r.put("reorderLevel", x.reorderLevel);
    if (access.role().permissions.contains("cost")) r.put("inventoryValue", x.inventoryValue);
    return r;
  }

  /** 工位费率只提供给有成本权限的账号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> stationView(Station x) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("id", x.id);
    r.put("code", x.code);
    r.put("name", x.name);
    r.put("departmentId", x.departmentId);
    r.put("enabled", x.enabled);
    if (access.role().permissions.contains("cost")) r.put("hourlyRate", x.hourlyRate);
    return r;
  }
}
