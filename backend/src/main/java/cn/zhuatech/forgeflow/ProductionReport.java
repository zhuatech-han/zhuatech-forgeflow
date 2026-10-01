// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 逐工序良品、报废和人工小时的实际登记；受控冲销保留原记录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "production_report")
public class ProductionReport {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long orderId;
  public Long stepId;
  public int goodQuantity;
  public int rejectedQuantity;

  @Column(precision = 20, scale = 3)
  public BigDecimal hours;

  @Column(precision = 20, scale = 2)
  public BigDecimal laborCost;

  public Long actorId;
  public String createdBy;
  public String reference;
  public String note = "";
  public boolean voided;
  public Long voidedBy;
  public String voidReason = "";
  public Instant createdAt;
}
