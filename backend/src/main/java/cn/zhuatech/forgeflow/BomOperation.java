// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** BOM顺序工序及工位名称、费率快照。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "bom_operation")
public class BomOperation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long bomId;

  @Column(name = "step_sequence")
  public int sequence;

  public Long stationId;
  public String name;
  public String stationName;

  @Column(precision = 20, scale = 4)
  public BigDecimal hourlyRate;
}
