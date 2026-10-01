// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 金额显示仅格式化，不参与后端过账计算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function money(value, currency = "CNY", lang = "zh") {
  return new Intl.NumberFormat(lang === "en" ? "en-GB" : "zh-CN", {
    style: "currency",
    currency,
  }).format(Number(value || 0));
}
/** 基本单位数量最多六位小数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function quantity(value) {
  return new Intl.NumberFormat("en-GB", { maximumFractionDigits: 6 }).format(
    Number(value || 0),
  );
}
/** 输入数字保留字符串精度交给后端校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function decimal(value) {
  if (
    value === null ||
    value === "" ||
    !/^\d+(\.\d{1,6})?$/.test(String(value))
  )
    throw new Error("INVALID_QUANTITY");
  return String(value);
}
