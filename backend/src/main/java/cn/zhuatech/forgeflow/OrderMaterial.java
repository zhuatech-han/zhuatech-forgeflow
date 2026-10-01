// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 下达时固化标准需求及可领上限；调整上限须独立理由。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "order_material")
public class OrderMaterial {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long orderId;
  public Long itemId;
  public String itemCode;
  public String itemName;
  public String unit;

  @Column(precision = 20, scale = 6)
  public BigDecimal requiredQuantity;

  @Column(precision = 20, scale = 6)
  public BigDecimal authorizedQuantity;

  public String adjustmentNote = "";
}
