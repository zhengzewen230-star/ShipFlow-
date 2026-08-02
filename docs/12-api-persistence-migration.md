# ShipFlow API 持久化迁移说明

## 1. 迁移范围

V002 新增两张 API 支撑表：

- `api_idempotency_record`：保存通用写接口的幂等处理状态和脱敏响应。
- `auth_refresh_session`：保存 Refresh Token 摘要、轮换链和撤销状态。

迁移不改变现有业务表数据，不插入初始化幂等记录或刷新会话记录。执行后数据库表数量由28张变为30张。

## 2. 执行前检查

1. 确认当前数据库为 MySQL 8.0，字符集和排序规则为 `utf8mb4` / `utf8mb4_0900_ai_ci`。
2. 备份数据库结构和业务数据，并确认备份可恢复。
3. 确认当前 `sys_user`、`tenant` 表和主键结构与 V002 外键要求一致。
4. 先在测试环境执行 V002，再执行 `database/verify.sql`。
5. 记录迁移文件摘要、执行时间、执行账号和结果；不得记录数据库密码或 Token。

## 3. 执行顺序

```sql
SOURCE database/migrations/V002__add_api_support_tables.sql;
SOURCE database/verify.sql;
```

实际部署时可先使用 `docker cp` 将文件复制到现有 MySQL 容器，再在 MySQL 客户端中执行。不得创建、停止、删除或重建现有容器。

## 4. 业务约束

### api_idempotency_record

- `scope_tenant_id=0` 表示平台作用域，租户接口使用真实租户 ID。
- 不建立 `scope_tenant_id` 到 `tenant` 的外键，因为0不是租户主键。
- 唯一约束为 `UNIQUE(scope_tenant_id, operation_id, idempotency_key)`。
- `request_path` 用于规范化请求摘要和审计，不进入最终唯一索引。
- `request_hash` 覆盖 HTTP 方法、规范化路径、规范化查询参数和规范化请求体。
- 相同唯一键且摘要一致返回原结果；摘要不同返回 `COMMON-1009`；处理中返回 `COMMON-1010`。
- `response_body` 必须是脱敏响应；认证、Token 和密码接口不得使用通用响应缓存。

### auth_refresh_session

- 只保存 Refresh Token 的 HMAC-SHA256 或 SHA-256 摘要，不保存明文。
- `UNIQUE(token_hash)` 防止同一摘要重复登记。
- `UNIQUE(previous_session_id)` 保证同一会话最多生成一个后继会话，防止并发重复刷新。
- `tenant_id` 可为空，平台用户为空；有值时必须关联现有租户。
- 状态只能是 `ACTIVE`、`ROTATED`、`REVOKED`、`EXPIRED`。

## 5. 回滚原则

回滚只能由经过审批的数据库管理员按照备份恢复或受控 DDL 方案执行。回滚前必须停止使用相关 API 持久化能力，确认没有依赖两张新表的运行中请求，并保留审计记录。

本项目不生成自动删除生产数据、自动 DROP 生产表或自动清空表的回滚脚本。任何生产回滚都必须经过变更审批、备份确认和人工复核。

## 6. 当前状态

V002 已在 Ubuntu 的 `my-mysql-docker` 容器中实际执行并验证通过。数据库当前为30张表，未记录服务器 IP、数据库密码或其他敏感信息。

实际验证结果：

| 检查项 | 结果 |
|---|---|
| `table_count` | 30/30 PASS |
| `table_collation` | 30张表全部正确 PASS |
| `required_unique_indexes` | 18/18 PASS |
| `support_tables_exist` | 2/2 PASS |
| `support_table_collation` | 2/2 PASS |
| `refresh_foreign_keys` | 3/3 PASS |
| `support_status_checks` | 2/2 PASS |
| `approved_order_status_values` | 0个异常值 PASS |
| 原有初始化数据 | 数量保持不变 |
| 执行过程 | 无 ERROR |
