<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { computed, onMounted, ref } from "vue";
import {
  Factory,
  LayoutDashboard,
  ClipboardList,
  Layers,
  Package,
  Settings,
  Users,
  LogOut,
  Search,
  Plus,
  ChevronLeft,
  ArrowLeft,
  Printer,
  RefreshCw,
  X,
  Check,
  Menu,
} from "@lucide/vue";
import { api, resetCsrf, downloadReport } from "./api.js";
import { money, quantity } from "./format.js";
const lang = ref(localStorage.getItem("forgeflow-language") || "zh");
const t = (zh, en) => (lang.value === "en" ? en : zh);
const profile = ref(null),
  catalog = ref({}),
  page = ref("dashboard"),
  rows = ref([]),
  total = ref(0),
  offset = ref(0),
  search = ref(""),
  filter = ref(""),
  sort = ref("id"),
  detail = ref(null),
  bomDetail = ref(null),
  dash = ref({ counts: {}, recent: [] }),
  report = ref({ items: [] }),
  from = ref(""),
  to = ref(""),
  loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  navOpen = ref(false);
const credentials = ref({ username: "", password: "" }),
  dialog = ref(null),
  form = ref({});
let loadSequence = 0;
const can = (p) => profile.value?.permissions.includes(p);
const cost = computed(() => can("cost"));
const settings = computed(() =>
  Object.fromEntries(
    (catalog.value.settings || []).map((x) => [x.code, x.value]),
  ),
);
const cash = (v) => money(v, settings.value.currency || "CNY", lang.value);
const date = (v) =>
  v
    ? new Intl.DateTimeFormat(lang.value === "en" ? "en-GB" : "zh-CN", {
        dateStyle: "short",
        timeStyle: "short",
        timeZone: settings.value.timezone || "Asia/Shanghai",
      }).format(new Date(v))
    : "—";
