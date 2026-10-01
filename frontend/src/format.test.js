// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { decimal, quantity, money } from "./format.js";
test("six decimal material quantities survive input and display", () => {
  assert.equal(decimal("0.012390"), "0.012390");
  assert.equal(quantity("0.012390"), "0.01239");
});
test("reject malformed or excess precision before submitting", () => {
  for (const x of ["-1", "1e2", "", null, "0.0000001"])
    assert.throws(() => decimal(x));
});
test("configured currency and language format presentation", () => {
  assert.match(money("192.39", "USD", "en"), /192\.39/);
  assert.match(money("192.39", "CNY", "zh"), /192\.39/);
});
