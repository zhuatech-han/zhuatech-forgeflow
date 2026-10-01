// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 工位和标准人工费率；生产下达后使用工艺费率快照。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "station")
public class Station {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public String code;
  public String name;
  public Long departmentId;

  @Column(precision = 20, scale = 4)
  public BigDecimal hourlyRate = BigDecimal.ZERO;

  public boolean enabled = true;
}
