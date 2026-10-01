// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import jakarta.persistence.*;
import java.time.*;

/** 单层物料清单与顺序工艺版本；启用后内容冻结。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "bill_of_materials")
public class BillOfMaterials {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long productId;
  public int versionNumber;
  public Long departmentId;
  public String description = "";
  public String status = "DRAFT";
  public long revision;
  public String createdBy;
  public Instant createdAt;
  public Instant updatedAt;
}
