// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 顺序工序及派工；报工从不可变流水汇总，完成后冻结。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "work_step")
public class WorkStep {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long orderId;

  @Column(name = "step_sequence")
  public int sequence;

  public Long stationId;
  public String name;
  public String stationName;

  @Column(precision = 20, scale = 4)
  public BigDecimal hourlyRate;

  public Long operatorId;
  public String operatorName = "";
  public boolean finished;
}
