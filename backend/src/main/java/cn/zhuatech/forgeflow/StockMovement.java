// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 实际仓库收发和原领料退回流水；余额与价值同事务保存。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "stock_movement")
public class StockMovement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long itemId;
  public Long departmentId;
  public Long orderId;
  public Long materialId;
  public Long sourceId;
  public String kind;

  @Column(precision = 20, scale = 6)
  public BigDecimal quantity;

  @Column(precision = 20, scale = 6)
  public BigDecimal quantityDelta;

  @Column(precision = 20, scale = 2)
  public BigDecimal valueDelta;

  @Column(precision = 20, scale = 6)
  public BigDecimal balanceQuantity;

  @Column(precision = 20, scale = 2)
  public BigDecimal balanceValue;

  public String reference;
  public String note = "";
  public Long actorId;
  public String createdBy;
  public Instant createdAt;
}
