# ForgeFlow 部署与维护

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。学习版，商用需书面授权。

## 独立环境

执行 `python3 scripts/init-env.py` 创建权限0600的独立 `.env`，已存在时拒绝覆盖；不会打印密码。MySQL root、应用和初始化管理员分别生成密码，不保留共享演示密码。管理员用户名默认admin，密码从本机 `.env` 的 ADMIN_PASSWORD读取。应用仅在第一次空库建账号；后续密码通过账号管理或自助修改。

使用 `docker compose --env-file .env up --build -d`，`docker compose ps` 检查三个服务健康。`WEB_PORT` 默认8099；可设 `WEB_PORT=8109` 处理冲突，不要停止其他项目。Compose v2负责 MySQL健康→后端迁移健康→前端顺序。镜像构建 Java测试不会跳过，前端通过npm构建。独立QA指定 `-p forgeflow-quality`，与生产项目隔离。容器应用用户非root；MySQL与后端不暴露宿主端口。服务日志用 `docker compose logs --tail=150 backend mysql frontend`；不要把敏感环境打印到公开Issue。

## HTTPS与外部数据库

对外使用可信域名和 HTTPS 反向代理，设置 `COOKIE_SECURE=true`。默认 `BIND_ADDRESS=127.0.0.1`，代理与应用同机；如更改绑定，评估防火墙和访问范围。不要直接暴露数据库或将 root 账号用作应用连接。

内部隔离 Compose 网 MySQL URL 默认 `sslMode=trust`，不验证证书身份；**这不是外部连接的安全设置**。外部数据库须设 `DATABASE_URL=jdbc:mariadb://db-host:3306/zhuatech_forgeflow?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowLocalInfile=false&sslMode=verify-full`，配置可信 CA、证书主机名及最小权限非root账号。应用需首次 Flyway DDL 与业务CRUD所需权限，维护期间规划降权。不得复制内网 trust 到公网数据库。`DATABASE_USER`默认forgeflow。统一数据库UTC，页面及报表按系统timezone参数显示。

## 备份和恢复

先在测试库演练，确认源库、目标库、Compose项目、数据库名和凭证归属。停止或冻结业务写入以获得可核对的一致恢复点。SQL备份含账号哈希和业务资料，保存到私有目录，权限0600，不提交仓库。

```bash
umask 077
docker compose --env-file .env exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers --no-tablespaces --set-gtid-purged=OFF zhuatech_forgeflow' > /private/backup/forgeflow.sql
# 独立恢复环境先启动mysql并确保数据库为空；目标凭证保存在它自己的.env
docker compose -p forgeflow-restore --env-file /private/restore.env up -d mysql
docker compose -p forgeflow-restore --env-file /private/restore.env exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot zhuatech_forgeflow' < /private/backup/forgeflow.sql
# 完整SQL含外键检查设置，勿只抽取建表片段恢复
docker compose -p forgeflow-restore --env-file /private/restore.env up -d backend frontend
```

恢复后登录使用备份中的账号密码（不是恢复环境新的 ADMIN_PASSWORD），核对 Flyway版本、工单、领退流水、成品数量与价值、报工和审计。恢复环境应使用独立 WEB_PORT，不同时让用户写入源库与恢复库。生产库恢复属于替换数据操作，必须由负责人确认目标及停机安排；上面的演练不代替该确认。

## 升级与故障

先备份、检查 `V*.sql`，独立数据库验收后再更换镜像。Flyway校验既有迁移，禁止修改已应用SQL或删除历史；出现校验失败读取日志并恢复正确版本，不通过repair或跳过校验掩盖问题。当前只有V1，尚无跨版本应用升级包，保留新增版本迁移的机制。

`down`停止服务但保留数据；`down -v`删除该项目数据，仅清理确认可丢弃QA项目。不要修改其他项目容器、卷或共享依赖缓存。单实例为当前支持配置，多实例会话、共享限流和高可用备份调度需要单独实现。
