// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 每件成品所需物料的数量和名称快照。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "bom_component")
public class BomComponent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long bomId;
  public Long itemId;
  public String itemCode;
  public String itemName;
  public String unit;

  @Column(precision = 20, scale = 6)
  public BigDecimal perUnit;
}
