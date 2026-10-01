// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import jakarta.persistence.*;

/** 提交幂等记录，与领料和报工处于同一事务。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "mutation_stamp")
public class MutationStamp {
  /** 已创建业务对象的标识，仅供同一命令重试定位。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Long resultId;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "request_key", nullable = false, length = 80)
  public String requestKey;

  @Column(name = "fingerprint", nullable = false, length = 64)
  public String fingerprint;
}
