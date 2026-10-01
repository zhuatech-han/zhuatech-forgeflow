// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 物料及成品基本单位档案；库存余额仅能由事务流水更新。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "item")
public class Item {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public String code;
  public String name;
  public String kind;
  public String unit;
  public String specification = "";
  public Long departmentId;
  public boolean enabled = true;

  @Column(precision = 20, scale = 6)
  public BigDecimal quantity = BigDecimal.ZERO;

  @Column(precision = 20, scale = 2)
  public BigDecimal inventoryValue = BigDecimal.ZERO;

  @Column(precision = 20, scale = 6)
  public BigDecimal reorderLevel = BigDecimal.ZERO;
}
