// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 首次空库初始化生产岗位及可选虚构物料，不伪造库存或产量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;
  final boolean demo;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${forgeflow.admin-username}") String username,
      @Value("${forgeflow.admin-password}") String password,
      @Value("${forgeflow.seed-demo}") boolean demo) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
    this.demo = demo;
  }

  /** 初始化独立强密码；已有账号和业务记录不被重启覆盖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    if (!username.matches("[a-zA-Z0-9_.-]{3,60}")) throw new Problem(400, "INVALID_USERNAME");
    var d = new Department();
    d.name = "主车间 / Main workshop";
    db.save(d);
    String[][] definitions = {
      {"dashboard", "工作台 / Overview"},
      {"master.read", "查看物料工位 / View masters"},
      {"master.write", "维护物料工位 / Edit masters"},
      {"bom.read", "查看BOM / View BOMs"},
      {"bom.write", "维护工艺版本 / Edit BOMs"},
      {"production.read", "查看生产工单 / View production"},
      {"production.manage", "下达派工与结单 / Plan and close"},
      {"execute", "工序报工 / Shop-floor reporting"},
      {"stock.read", "库存与流水 / Stock view"},
      {"stock.write", "收发料和盘点 / Stock operations"},
      {"quality", "终检 / Final inspection"},
      {"cost", "查看成本 / View costs"},
      {"report", "报表与导出 / Reports"},
      {"audit", "操作审计 / Audit"},
      {"admin", "系统管理 / Administration"}
    };
    Set<String> all = new HashSet<>();
    for (var row : definitions) {
      var p = new Permission();
      p.code = row[0];
      p.name = row[1];
      db.save(p);
      all.add(row[0]);
    }
    var ar = role("管理员 / Administrator", all, "ALL");
    role(
        "生产计划 / Planner",
        Set.of(
            "dashboard",
            "master.read",
            "master.write",
            "bom.read",
            "bom.write",
            "production.read",
            "production.manage",
            "stock.read",
            "cost",
            "report"),
        "DEPARTMENT");
    role(
        "仓管 / Storekeeper",
        Set.of(
            "dashboard",
            "master.read",
            "bom.read",
            "production.read",
            "stock.read",
            "stock.write",
            "cost",
            "report"),
        "DEPARTMENT");
    role(
        "操作员 / Operator",
        Set.of("dashboard", "master.read", "production.read", "execute"),
        "ASSIGNED");
    role(
        "质检员 / Quality reviewer",
        Set.of("dashboard", "master.read", "production.read", "quality", "report"),
        "DEPARTMENT");
    role(
        "只读 / Viewer",
        Set.of("dashboard", "master.read", "bom.read", "production.read", "stock.read", "report"),
        "DEPARTMENT");
    var a = new Account();
    a.username = username;
    a.displayName = "管理员 / Administrator";
    a.passwordHash = encoder.encode(password);
    a.roleId = ar.id;
    a.departmentId = d.id;
    a.enabled = true;
    db.save(a);
    String[][] menus = {
      {"dashboard", "工作台", "Overview", "dashboard"},
      {"orders", "生产工单", "Production orders", "production.read"},
      {"boms", "BOM与工艺", "BOMs & routing", "bom.read"},
      {"items", "物料与成品", "Items & stock", "master.read"},
      {"stations", "工位", "Workstations", "master.read"},
      {"movements", "库存流水", "Stock ledger", "stock.read"},
      {"reports", "生产统计", "Production reports", "report"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles & permissions", "admin"},
      {"departments", "车间部门", "Departments", "admin"},
      {"menus", "菜单管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "业务字典", "Dictionaries", "admin"},
      {"settings", "系统参数", "Settings", "admin"},
      {"audit", "操作审计", "Audit", "audit"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    for (var e :
        Map.of(
                "companyName",
                "知华生产 / ZhuaTech ForgeFlow",
                "currency",
                "CNY",
                "timezone",
                "Asia/Shanghai")
            .entrySet()) {
      var s = new SystemSetting();
      s.code = e.getKey();
      s.value = e.getValue();
      db.save(s);
    }
    for (var e :
        new String[][] {
          {"material", "材料", "Material"}, {"labor", "人工", "Labor"}, {"quality", "质量", "Quality"}
        }) {
      var x = new DictionaryEntry();
      x.type = "variance_reason";
      x.code = e[0];
      x.name = e[1];
      x.nameEn = e[2];
      db.save(x);
    }
    if (demo) {
      var p = item("DEMO-FG", "示例支架 / Demo bracket", "FINISHED", "件 / pc", d.id);
      var m = item("DEMO-STEEL", "示例钢材 / Demo steel", "MATERIAL", "kg", d.id);
      var fastener = item("DEMO-BOLT", "示例螺栓 / Demo bolt", "MATERIAL", "件 / pc", d.id);
      var s1 = station("DEMO-CUT", "示例切割 / Demo cutting", new BigDecimal("60"), d.id);
      var s2 = station("DEMO-ASSEMBLY", "示例装配 / Demo assembly", new BigDecimal("50"), d.id);
      var b = new BillOfMaterials();
      b.productId = p.id;
      b.versionNumber = 1;
      b.departmentId = d.id;
      b.description = "虚构学习数据 / Fictional learning data";
      b.createdBy = username;
      b.createdAt = Instant.now();
      b.updatedAt = b.createdAt;
      db.save(b);
      for (var row : List.of(m, fastener)) {
        var c = new BomComponent();
        c.bomId = b.id;
        c.itemId = row.id;
        c.itemCode = row.code;
        c.itemName = row.name;
        c.unit = row.unit;
        c.perUnit = new BigDecimal(row.id.equals(m.id) ? "0.012390" : "2.000000");
        db.save(c);
      }
      var seq = 1;
      for (var s : List.of(s1, s2)) {
        var op = new BomOperation();
        op.bomId = b.id;
        op.sequence = seq++;
        op.stationId = s.id;
        op.stationName = s.name;
        op.name = s.name;
        op.hourlyRate = s.hourlyRate;
        db.save(op);
      }
    }
  }

  private AccessRole role(String name, Set<String> ps, String scope) {
    var r = new AccessRole();
    r.name = name;
    r.permissions = new HashSet<>(ps);
    r.scope = scope;
    return db.save(r);
  }

  private Item item(String code, String name, String kind, String unit, Long department) {
    var x = new Item();
    x.code = code;
    x.name = name;
    x.kind = kind;
    x.unit = unit;
    x.departmentId = department;
    return db.save(x);
  }

  private Station station(String code, String name, BigDecimal rate, Long department) {
    var x = new Station();
    x.code = code;
    x.name = name;
    x.hourlyRate = rate;
    x.departmentId = department;
    return db.save(x);
  }
}
