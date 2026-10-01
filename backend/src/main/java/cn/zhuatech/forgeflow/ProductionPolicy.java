// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import java.math.*;

/** 基本单位精度、顺序数量守恒和成本分配规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class ProductionPolicy {
  private ProductionPolicy() {}

  /** 六位物料数量，拒绝静默舍入和超范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal quantity(BigDecimal n, boolean positive) {
    return decimal(n, 6, positive ? 1 : 0, "INVALID_QUANTITY");
  }

  /** 工位费率及采购单价最多四位小数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal rate(BigDecimal n) {
    return decimal(n, 4, 0, "INVALID_RATE");
  }

  /** 人工小时最多三位，单次记录最多一百万小时。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal hours(BigDecimal n) {
    return decimal(n, 3, 0, "INVALID_HOURS");
  }

  private static BigDecimal decimal(BigDecimal n, int scale, int min, String code) {
    if (n == null
        || n.scale() > scale
        || n.signum() < min
        || n.compareTo(new BigDecimal("1000000")) > 0) throw new Problem(400, code);
    return n.setScale(scale);
  }

  /** 金额两位舍入，保留有界非负库存价值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal money(BigDecimal n) {
    if (n == null || n.signum() < 0 || n.compareTo(new BigDecimal("1000000000000")) > 0)
      throw new Problem(400, "INVALID_MONEY");
    return n.setScale(2, RoundingMode.HALF_UP);
  }

  /** 移动平均出库，最后一份带走全部尾差。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal cost(BigDecimal available, BigDecimal value, BigDecimal out) {
    if (out.compareTo(available) > 0) throw new Problem(409, "INSUFFICIENT_STOCK");
    return out.compareTo(available) == 0
        ? value
        : value.multiply(out).divide(available, 2, RoundingMode.HALF_UP);
  }

  /** 退回原领料成本；最后一次归还剩余原成本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal returnCost(
      BigDecimal issued,
      BigDecimal value,
      BigDecimal returned,
      BigDecimal restored,
      BigDecimal next) {
    if (returned.add(next).compareTo(issued) > 0) throw new Problem(409, "RETURN_EXCEEDS_ISSUE");
    return returned.add(next).compareTo(issued) == 0
        ? value.subtract(restored)
        : value
            .multiply(next)
            .divide(issued, 2, RoundingMode.HALF_UP)
            .min(value.subtract(restored));
  }

  /** 离散产量必须是整数且在合理范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static int pieces(Integer value, boolean positive) {
    if (value == null || value < (positive ? 1 : 0) || value > 100000)
      throw new Problem(400, "INVALID_PIECES");
    return value;
  }

  /** 逐工序输出不超过实际转入数量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void output(int incoming, int recorded, int good, int rejected) {
    if ((long) recorded + good + rejected > incoming)
      throw new Problem(409, "OUTPUT_EXCEEDS_INPUT");
  }

  /** CSV文本防护公式前缀，导出只包含业务资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String csv(Object o) {
    var s = String.valueOf(o);
    if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }
}
