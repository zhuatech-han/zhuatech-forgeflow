// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import jakarta.persistence.*;
import java.time.*;

/** 生产状态、派工、超耗、终检与结单的可追溯人工说明。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "business_event")
public class BusinessEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long orderId;
  public String kind;
  public String note;
  public Long actorId;
  public String createdBy;
  public Instant createdAt;
}
