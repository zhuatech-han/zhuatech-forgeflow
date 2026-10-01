// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.forgeflow;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 精度、原成本尾差、守恒和导出输入边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class ProductionPolicyTest {
  BigDecimal n(String s) {
    return new BigDecimal(s);
  }

  @Test
  void supportsPrecisionWithoutSilentRounding() {
    assertEquals(n("0.012390"), ProductionPolicy.quantity(n("0.01239"), true));
    assertThrows(Problem.class, () -> ProductionPolicy.quantity(n("0.0000001"), true));
  }

  @Test
  void quantityBoundsAndFractionalPieces() {
    assertThrows(Problem.class, () -> ProductionPolicy.quantity(n("-1"), false));
    assertThrows(Problem.class, () -> ProductionPolicy.quantity(n("1000001"), false));
    assertThrows(Problem.class, () -> ProductionPolicy.pieces(100001, true));
  }

  @Test
  void finalIssueTakesCostRemainder() {
    assertEquals(n("1.00"), ProductionPolicy.cost(n("3"), n("1.00"), n("3")));
    assertEquals(n("0.33"), ProductionPolicy.cost(n("3"), n("1.00"), n("1")));
  }

  @Test
  void finalReturnRestoresRemainder() {
    assertEquals(
        n("0.34"), ProductionPolicy.returnCost(n("3"), n("1.00"), n("2"), n("0.66"), n("1")));
  }

  @Test
  void outputGuardUsesLongArithmetic() {
    assertThrows(Problem.class, () -> ProductionPolicy.output(100000, Integer.MAX_VALUE, 1, 1));
  }

  @Test
  void csvFormulaEscapesAndQuoteDoubles() {
    assertEquals("\"'=SUM(A1)\"", ProductionPolicy.csv("=SUM(A1)"));
    assertEquals("\"a\"\"b\"", ProductionPolicy.csv("a\"b"));
  }

  @Test
  void ratesHoursAndMoneyHaveDifferentScales() {
    assertThrows(Problem.class, () -> ProductionPolicy.rate(n("1.00001")));
    assertThrows(Problem.class, () -> ProductionPolicy.hours(n("1.0001")));
    assertEquals(n("0.01"), ProductionPolicy.money(n("0.005")));
  }
}
