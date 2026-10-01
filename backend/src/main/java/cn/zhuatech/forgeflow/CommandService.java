// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** 同事务命令指纹，防止网络重试重复扣料和报工；调用方先加写锁及校验权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
public class CommandService {
  final Store db;
  final AccessService access;

  public CommandService(Store db, AccessService access) {
    this.db = db;
    this.access = access;
  }

  /** 命令首次标记与已提交重试标记。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(MutationStamp stamp, boolean fresh) {}

  /** 哈希包含操作者、业务目标与结构化输入，避免字符串分隔符歧义。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Command open(String action, Map<String, Object> v) {
    var key = AdminService.text(v.get("requestKey") instanceof String s ? s : null, 80);
    String fingerprint;
    try {
      fingerprint =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          tools.jackson.databind.json.JsonMapper.builder()
                              .build()
                              .writeValueAsBytes(
                                  Map.of(
                                      "actor",
                                      access.current().id,
                                      "action",
                                      action,
                                      "body",
                                      new TreeMap<>(v)))));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    var old = db.query(MutationStamp.class, "from MutationStamp where requestKey=?1", key);
    if (!old.isEmpty()) {
      if (!old.getFirst().fingerprint.equals(fingerprint))
        throw new Problem(409, "RETRY_CONTENT_CHANGED");
      return new Command(old.getFirst(), false);
    }
    var s = new MutationStamp();
    s.requestKey = key;
    s.fingerprint = fingerprint;
    db.save(s);
    return new Command(s, true);
  }
}
