# ForgeFlow 架构与接口

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。非商业源码学习版。

Vue 3 单页面经 Nginx 同源访问 Java 21 / Spring Boot 服务。数据库 MySQL 8.4；Flyway V1 建表、JPA `validate` 验证映射，禁止自动更新。服务端保存会话；浏览器仅通过 HttpOnly Cookie 识别登录身份，CSRF token 在内存中保存。

## 状态与数据

`DRAFT → RELEASED → IN_PROGRESS → REVIEW → READY → COMPLETE`。QC失败从 REVIEW 返回 IN_PROGRESS 并重开末道；取消仅 DRAFT／RELEASED，且净领材料全部归零。BOM为 DRAFT → ACTIVE → ARCHIVED。质量检查、报工、库存和业务事件保留历史，不删除已执行凭证。

账号 → 角色 → 权限及范围；账号／物料／工位／BOM／工单归属部门。BOM → 组件与顺序工艺；工单下达复制为 OrderMaterial、WorkStep，冻结物料标识、标准用量、工位和费率。ProductionReport 保存产量、报废和工时，QualityReview 保存独立终检，StockMovement 保存原领引用及每次余额快照，BusinessEvent／AuditEvent 保存操作履历，MutationStamp 保存命令哈希及结果ID。全部引用由主外键保护。

基础部门1的悲观锁序列化写入，避免并发超领和原单超退；适合小车间，尚未进行大规模压力测试。所有有副作用的库存／报工／工单命令携带 `requestKey`（最多80字符）；同一账号、目标、请求内容重试返回同一结果，不再次过账。相同键不同内容报409。前端保存期间禁用提交，错误可用相同载荷重试；改动内容须重新打开操作获取新键。管理、BOM草稿编辑采用修订号防止旧计划覆盖；库存盘点核对账面数量快照。修改账号／菜单仍须重新检查真实接口权限。

成本响应由明确 DTO 组合，未经 `cost` 权限不包含费率、材料价值或人工成本；不能直接序列化带成本实体。部门、ASSIGNED 派工限制同时作用于工单详情、列表、首页、库存关联流水及报表。

## 主要 HTTP 接口

所有 `/api` 接口除 CSRF／login 外需要登录；写入需 CSRF。JSON错误仅返回 `code`，400输入、401会话、403权限范围、404不存在、409状态／约束／重试、413列表容量、429限流。

| 方法与路径 | 内容 / 权限 |
|---|---|
| GET `/auth/csrf`；POST `/auth/login`、`/auth/logout`、`/auth/password`；GET `/auth/me` | 会话、旧密码验证与当前安全身份；登录JSON username/password，改密码JSON oldPassword/newPassword |
| GET `/catalog` | 安全参考目录，依读取和成本权限返回 |
| GET `/lists/{resource}` | search/status/sort/desc/page/size；类型固定、各资源权限独立 |
| POST／PUT／DELETE `/master/{items,stations}[/{id}]` | 物料与工位；master.write，工位费率另需cost |
| POST `/master/items/import` | master.write，最多500条原子 JSON 数组 |
| POST／PUT／DELETE `/admin/{resource}[/{id}]` | admin + ALL范围；users/roles/departments/menus/permissions/dictionaries/settings；部分内建资源仅可编辑 |
| POST／PUT／GET／DELETE `/boms[/{id}]` | bom.read/write，保存组件与工序数组；编辑带revision |
| POST `/boms/{id}/{activate,archive}` | bom.write + revision |
| POST／PUT `/orders[/{id}]`；GET `/orders/{id}` | manage或read；保存带bomId/plannedQuantity/dueDate/requestKey，编辑另带revision |
| POST `/orders/{id}/{release,assign,start,authorize-material,send-review,cancel,void-report}` | production.manage + revision/requestKey，各状态规则适用 |
| POST `/orders/{id}/review` | quality + 独立检查人；passed/acceptedQuantity/note/revision/requestKey |
| POST `/orders/{id}/receive` | stock.write + READY；reference/revision/requestKey |
| POST `/steps/{id}/{report,finish}` | execute + 本人派工；报工goodQuantity/rejectedQuantity/hours/reference/requestKey |
| POST `/materials/{id}/{issue,return}` | stock.write；quantity/reference/requestKey，退料另有sourceId |
| POST `/items/{id}/{receive,dispatch,count}` | stock.write；数量凭证、收货price，盘点bookQuantity/note及零库存增盘price |
| GET `/dashboard`、`/reports`、`/reports.csv` | dashboard或report + production.read；报表from/to实际结单日，成本字段独立授权 |

所有路径均以 `/api` 为前缀。`requestKey` 为调用端独立生成的 UUID；数量可用十进制字符串避免浮点运算。参考脚本 `scripts/smoke.py` 是可复现调用范例。外部ERP、设备或身份单点接口没有预置，不能通过本说明推定已完成集成。
