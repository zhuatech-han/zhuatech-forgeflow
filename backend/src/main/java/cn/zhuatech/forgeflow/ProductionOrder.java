// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 生产工单保存下达快照、状态、终检产量与结单成本。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "production_order")
public class ProductionOrder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public String number;
  public Long bomId;
  public Long productId;
  public Long departmentId;
  public String productCode;
  public String productName;
  public String unit;
  public int bomVersion;
  public int plannedQuantity;
  public LocalDate dueDate;
  public String note = "";
  public String sourceReference = "";
  public String status = "DRAFT";
  public long revision;
  public String createdBy;
  public Instant createdAt;
  public Instant updatedAt;
  public Instant finishedAt;
  public int acceptedQuantity;
  public String receiptReference = "";
  public String materialVarianceNote = "";

  @Column(precision = 20, scale = 2)
  public BigDecimal materialCost = BigDecimal.ZERO;

  @Column(precision = 20, scale = 2)
  public BigDecimal laborCost = BigDecimal.ZERO;

  @Column(precision = 20, scale = 2)
  public BigDecimal totalCost = BigDecimal.ZERO;

  @Column(precision = 20, scale = 2)
  public BigDecimal lossCost = BigDecimal.ZERO;
}