const title = computed(() => {
  let m = profile.value?.menus.find((x) => x.code === page.value);
  return m
    ? lang.value === "en"
      ? m.nameEn
      : m.name
    : t("工作台", "Overview");
});
const stateLabels = {
  DRAFT: ["草稿", "Draft"],
  ACTIVE: ["已启用", "Active"],
  ARCHIVED: ["已归档", "Archived"],
  RELEASED: ["已下达", "Released"],
  IN_PROGRESS: ["生产中", "In progress"],
  REVIEW: ["待终检", "Awaiting QC"],
  READY: ["待入库", "Ready to receive"],
  COMPLETE: ["已完结", "Complete"],
  CANCELLED: ["已取消", "Cancelled"],
  MATERIAL: ["原材料", "Material"],
  FINISHED: ["成品", "Finished"],
  RECEIVE: ["收货", "Receipt"],
  ISSUE: ["领料", "Issue"],
  RETURN: ["退料", "Return"],
  PRODUCE: ["生产入库", "Production receipt"],
  DISPATCH: ["成品发出", "Dispatch"],
  COUNT: ["盘点调整", "Stock count"],
  ALL: ["全部部门", "All departments"],
  DEPARTMENT: ["本部门", "Own department"],
  ASSIGNED: ["本部门派工", "Assigned in own department"],
};
const label = (v) => (stateLabels[v] ? t(...stateLabels[v]) : v);
const errorLabels = {
  UNAUTHENTICATED: ["请重新登录", "Please sign in again"],
  BAD_CREDENTIALS: ["账号或密码不正确", "Incorrect account or password"],
  LOGIN_FAILED: ["账号或密码不正确", "Incorrect account or password"],
  RATE_LIMITED: [
    "尝试过于频繁，稍后再试",
    "Too many attempts; try again later",
  ],
  FORBIDDEN: ["没有此操作权限", "Permission denied"],
  OUT_OF_SCOPE: ["不在可操作部门范围内", "Outside your department scope"],
  STALE_VERSION: [
    "工单已更新，请刷新后再操作",
    "Order changed; refresh before retrying",
  ],
  INSUFFICIENT_STOCK: ["库存不足", "Insufficient stock"],
  INVALID_STATE: ["当前状态不能执行此操作", "Action unavailable in this state"],
  UNASSIGNED_STEP: ["请为全部工序派工", "Assign every operation first"],
  MATERIAL_NOT_ISSUED: ["每种物料必须先领料", "Issue each material first"],
  PREVIOUS_STEP_OPEN: ["前道工序尚未完工", "Previous operation is still open"],
  UNACCOUNTED_OUTPUT: [
    "良品与报废数量必须等于转入量",
    "Good plus scrap must equal input",
  ],
  INDEPENDENT_QC_REQUIRED: [
    "报工人员不能终检自己的工单",
    "A reporter cannot inspect their own order",
  ],
  NOT_ASSIGNED: [
    "只能登记派给自己的工序",
    "Only your assigned operations may be reported",
  ],
  ISSUE_EXCEEDS_AUTHORIZATION: [
    "超出可领上限，请调整授权量",
    "Issue exceeds the authorized quantity",
  ],
  RETURN_MATERIAL_FIRST: [
    "请先退回全部已领材料",
    "Return all issued materials first",
  ],
  DOWNSTREAM_STARTED: [
    "后道工序已开始，不能冲销前道报工",
    "A downstream operation has started",
  ],
  STALE_STOCK_COUNT: [
    "账面库存已变化，请重新盘点",
    "Book stock changed; recount",
  ],
  RETRY_CONTENT_CHANGED: [
    "重试内容已改变，请关闭后重新操作",
    "Retry content changed; reopen the action",
  ],
  WEAK_PASSWORD: [
    "密码需12–72位，包含大小写字母和数字",
    "Use 12–72 characters with upper/lowercase letters and numbers",
  ],
  CONFLICT: [
    "记录被引用或存在重复，请检查输入",
    "Referenced record or duplicate; check input",
  ],
  LAST_ADMIN: [
    "必须保留一名启用的全局管理员",
    "Keep an enabled global administrator",
  ],
  CURRENCY_LOCKED: [
    "已有业务数据，币种不能变更",
    "Currency is locked after business activity",
  ],
  IDENTITY_FROZEN: [
    "类型、单位及部门不能改动",
    "Type, unit and department are fixed",
  ],
  BOM_FROZEN: [
    "启用BOM已冻结，请新建版本",
    "Active BOM is frozen; create a new version",
  ],
  BOM_REFERENCED: [
    "BOM已被使用，不能删除或改写",
    "BOM is referenced and cannot be changed",
  ],
  INVALID_VARIANCE_REASON: [
    "用料与标准不同，请填写有效偏差原因",
    "Select a valid reason for material variance",
  ],
  OUTPUT_EXCEEDS_INPUT: ["产出超过转入量", "Output exceeds input"],
  INVALID_ACCEPTED_QUANTITY: [
    "合格数量无效；不通过时应为0",
    "Invalid accepted quantity; use zero when failing QC",
  ],
  NETWORK_ERROR: ["连接失败，请稍后重试", "Connection failed; retry later"],
};
function fail(e) {
  error.value = errorLabels[e.message]
    ? t(...errorLabels[e.message])
    : t(
        "操作未完成，请检查输入及业务状态。",
        "Action failed; check inputs and business state.",
      ) +
      " (" +
      e.message +
      ")";
  if (e.message === "UNAUTHENTICATED") {
    profile.value = null;
    detail.value = null;
    dialog.value = null;
  }
}
function changeLanguage() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("forgeflow-language", lang.value);
}
async function login() {
  busy.value = true;
  error.value = "";
  try {
    await api("/auth/login", "POST", credentials.value);
    credentials.value.password = "";
    resetCsrf();
    await initialise();
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function initialise() {
  profile.value = await api("/auth/me");
  catalog.value = await api("/catalog");
  page.value = profile.value.menus[0]?.code || "dashboard";
  await load();
}
async function logout() {
  if (busy.value) return;
  try {
    await api("/auth/logout", "POST", {});
    resetCsrf();
    profile.value = null;
    detail.value = null;
    dialog.value = null;
    rows.value = [];
  } catch (e) {
    fail(e);
  }
}
async function load() {
  const seq = ++loadSequence;
  loading.value = true;
  rows.value = [];
  total.value = 0;
  try {
    let value;
    if (page.value === "dashboard") {
      value = await api("/dashboard");
      if (seq === loadSequence) dash.value = value;
    } else if (page.value === "reports") {
      value = await api(
        "/reports?" + new URLSearchParams({ from: from.value, to: to.value }),
      );
      if (seq === loadSequence) report.value = value;
    } else {
      value = await api(
        "/lists/" +
          page.value +
          "?" +
          new URLSearchParams({
            search: search.value,
            status: filter.value,
            page: offset.value,
            size: 20,
            sort: sort.value,
          }),
      );
      if (seq === loadSequence) {
        rows.value = value.items;
        total.value = value.total;
      }
    }
  } catch (e) {
    if (seq === loadSequence) fail(e);
  } finally {
    if (seq === loadSequence) loading.value = false;
  }
}
async function navigate(p) {
  page.value = p;
  offset.value = 0;
  search.value = "";
  filter.value = "";
  detail.value = null;
  bomDetail.value = null;
  error.value = "";
  notice.value = "";
  navOpen.value = false;
  await load();
}
async function refresh() {
  try {
    profile.value = await api("/auth/me");
    catalog.value = await api("/catalog");
    if (!profile.value.menus.some((m) => m.code === page.value)) {
      await navigate(profile.value.menus[0]?.code || "dashboard");
      return;
    }
    if (detail.value)
      detail.value = await api("/orders/" + detail.value.order.id);
    if (bomDetail.value)
      bomDetail.value = await api("/boms/" + bomDetail.value.bom.id);
    await load();
  } catch (e) {
    fail(e);
  }
}

async function openOrder(id) {
  try {
    detail.value = await api("/orders/" + id);
    bomDetail.value = null;
    page.value = "orders";
  } catch (e) {
    fail(e);
  }
}
async function openBom(id) {
  try {
    bomDetail.value = await api("/boms/" + id);
    detail.value = null;
  } catch (e) {
    fail(e);
  }
}
const F = (name, zh, en, type = "text", options = [], extra = {}) => ({
  name,
  zh,
  en,
  type,
  options,
  ...extra,
});
const opts = (rows, field = "name") =>
  (rows || []).map((x) => ({ value: x.id, label: x[field] || x.code }));
const enums = (values) => values.map((x) => ({ value: x, label: label(x) }));
const deps = () => opts(catalog.value.departments);
function showDialog(
  zh,
  en,
  fields,
  values,
  endpoint,
  method = "POST",
  kind = "normal",
) {
  error.value = "";
  dialog.value = { zh, en, fields, endpoint, method, kind };
  form.value = { ...values };
}
function newOrder(o) {
  const active = (catalog.value.boms || []).filter(
    (b) => b.status === "ACTIVE",
  );
  showDialog(
    o ? t("编辑计划", "Edit plan") : "新建工单",
    o ? "Edit plan" : "New production order",
    [
      F(
        "bomId",
        "产品 / BOM版本",
        "Product / BOM version",
        "select",
        active.map((x) => ({
          value: x.id,
          label: x.productName + " · v" + x.versionNumber,
        })),
      ),
      F("plannedQuantity", "计划件数", "Planned pieces", "number", [], {
        min: 1,
        max: 100000,
        step: 1,
      }),
      F("dueDate", "交期", "Due date", "date"),
      F(
        "sourceReference",
        "外部单号（可选）",
        "External reference (optional)",
        "text",
        [],
        { required: false, maxlength: 200 },
      ),
      F("note", "备注（可选）", "Notes (optional)", "textarea", [], {
        required: false,
        maxlength: 2000,
      }),
    ],
    o
      ? { ...o, requestKey: crypto.randomUUID() }
      : {
          bomId: active[0]?.id,
          plannedQuantity: 1,
          dueDate: new Date().toISOString().slice(0, 10),
          sourceReference: "",
          note: "",
          requestKey: crypto.randomUUID(),
        },
    o ? "/orders/" + o.id : "/orders",
    o ? "PUT" : "POST",
  );
}
function editMaster(row) {
  let type = page.value;
  const fields = [
    F("code", "编码", "Code"),
    F("name", "名称", "Name"),
    F("departmentId", "部门", "Department", "select", deps()),
    F("enabled", "启用", "Enabled", "checkbox"),
  ];
  if (type === "items")
    fields.push(
      F("kind", "类型", "Type", "select", enums(["MATERIAL", "FINISHED"])),
      F("unit", "基本单位", "Base unit"),
      F("reorderLevel", "低库存阈值", "Low-stock level", "number", [], {
        step: "0.000001",
        min: 0,
      }),
      F(
        "specification",
        "规格（可选）",
        "Specification (optional)",
        "textarea",
        [],
        { required: false, maxlength: 400 },
      ),
    );
  else
    fields.push(
      F("hourlyRate", "标准小时费率", "Standard hourly rate", "number", [], {
        step: "0.0001",
        min: 0,
      }),
    );
  showDialog(
    row ? "编辑档案" : "新增档案",
    row ? "Edit record" : "New record",
    fields,
    row || {
      code: "",
      name: "",
      departmentId: profile.value.departmentId,
      enabled: true,
      kind: "MATERIAL",
      unit: "kg",
      reorderLevel: 0,
      hourlyRate: 0,
      specification: "",
    },
    "/master/" + type + (row ? "/" + row.id : ""),
    row ? "PUT" : "POST",
  );
}
function editAdmin(row) {
  const type = page.value;
  let fields = [];
  let values = {
    enabled: true,
    scope: "DEPARTMENT",
    permissions: [],
    departmentId: profile.value.departmentId,
  };
  if (type === "users")
    fields = [
      F("username", "登录账号", "Username"),
      F("displayName", "姓名", "Display name"),
      F(
        "password",
        row ? "新密码（留空不变）" : "密码",
        "Password",
        "password",
        [],
        { required: !row, minlength: row ? undefined : 12, maxlength: 72 },
      ),
      F("roleId", "角色", "Role", "select", opts(catalog.value.roles)),
      F("departmentId", "部门", "Department", "select", deps()),
      F("enabled", "启用", "Enabled", "checkbox"),
    ];
  else if (type === "roles")
    fields = [
      F("name", "角色名称", "Role name"),
      F(
        "scope",
        "数据范围",
        "Data scope",
        "select",
        enums(["ALL", "DEPARTMENT", "ASSIGNED"]),
      ),
      F(
        "permissions",
        "操作权限",
        "Permissions",
        "checks",
        (catalog.value.permissions || []).map((x) => ({
          value: x.code,
          label: x.name,
        })),
      ),
    ];
  else if (type === "menus")
    fields = [
      F("name", "中文名称", "Chinese label"),
      F("nameEn", "英文名称", "English label"),
      F(
        "permissionCode",
        "所需权限",
        "Required permission",
        "select",
        (catalog.value.permissions || []).map((x) => ({
          value: x.code,
          label: x.name,
        })),
      ),
      F("position", "顺序", "Position", "number", [], { step: 1, min: 0 }),
      F("enabled", "启用", "Enabled", "checkbox"),
    ];
  else if (type === "dictionaries")
    fields = [
      F("type", "字典类型", "Dictionary type"),
      F("code", "编码", "Code"),
      F("name", "中文名称", "Chinese label"),
      F("nameEn", "英文名称", "English label"),
      F("enabled", "启用", "Enabled", "checkbox"),
    ];
  else if (type === "settings") fields = [F("value", "参数值", "Value")];
  else fields = [F("name", "名称", "Name")];
  showDialog(
    row ? "编辑" : "新增",
    row ? "Edit" : "Add",
    fields,
    row ? { ...row, password: "" } : values,
    "/admin/" + type + (row ? "/" + row.id : ""),
    row ? "PUT" : "POST",
  );
}
function editBom(d) {
  let b = d?.bom;
  let products = (catalog.value.items || []).filter(
    (x) => x.kind === "FINISHED" && x.enabled,
  );
  showDialog(
    b ? "编辑BOM草稿" : "新建BOM版本",
    b ? "Edit BOM draft" : "New BOM version",
    [
      F("productId", "成品", "Finished product", "select", opts(products)),
      F("versionNumber", "版本号", "Version number", "number", [], {
        min: 1,
        max: 9999,
        step: 1,
      }),
      F(
        "description",
        "描述（可选）",
        "Description (optional)",
        "textarea",
        [],
        { required: false, maxlength: 2000 },
      ),
    ],
    {
      productId: b?.productId || products[0]?.id,
      versionNumber: b?.versionNumber || 1,
      description: b?.description || "",
      revision: b?.revision,
      components: d?.components.map((x) => ({
        itemId: x.itemId,
        perUnit: x.perUnit,
      })) || [{ itemId: null, perUnit: "1" }],
      operations: d?.operations.map((x) => ({
        stationId: x.stationId,
        name: x.name,
      })) || [{ stationId: null, name: "" }],
    },
    "/boms" + (b ? "/" + b.id : ""),
    b ? "PUT" : "POST",
    "bom",
  );
}
function orderAction(action, fields = [], values = {}) {
  const names = {
    release: ["下达生产", "Release order"],
    assign: ["工序派工", "Assign operation"],
    start: ["开始生产", "Start production"],
    "authorize-material": ["调整可领量", "Authorize material"],
    "send-review": ["送交终检", "Submit for inspection"],
    review: ["登记终检", "Record inspection"],
    receive: ["成品入库 / 结单", "Receive / close"],
    cancel: ["取消工单", "Cancel order"],
    "void-report": ["冲销报工", "Void report"],
  };
  let o = detail.value.order;
  showDialog(
    ...names[action],
    fields,
    { revision: o.revision, requestKey: crypto.randomUUID(), ...values },
    "/orders/" + o.id + "/" + action,
  );
}
function assign(s) {
  orderAction(
    "assign",
    [
      F(
        "operatorId",
        "操作员",
        "Operator",
        "select",
        opts(
          (catalog.value.operators || []).filter(
            (a) => a.departmentId === detail.value.order.departmentId,
          ),
          "displayName",
        ),
      ),
    ],
    { stepId: s.id, operatorId: s.operatorId },
  );
}
function review() {
  orderAction(
    "review",
    [
      F("passed", "检验通过", "Inspection passed", "checkbox"),
      F(
        "acceptedQuantity",
        "最终合格件数",
        "Final accepted pieces",
        "number",
        [],
        { min: 0, max: detail.value.steps.at(-1).goodQuantity, step: 1 },
      ),
      F(
        "note",
        "检验记录 / 返工原因",
        "Inspection record / rework reason",
        "textarea",
        [],
        { maxlength: 2000 },
      ),
    ],
    {
      passed: true,
      acceptedQuantity: detail.value.steps.at(-1).goodQuantity,
      note: "",
    },
  );
}
function sendReview() {
  orderAction(
    "send-review",
    [
      F(
        "varianceType",
        "偏差原因（用量不同必填）",
        "Variance reason (required for variance)",
        "select",
        (catalog.value.dictionaries || [])
          .filter((x) => x.enabled && x.type === "variance_reason")
          .map((x) => ({
            value: x.code,
            label: lang.value === "en" ? x.nameEn : x.name,
          })),
        { required: false },
      ),
      F(
        "note",
        "偏差说明（用量不同必填）",
        "Variance notes (required for variance)",
        "textarea",
        [],
        { required: false, maxlength: 1900 },
      ),
    ],
    { varianceType: "", note: "" },
  );
}
function materialAction(l, action) {
  let fields = [
    F("quantity", "数量", "Quantity", "number", [], {
      step: "0.000001",
      min: "0.000001",
    }),
    F("reference", "仓库凭证号", "Warehouse reference", "text", [], {
      maxlength: 200,
    }),
    F("note", "备注（可选）", "Notes (optional)", "textarea", [], {
      required: false,
      maxlength: 2000,
    }),
  ];
  if (action === "return")
    fields.unshift(
      F(
        "sourceId",
        "原领料凭证",
        "Original issue",
        "select",
        detail.value.movements
          .filter((m) => m.kind === "ISSUE" && m.materialId === l.id)
          .map((m) => ({
            value: m.id,
            label: m.reference + " · " + quantity(m.quantity),
          })),
      ),
    );
  showDialog(
    action === "issue" ? "领料" : "退料",
    action === "issue" ? "Issue materials" : "Return materials",
    fields,
    {
      quantity:
        action === "issue"
          ? Math.max(0, l.authorizedQuantity - l.netQuantity).toFixed(6)
          : "",
      reference: "",
      note: "",
      requestKey: crypto.randomUUID(),
    },
    "/materials/" + l.id + "/" + action,
  );
}
function stepAction(s, action) {
  const fields =
    action === "report"
      ? [
          F("goodQuantity", "良品件数", "Good pieces", "number", [], {
            step: 1,
            min: 0,
            max: s.incoming - s.goodQuantity - s.rejectedQuantity,
          }),
          F("rejectedQuantity", "报废件数", "Scrapped pieces", "number", [], {
            step: 1,
            min: 0,
            max: s.incoming - s.goodQuantity - s.rejectedQuantity,
          }),
          F("hours", "实际工时", "Actual hours", "number", [], {
            step: "0.001",
            min: 0,
          }),
          F("reference", "报工凭证号", "Reporting reference", "text", [], {
            maxlength: 200,
          }),
          F("note", "备注（可选）", "Notes (optional)", "textarea", [], {
            required: false,
            maxlength: 2000,
          }),
        ]
      : [];
  showDialog(
    action === "report" ? "登记报工" : "工序完工",
    action === "report" ? "Report output" : "Finish operation",
    fields,
    {
      goodQuantity: 0,
      rejectedQuantity: 0,
      hours: 0,
      reference: "",
      note: "",
      requestKey: crypto.randomUUID(),
    },
    "/steps/" + s.id + "/" + action,
  );
}
function stockAction(x, action) {
  const fields = [
    F(
      "quantity",
      action === "count" ? "实盘数量" : "数量",
      action === "count" ? "Counted quantity" : "Quantity",
      "number",
      [],
      {
        min: action === "count" ? 0 : 0.000001,
        step: x.kind === "FINISHED" ? 1 : 0.000001,
      },
    ),
    F("reference", "仓库凭证号", "Warehouse reference", "text", [], {
      maxlength: 200,
    }),
    F(
      "note",
      action === "count" ? "盘点原因" : "备注（可选）",
      action === "count" ? "Count reason" : "Notes (optional)",
      "textarea",
      [],
      { required: action === "count", maxlength: 2000 },
    ),
  ];
  if (action === "receive" || (action === "count" && Number(x.quantity) === 0))
    fields.unshift(
      F("price", "每基本单位成本", "Cost per base unit", "number", [], {
        step: 0.0001,
        min: 0,
      }),
    );
  showDialog(
    label(action.toUpperCase()),
    label(action.toUpperCase()),
    fields,
    {
      quantity: action === "count" ? x.quantity : "",
      bookQuantity: x.quantity,
      price: 0,
      reference: "",
      note: "",
      requestKey: crypto.randomUUID(),
    },
    "/items/" + x.id + "/" + action,
  );
}
function confirmDelete(x, type = page.value) {
  let path = ["boms"].includes(type)
    ? "/boms/" + x.id
    : ["items", "stations"].includes(type)
      ? "/master/" + type + "/" + x.id
      : "/admin/" + type + "/" + x.id;
  showDialog("删除记录", "Delete record", [], {}, path, "DELETE");
}
function bomState(action) {
  showDialog(
    action === "activate" ? "启用并冻结此版本" : "归档此版本",
    action === "activate" ? "Activate and freeze version" : "Archive version",
    [],
    { revision: bomDetail.value.bom.revision },
    "/boms/" + bomDetail.value.bom.id + "/" + action,
  );
}
function changePassword() {
  showDialog(
    "修改密码",
    "Change password",
    [
      F("oldPassword", "当前密码", "Current password", "password"),
      F("newPassword", "新密码", "New password", "password", [], {
        minlength: 12,
        maxlength: 72,
      }),
    ],
    {},
    "/auth/password",
  );
}
function importItems() {
  showDialog(
    "批量导入物料",
    "Import items",
    [
      F("json", "物料JSON数组", "Item JSON array", "textarea", [], {
        maxlength: 500000,
      }),
    ],
    { json: "" },
    "/master/items/import",
    "POST",
    "import",
  );
}
async function submit() {
  busy.value = true;
  error.value = "";
  try {
    let payload = { ...form.value };
    if (dialog.value.kind === "bom") {
      payload.components = payload.components.map((x) => ({
        itemId: Number(x.itemId),
        perUnit: String(x.perUnit),
      }));
      payload.operations = payload.operations.map((x) => ({
        stationId: Number(x.stationId),
        name: x.name,
      }));
    }
    if (dialog.value.kind === "import") payload = JSON.parse(payload.json);
    const endpoint = dialog.value.endpoint;
    const method = dialog.value.method;
    const result = await api(endpoint, dialog.value.method, payload);
    dialog.value = null;
    notice.value = t("已保存", "Saved");
    if (endpoint === "/auth/password") {
      profile.value = null;
      resetCsrf();
      return;
    }
    if (endpoint === "/orders") {
      detail.value = await api("/orders/" + result.id);
      page.value = "orders";
    }
    if (endpoint.startsWith("/boms") && result.id) {
      bomDetail.value = await api("/boms/" + result.id);
    }
    if (endpoint.includes("/boms/") && method === "DELETE")
      bomDetail.value = null;
    await refresh();
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
const tableColumns = computed(
  () =>
    ({
      orders: [
        ["number", "工单", "Order"],
        ["productName", "成品", "Product"],
        ["plannedQuantity", "计划件数", "Planned"],
        ["dueDate", "交期", "Due"],
        ["status", "状态", "Status"],
      ],
      boms: [
        ["productName", "成品", "Product"],
        ["versionNumber", "版本", "Version"],
        ["status", "状态", "Status"],
        ["description", "描述", "Description"],
      ],
      items: [
        ["code", "编码", "Code"],
        ["name", "名称", "Name"],
        ["kind", "类型", "Type"],
        ["unit", "单位", "Unit"],
        ["quantity", "账面数量", "Book stock"],
        ...(cost.value ? [["inventoryValue", "库存价值", "Stock value"]] : []),
        ["enabled", "启用", "Enabled"],
      ],
      stations: [
        ["code", "编码", "Code"],
        ["name", "名称", "Name"],
        ...(cost.value ? [["hourlyRate", "小时费率", "Hourly rate"]] : []),
        ["enabled", "启用", "Enabled"],
      ],
      movements: [
        ["kind", "类型", "Type"],
        ["itemId", "物料", "Item"],
        ["quantityDelta", "数量变化", "Qty change"],
        ["balanceQuantity", "结存数量", "Balance"],
        ...(cost.value ? [["valueDelta", "价值变化", "Value change"]] : []),
        ["reference", "凭证号", "Reference"],
        ["createdBy", "经办人", "Recorded by"],
        ["createdAt", "时间", "Time"],
      ],
      users: [
        ["username", "登录账号", "Username"],
        ["displayName", "姓名", "Name"],
        ["roleId", "角色", "Role"],
        ["departmentId", "部门", "Department"],
        ["enabled", "启用", "Enabled"],
      ],
      roles: [
        ["name", "角色", "Role"],
        ["scope", "范围", "Scope"],
        ["permissions", "权限", "Permissions"],
      ],
      departments: [["name", "部门", "Department"]],
      permissions: [
        ["code", "权限代码", "Code"],
        ["name", "名称", "Name"],
      ],
      menus: [
        ["code", "页面", "Page"],
        ["name", "中文名称", "Chinese label"],
        ["nameEn", "英文名称", "English label"],
        ["position", "顺序", "Position"],
        ["enabled", "启用", "Enabled"],
      ],
      dictionaries: [
        ["type", "类型", "Type"],
        ["code", "编码", "Code"],
        ["name", "中文名称", "Chinese label"],
        ["nameEn", "英文名称", "English label"],
        ["enabled", "启用", "Enabled"],
      ],
      settings: [
        ["code", "参数", "Setting"],
        ["value", "值", "Value"],
      ],
      audit: [
        ["actor", "账号", "Actor"],
        ["action", "操作", "Action"],
        ["objectId", "对象", "Object"],
        ["createdAt", "时间", "Time"],
      ],
    })[page.value] || [],
);
const adminPages = [
  "users",
  "roles",
  "departments",
  "permissions",
  "menus",
  "dictionaries",
  "settings",
];
const canAdd = computed(() =>
  page.value === "orders"
    ? can("production.manage")
    : page.value === "boms"
      ? can("bom.write")
      : ["items", "stations"].includes(page.value)
        ? can("master.write") && (page.value !== "stations" || cost.value)
        : ["users", "roles", "departments", "dictionaries"].includes(
            page.value,
          ) && can("admin"),
);
const canEdit = computed(() =>
  ["items", "stations"].includes(page.value)
    ? can("master.write") && (page.value !== "stations" || cost.value)
    : adminPages.includes(page.value) && can("admin"),
);
function add() {
  if (page.value === "orders") newOrder();
  else if (page.value === "boms") editBom();
  else if (["items", "stations"].includes(page.value)) editMaster();
  else editAdmin();
}
function display(row, key) {
  let v = row[key];
  if (key === "itemId")
    return (catalog.value.items || []).find((x) => x.id === v)?.name || v;
  if (key === "roleId")
    return (catalog.value.roles || []).find((x) => x.id === v)?.name || v;
  if (key === "departmentId")
    return (catalog.value.departments || []).find((x) => x.id === v)?.name || v;
  if (["createdAt", "finishedAt", "updatedAt"].includes(key)) return date(v);
  if (
    [
      "inventoryValue",
      "valueDelta",
      "totalCost",
      "materialCost",
      "laborCost",
      "lossCost",
    ].includes(key)
  )
    return cash(v);
  if (
    [
      "quantity",
      "quantityDelta",
      "balanceQuantity",
      "hourlyRate",
      "hours",
    ].includes(key)
  )
    return quantity(v);
  if (key === "enabled") return v ? t("是", "Yes") : t("否", "No");
  if (Array.isArray(v)) return v.join(" · ");
  if (["status", "kind", "scope"].includes(key)) return label(v);
  return v ?? "—";
}
function about() {
  showDialog("关于系统", "About", [], {}, null, "GET", "about");
}
function printOrder() {
  window.print();
}
function navIcon(code) {
  return code === "dashboard"
    ? LayoutDashboard
    : code === "orders"
      ? ClipboardList
      : code === "boms"
        ? Layers
        : code === "items"
          ? Package
          : adminPages.includes(code)
            ? Users
            : Settings;
}
onMounted(async () => {
  try {
    await initialise();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") fail(e);
  }
});
</script>

<template>
  <div v-if="!profile" class="login-layout">
    <section class="login-brand">
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        ><img src="/brand/logo.jpg" alt="知华科技" /> ZHUA TECH</a
      >
      <div>
        <p>FORGEFLOW / 01</p>
        <h1>{{ t("知华生产工单管理", "ZhuaTech production orders") }}</h1>
        <div class="brand-rule"></div>
        <p>
          {{
            t(
              "物料 · 工序 · 生产实绩",
              "Materials · Operations · Actual output",
            )
          }}
        </p>
      </div>
      <footer>上海如静知华信息科技有限公司</footer>
    </section>
    <main class="login-panel">
      <button class="language" @click="changeLanguage">
        {{ lang === "zh" ? "English" : "中文" }}
      </button>
      <form class="login-form" @submit.prevent="login">
        <Factory :size="32" />
        <h2>{{ t("登录工作台", "Sign in to your workspace") }}</h2>
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="credentials.username"
            required
            autocomplete="username"
            maxlength="60" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="credentials.password"
            type="password"
            required
            autocomplete="current-password"
            maxlength="72"
        /></label>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <button class="primary" :disabled="busy">
          {{ busy ? t("正在登录…", "Signing in…") : t("登录", "Sign in") }}
        </button>
        <p class="license-note">
          {{
            t(
              "公开源码学习版 · 商用需获得授权",
              "Non-commercial source edition · Commercial license required",
            )
          }}
        </p>
        <a
          class="consult"
          href="https://www.zhuatech.cn/"
          target="_blank"
          rel="noopener"
          >{{ t("知华科技 · 商业咨询", "ZhuaTech · Commercial enquiries") }}</a
        >
      </form>
    </main>
  </div>
  <div v-else class="workspace">
    <aside :class="['sidebar', { open: navOpen }]">
      <a
        class="brand"
        href="https://www.zhuatech.cn/"
        target="_blank"
        rel="noopener"
        ><img src="/brand/logo.jpg" alt="知华科技" /><span
          >ForgeFlow<small>{{
            t("知华生产", "ZhuaTech production")
          }}</small></span
        ></a
      >
      <nav>
        <button
          v-for="m in profile.menus"
          :key="m.id"
          :class="{ active: page === m.code }"
          @click="navigate(m.code)"
        >
          <component :is="navIcon(m.code)" :size="17" /><span>{{
            lang === "en" ? m.nameEn : m.name
          }}</span>
        </button>
      </nav>
      <div class="sidebar-footer">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">{{
          t("知华科技 · 商业咨询", "ZhuaTech · Enquiries")
        }}</a
        ><small>WeChat: zhuatech / zhuatech2</small
        ><small>{{
          t("公开源码学习版", "Non-commercial source edition")
        }}</small>
        <button class="about-link" @click="about">
          {{ t("关于系统", "About") }}
        </button>
      </div>
    </aside>
    <div v-if="navOpen" class="nav-mask" @click="navOpen = false"></div>
    <div class="work-main">
      <header class="topbar">
        <button
          class="mobile-toggle"
          :aria-label="t('菜单', 'Menu')"
          @click="navOpen = !navOpen"
        >
          <Menu :size="20" /></button
        ><span>{{ settings.companyName }}</span>
        <div>
          <button @click="changeLanguage">
            {{ lang === "zh" ? "EN" : "中文" }}</button
          ><button @click="changePassword">{{ profile.displayName }}</button
          ><button
            :disabled="busy"
            :aria-label="t('退出', 'Sign out')"
            @click="logout"
          >
            <LogOut :size="16" />
          </button>
        </div>
      </header>
      <main class="content">
        <p v-if="error" role="alert" class="error banner">
          {{ error
          }}<button :aria-label="t('关闭', 'Close')" @click="error = ''">
            ×
          </button>
        </p>
        <p v-if="notice" role="status" class="notice banner">
          {{ notice
          }}<button :aria-label="t('关闭', 'Close')" @click="notice = ''">
            ×
          </button>
        </p>
        <template v-if="detail">
          <div class="page-heading">
            <div>
              <button
                class="back"
                @click="
                  detail = null;
                  load();
                "
              >
                <ArrowLeft :size="15" />{{ t("工单列表", "Orders") }}
              </button>
              <h1>{{ detail.order.number }}</h1>
              <p>
                {{ detail.order.productCode }} ·
                {{ detail.order.productName }} · BOM v{{
                  detail.order.bomVersion
                }}
              </p>
            </div>
            <div class="heading-actions">
              <span :class="['status', detail.order.status]">{{
                label(detail.order.status)
              }}</span
              ><button :aria-label="t('刷新', 'Refresh')" @click="refresh">
                <RefreshCw :size="16" /></button
              ><button @click="printOrder()">
                <Printer :size="16" />{{ t("打印工单", "Print order") }}
              </button>
            </div>
          </div>
          <section class="order-facts">
            <div>
              <small>{{ t("计划件数", "Planned pieces") }}</small
              ><strong
                >{{ quantity(detail.order.plannedQuantity) }}
                <em>{{ detail.order.unit }}</em></strong
              >
            </div>
            <div>
              <small>{{ t("交期", "Due date") }}</small
              ><strong>{{ detail.order.dueDate }}</strong>
            </div>
            <div>
              <small>{{ t("外部单号", "External reference") }}</small
              ><strong>{{ detail.order.sourceReference || "—" }}</strong>
            </div>
            <div>
              <small>{{ t("入库良品", "Accepted output") }}</small
              ><strong>{{ quantity(detail.order.acceptedQuantity) }}</strong>
            </div>
          </section>
          <p v-if="detail.order.note" class="order-note">
            {{ detail.order.note }}
          </p>
          <div class="flow-actions">
            <template v-if="can('production.manage')"
              ><button
                v-if="detail.order.status === 'DRAFT'"
                @click="newOrder(detail.order)"
              >
                {{ t("编辑计划", "Edit plan") }}</button
              ><button
                v-if="detail.order.status === 'DRAFT'"
                class="primary"
                @click="orderAction('release')"
              >
                {{ t("下达生产", "Release order") }}</button
              ><button
                v-if="detail.order.status === 'RELEASED'"
                class="primary"
                @click="orderAction('start')"
              >
                {{ t("开始生产", "Start production") }}</button
              ><button
                v-if="detail.order.status === 'IN_PROGRESS'"
                class="primary"
                @click="sendReview"
              >
                {{ t("送交终检", "Submit for inspection") }}</button
              ><button
                v-if="['DRAFT', 'RELEASED'].includes(detail.order.status)"
                class="danger"
                @click="
                  orderAction(
                    'cancel',
                    [
                      F(
                        'note',
                        '取消原因',
                        'Cancellation reason',
                        'textarea',
                        [],
                        { maxlength: 2000 },
                      ),
                    ],
                    { note: '' },
                  )
                "
              >
                {{ t("取消工单", "Cancel order") }}
              </button></template
            >
            <button
              v-if="can('quality') && detail.order.status === 'REVIEW'"
              class="primary"
              @click="review"
            >
              {{ t("登记终检", "Record inspection") }}</button
            ><button
              v-if="can('stock.write') && detail.order.status === 'READY'"
              class="primary"
              @click="
                orderAction(
                  'receive',
                  [
                    F(
                      'reference',
                      '入库凭证号 / 零良品结单号',
                      'Receipt / zero-output closing reference',
                      'text',
                      [],
                      { maxlength: 200 },
                    ),
                  ],
                  { reference: '' },
                )
              "
            >
              {{ t("成品入库 / 结单", "Receive / close") }}
            </button>
          </div>
          <section class="panel">
            <div class="section-heading">
              <h2>{{ t("工单物料", "Order materials") }}</h2>
              <span>{{
                t(
                  "标准用量、授权与净领量",
                  "Standard, authorization and net issues",
                )
              }}</span>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("物料", "Material") }}</th>
                    <th>{{ t("单位", "Unit") }}</th>
                    <th class="numeric">{{ t("标准用量", "Standard") }}</th>
                    <th class="numeric">{{ t("可领上限", "Authorized") }}</th>
                    <th class="numeric">{{ t("净领数量", "Net issued") }}</th>
                    <th class="no-print">{{ t("操作", "Actions") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="l in detail.materials" :key="l.id">
                    <td>
                      <strong>{{ l.itemName }}</strong
                      ><small>{{ l.itemCode }}</small>
                    </td>
                    <td>{{ l.unit }}</td>
                    <td class="numeric">{{ quantity(l.requiredQuantity) }}</td>
                    <td class="numeric">
                      {{ quantity(l.authorizedQuantity) }}
                    </td>
                    <td class="numeric">{{ quantity(l.netQuantity) }}</td>
                    <td class="row-actions no-print">
                      <template
                        v-if="
                          ['RELEASED', 'IN_PROGRESS'].includes(
                            detail.order.status,
                          )
                        "
                        ><button
                          v-if="can('stock.write')"
                          @click="materialAction(l, 'issue')"
                        >
                          {{ t("领料", "Issue") }}</button
                        ><button
                          v-if="can('stock.write') && Number(l.netQuantity) > 0"
                          @click="materialAction(l, 'return')"
                        >
                          {{ t("退料", "Return") }}</button
                        ><button
                          v-if="can('production.manage')"
                          @click="
                            orderAction(
                              'authorize-material',
                              [
                                F(
                                  'quantity',
                                  '可领上限',
                                  'Authorized quantity',
                                  'number',
                                  [],
                                  { step: 0.000001, min: 0.000001 },
                                ),
                                F(
                                  'note',
                                  '调整原因',
                                  'Adjustment reason',
                                  'textarea',
                                  [],
                                  { maxlength: 1800 },
                                ),
                              ],
                              {
                                materialId: l.id,
                                quantity: l.authorizedQuantity,
                                note: '',
                              },
                            )
                          "
                        >
                          {{ t("调整上限", "Authorize") }}
                        </button></template
                      >
                    </td>
                  </tr>
                  <tr v-if="!detail.materials.length">
                    <td colspan="6" class="empty">
                      {{
                        t(
                          "下达后生成物料快照",
                          "Material snapshots appear on release",
                        )
                      }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <section class="panel">
            <div class="section-heading">
              <h2>{{ t("工序与实绩", "Operations & output") }}</h2>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("工序", "Operation") }}</th>
                    <th>{{ t("操作员", "Operator") }}</th>
                    <th class="numeric">{{ t("转入", "Input") }}</th>
                    <th class="numeric">{{ t("良品", "Good") }}</th>
                    <th class="numeric">{{ t("报废", "Scrap") }}</th>
                    <th class="numeric">{{ t("小时", "Hours") }}</th>
                    <th>{{ t("状态", "Status") }}</th>
                    <th class="no-print">{{ t("操作", "Actions") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="s in detail.steps" :key="s.id">
                    <td>
                      <strong>{{ s.sequence }}. {{ s.name }}</strong
                      ><small>{{ s.stationName }}</small>
                    </td>
                    <td>{{ s.operatorName || t("未派工", "Unassigned") }}</td>
                    <td class="numeric">{{ quantity(s.incoming) }}</td>
                    <td class="numeric">{{ quantity(s.goodQuantity) }}</td>
                    <td class="numeric">{{ quantity(s.rejectedQuantity) }}</td>
                    <td class="numeric">{{ quantity(s.hours) }}</td>
                    <td>
                      <span
                        :class="['status', s.finished ? 'COMPLETE' : 'DRAFT']"
                        >{{
                          s.finished
                            ? t("已完工", "Finished")
                            : t("未完工", "Open")
                        }}</span
                      >
                    </td>
                    <td class="row-actions no-print">
                      <button
                        v-if="
                          can('production.manage') &&
                          ['RELEASED', 'IN_PROGRESS'].includes(
                            detail.order.status,
                          ) &&
                          !s.finished
                        "
                        @click="assign(s)"
                      >
                        {{ t("派工", "Assign") }}</button
                      ><template
                        v-if="
                          can('execute') &&
                          s.operatorId === profile.id &&
                          detail.order.status === 'IN_PROGRESS' &&
                          !s.finished
                        "
                        ><button @click="stepAction(s, 'report')">
                          {{ t("报工", "Report") }}</button
                        ><button @click="stepAction(s, 'finish')">
                          {{ t("完工", "Finish") }}
                        </button></template
                      >
                    </td>
                  </tr>
                  <tr v-if="!detail.steps.length">
                    <td colspan="8" class="empty">
                      {{
                        t(
                          "下达后生成工艺快照",
                          "Routing snapshots appear on release",
                        )
                      }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <section v-if="cost" class="panel">
            <div class="section-heading">
              <h2>{{ t("完结成本", "Closing costs") }}</h2>
              <span>{{
                detail.order.status === "COMPLETE"
                  ? t(
                      "按原领料成本及实际工时结算",
                      "Original material cost and actual labor",
                    )
                  : t("入库结单时计算", "Calculated on receiving / closing")
              }}</span>
            </div>
            <div class="cost-strip">
              <div>
                {{ t("材料", "Materials")
                }}<strong>{{ cash(detail.order.materialCost) }}</strong>
              </div>
              <div>
                {{ t("人工", "Labor")
                }}<strong>{{ cash(detail.order.laborCost) }}</strong>
              </div>
              <div>
                {{ t("总成本", "Total")
                }}<strong>{{ cash(detail.order.totalCost) }}</strong>
              </div>
              <div>
                {{ t("零良品损失", "Zero-output loss")
                }}<strong>{{ cash(detail.order.lossCost) }}</strong>
              </div>
            </div>
          </section>
          <section class="panel">
            <div class="section-heading">
              <h2>{{ t("报工记录", "Reporting history") }}</h2>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("凭证", "Reference") }}</th>
                    <th>{{ t("工序", "Operation") }}</th>
                    <th>{{ t("良品 / 报废", "Good / scrap") }}</th>
                    <th>{{ t("工时", "Hours") }}</th>
                    <th>{{ t("登记人", "Recorded by") }}</th>
                    <th>{{ t("时间", "Time") }}</th>
                    <th>{{ t("状态 / 原因", "State / reason") }}</th>
                    <th class="no-print"></th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="r in detail.reports"
                    :key="r.id"
                    :class="{ voided: r.voided }"
                  >
                    <td>{{ r.reference }}</td>
                    <td>
                      {{ detail.steps.find((s) => s.id === r.stepId)?.name }}
                    </td>
                    <td>{{ r.goodQuantity }} / {{ r.rejectedQuantity }}</td>
                    <td>{{ quantity(r.hours) }}</td>
                    <td>{{ r.createdBy }}</td>
                    <td>{{ date(r.createdAt) }}</td>
                    <td>
                      {{
                        r.voided
                          ? t("已冲销", "Voided") + " · " + r.voidReason
                          : r.note || "—"
                      }}
                    </td>
                    <td class="no-print">
                      <button
                        v-if="
                          can('production.manage') &&
                          detail.order.status === 'IN_PROGRESS' &&
                          !r.voided
                        "
                        class="danger"
                        @click="
                          orderAction(
                            'void-report',
                            [
                              F(
                                'note',
                                '冲销原因',
                                'Void reason',
                                'textarea',
                                [],
                                { maxlength: 2000 },
                              ),
                            ],
                            { reportId: r.id, note: '' },
                          )
                        "
                      >
                        {{ t("冲销", "Void") }}
                      </button>
                    </td>
                  </tr>
                  <tr v-if="!detail.reports.length">
                    <td colspan="8" class="empty">
                      {{ t("暂无报工记录", "No output reported") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <section class="panel">
            <div class="section-heading">
              <h2>{{ t("终检记录", "Inspection history") }}</h2>
            </div>
            <div class="inspection" v-for="q in detail.reviews" :key="q.id">
              <span :class="['status', q.passed ? 'COMPLETE' : 'CANCELLED']">{{
                q.passed ? t("通过", "Pass") : t("返工", "Rework")
              }}</span
              ><strong
                >{{ t("观察 / 合格", "Observed / accepted") }}
                {{ q.observedQuantity }} / {{ q.acceptedQuantity }}</strong
              ><span>{{ q.createdBy }} · {{ date(q.createdAt) }}</span>
              <p>{{ q.note }}</p>
            </div>
            <p v-if="!detail.reviews.length" class="empty">
              {{ t("暂无终检记录", "No inspections yet") }}
            </p>
          </section>
          <section class="panel">
            <div class="section-heading">
              <h2>{{ t("领退与入库凭证", "Material and receipt ledger") }}</h2>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("类型", "Type") }}</th>
                    <th>{{ t("凭证", "Reference") }}</th>
                    <th>{{ t("物料", "Item") }}</th>
                    <th>{{ t("数量变化", "Qty change") }}</th>
                    <th v-if="cost">{{ t("价值变化", "Value change") }}</th>
                    <th>{{ t("原凭证ID", "Original ID") }}</th>
                    <th>{{ t("经办人", "Recorded by") }}</th>
                    <th>{{ t("时间", "Time") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="m in detail.movements" :key="m.id">
                    <td>{{ label(m.kind) }}</td>
                    <td>{{ m.reference }}</td>
                    <td>{{ display(m, "itemId") }}</td>
                    <td>{{ quantity(m.quantityDelta) }}</td>
                    <td v-if="cost">{{ cash(m.valueDelta) }}</td>
                    <td>{{ m.sourceId || "—" }}</td>
                    <td>{{ m.createdBy }}</td>
                    <td>{{ date(m.createdAt) }}</td>
                  </tr>
                  <tr v-if="!detail.movements.length">
                    <td colspan="8" class="empty">
                      {{ t("暂无仓库凭证", "No warehouse transactions") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <details class="panel history">
            <summary>
              {{ t("工单事件", "Order events") }} ({{ detail.events.length }})
            </summary>
            <ol>
              <li v-for="e in detail.events" :key="e.id">
                <strong>{{ e.kind }}</strong> · {{ e.createdBy }} ·
                {{ date(e.createdAt) }}
                <p>{{ e.note }}</p>
              </li>
            </ol>
          </details>
        </template>
        <template v-else-if="bomDetail"
          ><div class="page-heading">
            <div>
              <button
                class="back"
                @click="
                  bomDetail = null;
                  load();
                "
              >
                <ArrowLeft :size="15" />{{ t("BOM列表", "BOMs") }}
              </button>
              <h1>
                {{ bomDetail.product }} · v{{ bomDetail.bom.versionNumber }}
              </h1>
              <p>{{ bomDetail.bom.description }}</p>
            </div>
            <span :class="['status', bomDetail.bom.status]">{{
              label(bomDetail.bom.status)
            }}</span>
          </div>
          <div v-if="can('bom.write')" class="flow-actions">
            <template v-if="bomDetail.bom.status === 'DRAFT'"
              ><button @click="editBom(bomDetail)">
                {{ t("编辑草稿", "Edit draft") }}</button
              ><button class="primary" @click="bomState('activate')">
                {{ t("启用版本", "Activate version") }}</button
              ><button
                class="danger"
                @click="confirmDelete(bomDetail.bom, 'boms')"
              >
                {{ t("删除", "Delete") }}
              </button></template
            ><button
              v-if="bomDetail.bom.status === 'ACTIVE'"
              @click="bomState('archive')"
            >
              {{ t("归档版本", "Archive version") }}
            </button>
          </div>
          <section class="panel">
            <div class="section-heading">
              <h2>{{ t("每件成品用料", "Materials per finished piece") }}</h2>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("编码", "Code") }}</th>
                    <th>{{ t("物料", "Material") }}</th>
                    <th>{{ t("单位", "Unit") }}</th>
                    <th class="numeric">{{ t("每件用量", "Per piece") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="c in bomDetail.components" :key="c.id">
                    <td>{{ c.itemCode }}</td>
                    <td>{{ c.itemName }}</td>
                    <td>{{ c.unit }}</td>
                    <td class="numeric">{{ quantity(c.perUnit) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <section class="panel">
            <div class="section-heading">
              <h2>{{ t("顺序工艺", "Sequential routing") }}</h2>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>{{ t("工序", "Operation") }}</th>
                    <th>{{ t("工位", "Workstation") }}</th>
                    <th v-if="cost">
                      {{ t("标准小时费率", "Standard hourly rate") }}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="s in bomDetail.operations" :key="s.id">
                    <td>{{ s.sequence }}</td>
                    <td>{{ s.name }}</td>
                    <td>{{ s.stationName }}</td>
                    <td v-if="cost">{{ quantity(s.hourlyRate) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section></template
        >
        <template v-else>
          <div class="page-heading">
            <div>
              <p class="eyebrow">
                FORGEFLOW / {{ t("生产管理", "PRODUCTION") }}
              </p>
              <h1>
                {{
                  page === "dashboard" && profile.scope === "ASSIGNED"
                    ? t("我的生产工单", "My assigned orders")
                    : title
                }}
              </h1>
            </div>
            <div class="heading-actions">
              <button :aria-label="t('刷新', 'Refresh')" @click="refresh">
                <RefreshCw :size="16" /></button
              ><button
                v-if="page === 'items' && can('master.write')"
                @click="importItems"
              >
                {{ t("批量导入", "Import") }}</button
              ><button v-if="canAdd" class="primary" @click="add">
                <Plus :size="16" />{{ t("新增", "Add") }}
              </button>
            </div>
          </div>
          <template v-if="page === 'dashboard'"
            ><div class="overview-counts">
              <button
                v-for="st in ['RELEASED', 'IN_PROGRESS', 'REVIEW', 'READY']"
                :key="st"
                @click="
                  navigate('orders');
                  filter = st;
                  load();
                "
              >
                <small>{{ label(st) }}</small
                ><strong>{{ dash.counts[st] || 0 }}</strong>
              </button>
              <div v-if="can('stock.read')">
                <small>{{ t("低库存物料", "Low-stock items") }}</small
                ><strong>{{ dash.lowStock || 0 }}</strong>
              </div>
            </div>
            <section class="panel">
              <div class="section-heading">
                <h2>{{ t("近期工单", "Recent orders") }}</h2>
                <button
                  v-if="can('production.read')"
                  @click="navigate('orders')"
                >
                  {{ t("全部工单", "All orders") }} →
                </button>
              </div>
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>{{ t("工单", "Order") }}</th>
                      <th>{{ t("产品", "Product") }}</th>
                      <th>{{ t("计划件数", "Planned pieces") }}</th>
                      <th>{{ t("交期", "Due date") }}</th>
                      <th>{{ t("状态", "Status") }}</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="o in dash.recent" :key="o.id">
                      <td>
                        <button class="link" @click="openOrder(o.id)">
                          {{ o.number }}
                        </button>
                      </td>
                      <td>{{ o.productName }}</td>
                      <td>{{ quantity(o.plannedQuantity) }}</td>
                      <td>{{ o.dueDate }}</td>
                      <td>
                        <span :class="['status', o.status]">{{
                          label(o.status)
                        }}</span>
                      </td>
                      <td>
                        <button @click="openOrder(o.id)">
                          {{ t("打开", "Open") }}
                        </button>
                      </td>
                    </tr>
                    <tr v-if="!dash.recent.length">
                      <td colspan="6" class="empty">
                        {{ t("暂无可见工单", "No visible orders") }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section></template
          >
          <template v-else-if="page === 'reports'"
            ><form class="filters" @submit.prevent="load">
              <label
                >{{ t("实际入库日期", "Actual receiving date")
                }}<input v-model="from" type="date" /></label
              ><span>—</span
              ><input
                v-model="to"
                type="date"
                :aria-label="t('截至日期', 'End date')"
              /><button>{{ t("查询", "Apply") }}</button
              ><button
                type="button"
                @click="downloadReport(from, to).catch(fail)"
              >
                {{ t("导出CSV", "Export CSV") }}
              </button>
            </form>
            <div class="overview-counts">
              <div>
                <small>{{ t("完结工单", "Completed orders") }}</small
                ><strong>{{ report.completed || 0 }}</strong>
              </div>
              <div>
                <small>{{ t("计划件数", "Planned pieces") }}</small
                ><strong>{{ quantity(report.plannedQuantity) }}</strong>
              </div>
              <div>
                <small>{{ t("合格件数", "Accepted pieces") }}</small
                ><strong>{{ quantity(report.acceptedQuantity) }}</strong>
              </div>
              <div>
                <small>{{ t("损耗件数", "Scrapped pieces") }}</small
                ><strong>{{ quantity(report.scrappedQuantity) }}</strong>
              </div>
              <div>
                <small>{{ t("实际工时", "Actual hours") }}</small
                ><strong>{{ quantity(report.hours) }}</strong>
              </div>
            </div>
            <section v-if="cost" class="panel cost-strip">
              <div>
                {{ t("材料成本", "Material cost")
                }}<strong>{{ cash(report.materialCost) }}</strong>
              </div>
              <div>
                {{ t("人工成本", "Labor cost")
                }}<strong>{{ cash(report.laborCost) }}</strong>
              </div>
              <div>
                {{ t("合计成本", "Total cost")
                }}<strong>{{ cash(report.totalCost) }}</strong>
              </div>
              <div>
                {{ t("零良品损失", "Zero-output loss")
                }}<strong>{{ cash(report.lossCost) }}</strong>
              </div>
            </section>
            <section class="panel">
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>{{ t("工单", "Order") }}</th>
                      <th>{{ t("成品", "Product") }}</th>
                      <th>{{ t("入库时间", "Received at") }}</th>
                      <th>{{ t("合格 / 损耗", "Accepted / scrap") }}</th>
                      <th v-if="cost">{{ t("总成本", "Total cost") }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="o in report.items" :key="o.id">
                      <td>
                        <button class="link" @click="openOrder(o.id)">
                          {{ o.number }}
                        </button>
                      </td>
                      <td>{{ o.productName }}</td>
                      <td>{{ date(o.finishedAt) }}</td>
                      <td>
                        {{ quantity(o.acceptedQuantity) }} /
                        {{ quantity(o.scrappedQuantity) }}
                      </td>
                      <td v-if="cost">{{ cash(o.totalCost) }}</td>
                    </tr>
                    <tr v-if="!report.items.length">
                      <td colspan="5" class="empty">
                        {{
                          t(
                            "此期间暂无完结工单",
                            "No completed orders in this period",
                          )
                        }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section></template
          >
          <template v-else
            ><form
              class="filters"
              @submit.prevent="
                offset = 0;
                load();
              "
            >
              <div class="search">
                <Search :size="16" /><input
                  v-model="search"
                  :placeholder="
                    t('搜索编码、名称或凭证', 'Search code, name or reference')
                  "
                  :aria-label="t('搜索', 'Search')"
                  maxlength="200"
                />
              </div>
              <select
                v-if="['orders', 'boms', 'items'].includes(page)"
                v-model="filter"
                :aria-label="t('状态过滤', 'Status filter')"
                @change="
                  offset = 0;
                  load();
                "
              >
                <option value="">{{ t("全部状态", "All states") }}</option>
                <option
                  v-for="s in page === 'orders'
                    ? [
                        'DRAFT',
                        'RELEASED',
                        'IN_PROGRESS',
                        'REVIEW',
                        'READY',
                        'COMPLETE',
                        'CANCELLED',
                      ]
                    : page === 'boms'
                      ? ['DRAFT', 'ACTIVE', 'ARCHIVED']
                      : ['MATERIAL', 'FINISHED']"
                  :key="s"
                  :value="s"
                >
                  {{ label(s) }}
                </option></select
              ><select
                v-model="sort"
                :aria-label="t('排序', 'Sort')"
                @change="load()"
              >
                <option value="id">{{ t("最近创建", "Newest first") }}</option>
                <option v-if="page === 'orders'" value="dueDate">
                  {{ t("交期倒序", "Due date descending") }}
                </option>
                <option value="name">
                  {{ t("名称倒序", "Name descending") }}
                </option></select
              ><button>{{ t("查询", "Search") }}</button>
            </form>
            <section class="panel">
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th
                        v-for="c in tableColumns"
                        :key="c[0]"
                        :class="{
                          numeric: [
                            'quantity',
                            'inventoryValue',
                            'hourlyRate',
                            'quantityDelta',
                            'valueDelta',
                            'balanceQuantity',
                          ].includes(c[0]),
                        }"
                      >
                        {{ t(c[1], c[2]) }}
                      </th>
                      <th
                        v-if="
                          canEdit || ['orders', 'boms', 'items'].includes(page)
                        "
                      >
                        {{ t("操作", "Actions") }}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="r in rows" :key="r.id">
                      <td
                        v-for="c in tableColumns"
                        :key="c[0]"
                        :class="{
                          numeric: [
                            'quantity',
                            'inventoryValue',
                            'hourlyRate',
                            'quantityDelta',
                            'valueDelta',
                            'balanceQuantity',
                          ].includes(c[0]),
                        }"
                      >
                        <span
                          v-if="c[0] === 'status'"
                          :class="['status', r.status]"
                          >{{ label(r.status) }}</span
                        ><template v-else>{{ display(r, c[0]) }}</template>
                      </td>
                      <td
                        v-if="
                          canEdit || ['orders', 'boms', 'items'].includes(page)
                        "
                        class="row-actions"
                      >
                        <button
                          v-if="page === 'orders'"
                          @click="openOrder(r.id)"
                        >
                          {{ t("打开", "Open") }}</button
                        ><button v-if="page === 'boms'" @click="openBom(r.id)">
                          {{ t("详情", "Details") }}</button
                        ><template v-if="canEdit"
                          ><button
                            @click="
                              ['items', 'stations'].includes(page)
                                ? editMaster(r)
                                : editAdmin(r)
                            "
                          >
                            {{ t("编辑", "Edit") }}</button
                          ><button
                            v-if="
                              !['permissions', 'menus', 'settings'].includes(
                                page,
                              )
                            "
                            class="danger"
                            @click="confirmDelete(r)"
                          >
                            {{ t("删除", "Delete") }}
                          </button></template
                        ><template
                          v-if="
                            page === 'items' && can('stock.write') && r.enabled
                          "
                          ><button @click="stockAction(r, 'receive')">
                            {{ t("收货", "Receive") }}</button
                          ><button
                            v-if="r.kind === 'FINISHED'"
                            @click="stockAction(r, 'dispatch')"
                          >
                            {{ t("发出", "Dispatch") }}</button
                          ><button @click="stockAction(r, 'count')">
                            {{ t("盘点", "Count") }}
                          </button></template
                        >
                      </td>
                    </tr>
                    <tr v-if="!rows.length">
                      <td :colspan="tableColumns.length + 1" class="empty">
                        {{
                          loading
                            ? t("正在加载…", "Loading…")
                            : t("暂无记录", "No records")
                        }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <footer class="pagination">
                <span
                  >{{ t("共", "Total") }} {{ total }}
                  {{ t("条", "records") }}</span
                >
                <div>
                  <button
                    :disabled="offset === 0 || loading"
                    :aria-label="t('上一页', 'Previous page')"
                    @click="
                      offset--;
                      load();
                    "
                  >
                    <ChevronLeft :size="16" /></button
                  ><span
                    >{{ offset + 1 }} /
                    {{ Math.max(1, Math.ceil(total / 20)) }}</span
                  ><button
                    :disabled="(offset + 1) * 20 >= total || loading"
                    :aria-label="t('下一页', 'Next page')"
                    @click="
                      offset++;
                      load();
                    "
                  >
                    <ChevronLeft :size="16" class="next" />
                  </button>
                </div>
              </footer></section
          ></template>
        </template>
      </main>
    </div>
    <div
      v-if="dialog"
      class="modal-mask"
      @click.self="!busy && (dialog = null)"
    >
      <section
        role="dialog"
        aria-modal="true"
        :aria-label="t(dialog.zh, dialog.en)"
        :class="['modal', { wide: dialog.kind === 'bom' }]"
      >
        <header>
          <h2>{{ t(dialog.zh, dialog.en) }}</h2>
          <button
            :disabled="busy"
            :aria-label="t('关闭', 'Close')"
            @click="dialog = null"
          >
            <X :size="19" />
          </button>
        </header>
        <form @submit.prevent="submit">
          <div class="dialog-body">
            <p v-if="error" class="error" role="alert">{{ error }}</p>
            <div class="form-grid">
              <label
                v-for="f in dialog.fields"
                :key="f.name"
                :class="{
                  full: ['textarea', 'checks'].includes(f.type),
                  checkbox: f.type === 'checkbox',
                }"
                ><template v-if="f.type === 'checkbox'"
                  ><input v-model="form[f.name]" type="checkbox" />{{
                    t(f.zh, f.en)
                  }}</template
                ><template v-else
                  ><span>{{ t(f.zh, f.en) }}</span
                  ><select
                    v-if="f.type === 'select'"
                    v-model="form[f.name]"
                    :required="f.required !== false"
                  >
                    <option value="">{{ t("请选择", "Select") }}</option>
                    <option
                      v-for="o in f.options"
                      :key="o.value"
                      :value="o.value"
                    >
                      {{ o.label }}
                    </option></select
                  ><textarea
                    v-else-if="f.type === 'textarea'"
                    v-model="form[f.name]"
                    :required="f.required !== false"
                    :maxlength="f.maxlength || 2000"
                    rows="4"
                  ></textarea>
                  <div v-else-if="f.type === 'checks'" class="check-list">
                    <label
                      v-for="o in f.options"
                      :key="o.value"
                      class="checkbox"
                      ><input
                        v-model="form[f.name]"
                        :value="o.value"
                        type="checkbox"
                      />{{ o.label }}</label
                    >
                  </div>
                  <input
                    v-else
                    v-model="form[f.name]"
                    :type="f.type"
                    :required="f.required !== false"
                    :min="f.min"
                    :max="f.max"
                    :step="f.step"
                    :minlength="f.minlength"
                    :maxlength="f.maxlength || 120"
                    :autocomplete="
                      f.type === 'password' ? 'new-password' : 'off'
                    " /></template
              ></label>
            </div>
            <template v-if="dialog.kind === 'bom'"
              ><div class="section-heading">
                <h3>{{ t("每件成品用料", "Materials per piece") }}</h3>
                <button
                  type="button"
                  :disabled="form.components.length >= 50"
                  @click="form.components.push({ itemId: null, perUnit: '1' })"
                >
                  <Plus :size="14" />{{ t("添加物料", "Add material") }}
                </button>
              </div>
              <div
                class="editor-row"
                v-for="(c, i) in form.components"
                :key="i"
              >
                <select
                  v-model="c.itemId"
                  required
                  :aria-label="t('物料', 'Material')"
                >
                  <option value="">
                    {{ t("选择物料", "Select material") }}
                  </option>
                  <option
                    v-for="x in (catalog.items || []).filter(
                      (x) => x.kind === 'MATERIAL' && x.enabled,
                    )"
                    :key="x.id"
                    :value="x.id"
                  >
                    {{ x.code }} · {{ x.name }} · {{ x.unit }}
                  </option></select
                ><input
                  v-model="c.perUnit"
                  required
                  type="number"
                  min="0.000001"
                  step="0.000001"
                  :aria-label="t('每件用量', 'Quantity per piece')"
                /><button
                  type="button"
                  :disabled="form.components.length === 1"
                  :aria-label="t('移除物料', 'Remove material')"
                  @click="form.components.splice(i, 1)"
                >
                  <X :size="16" />
                </button>
              </div>
              <div class="section-heading">
                <h3>{{ t("顺序工艺", "Sequential routing") }}</h3>
                <button
                  type="button"
                  :disabled="form.operations.length >= 50"
                  @click="form.operations.push({ stationId: null, name: '' })"
                >
                  <Plus :size="14" />{{ t("添加工序", "Add operation") }}
                </button>
              </div>
              <div
                class="editor-row routing"
                v-for="(s, i) in form.operations"
                :key="i"
              >
                <span>{{ i + 1 }}</span
                ><select
                  v-model="s.stationId"
                  required
                  :aria-label="t('工位', 'Workstation')"
                >
                  <option value="">
                    {{ t("选择工位", "Select workstation") }}
                  </option>
                  <option
                    v-for="x in (catalog.stations || []).filter(
                      (x) => x.enabled,
                    )"
                    :key="x.id"
                    :value="x.id"
                  >
                    {{ x.name }}
                  </option></select
                ><input
                  v-model="s.name"
                  required
                  maxlength="120"
                  :placeholder="t('工序名称', 'Operation name')"
                  :aria-label="t('工序名称', 'Operation name')"
                /><button
                  type="button"
                  :disabled="form.operations.length === 1"
                  :aria-label="t('移除工序', 'Remove operation')"
                  @click="form.operations.splice(i, 1)"
                >
                  <X :size="16" />
                </button></div
            ></template>
            <div v-if="dialog.kind === 'about'" class="about-content">
              <img src="/brand/logo.jpg" alt="知华科技" width="54" />
              <h3>ForgeFlow 1.0.0</h3>
              <p>知华科技 · 上海如静知华信息科技有限公司</p>
              <p>
                {{
                  t(
                    "公开源码学习版，未经书面授权不得商用。",
                    "Non-commercial source edition. Written authorization is required for commercial use.",
                  )
                }}
              </p>
              <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
                >https://www.zhuatech.cn/</a
              >
              <p>WeChat: zhuatech / zhuatech2</p>
            </div>
            <p
              v-if="
                !dialog.fields.length && !['bom', 'about'].includes(dialog.kind)
              "
              class="confirm-text"
            >
              {{ t("确认执行此操作？", "Confirm this action?") }}
            </p>
          </div>
          <footer>
            <button type="button" :disabled="busy" @click="dialog = null">
              {{ t("取消", "Cancel") }}</button
            ><button
              v-if="dialog.kind !== 'about'"
              class="primary"
              :disabled="busy"
            >
              <Check :size="16" />{{
                busy ? t("正在保存…", "Saving…") : t("确认", "Confirm")
              }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </div>
</template>
