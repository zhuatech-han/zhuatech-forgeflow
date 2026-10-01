// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import jakarta.persistence.*;
import java.time.*;

/** 独立人工终检凭据；通过数量决定实际可入库数量。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "quality_review")
public class QualityReview {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long orderId;
  public boolean passed;
  public int observedQuantity;
  public int acceptedQuantity;
  public String note;
  public Long actorId;
  public String createdBy;
  public Instant createdAt;
}
