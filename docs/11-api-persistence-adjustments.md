# API 持久化变更提案

## 1. 目的

本文件只记录 API 契约评审提出的数据库持久化调整，当前不修改 `schema.sql`、`init_data.sql`、`verify.sql`，不执行数据库迁移。

如果后续实施，两张表将使数据库表数量从 28 张增加到 30 张，`verify.sql` 的期望表数量和统计项需要同步调整。

## 2. `api_idempotency_record` 提案

### 2.1 业务用途

用于持久化跨接口幂等请求的请求指纹、处理中状态、原始响应和资源关联，解决服务重启、异步处理和重复请求下只依赖业务表唯一索引不足的问题。

### 2.2 字段建议

| 字段 | 建议类型 | 说明 |
|---|---|---|
| `id` | `BIGINT` | 主键，JSON 返回字符串 |
| `scope_tenant_id` | `BIGINT` | 幂等作用域；平台接口使用 0，租户接口使用租户 ID |
| `idempotency_key` | `VARCHAR(128)` | 请求头幂等键 |
| `http_method` | `VARCHAR(16)` | HTTP 方法 |
| `request_path` | `VARCHAR(255)` | 归一化接口路径 |
| `request_hash` | `CHAR(64)` | 规范化请求体摘要 |
| `processing_status` | `VARCHAR(32)` | `PROCESSING`、`SUCCEEDED`、`FAILED`、`EXPIRED` |
| `response_status` | `INT` | 原始 HTTP 状态码 |
| `response_body` | `JSON` | 脱敏后的响应体 |
| `resource_type` | `VARCHAR(64)` | 资源类型 |
| `resource_id` | `BIGINT` | 资源 ID |
| `expires_at` | `DATETIME(3)` | 幂等记录过期时间，UTC |
| `created_at` | `DATETIME(3)` | 创建时间，UTC |
| `updated_at` | `DATETIME(3)` | 更新时间，UTC |

### 2.3 唯一约束建议

```text
UNIQUE(scope_tenant_id, operation_id, idempotency_key)
```

请求体 Hash 不放入唯一键，但相同唯一键再次请求时必须比较 `request_hash`：相同返回原响应，不同返回 `COMMON-1009`；仍处于处理中返回 `COMMON-1010`。`request_path` 保留用于请求摘要和审计，不进入最终唯一索引。

## 3. `auth_refresh_session` 提案

### 3.1 业务用途

用于 Refresh Token 轮换、Token 家族撤销、重复使用检测和跨租户登录会话隔离。

### 3.2 字段建议

| 字段 | 建议类型 | 说明 |
|---|---|---|
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 用户 ID |
| `tenant_id` | `BIGINT` | 租户 ID；平台用户可为空 |
| `token_hash` | `CHAR(64)` | Refresh Token 摘要，禁止保存明文 |
| `family_id` | `CHAR(36)` | Token 家族 ID |
| `previous_session_id` | `BIGINT` | 上一个会话 ID，可为空 |
| `status` | `VARCHAR(32)` | `ACTIVE`、`ROTATED`、`REVOKED`、`EXPIRED` |
| `expires_at` | `DATETIME(3)` | 过期时间，UTC |
| `revoked_at` | `DATETIME(3)` | 撤销时间，UTC |
| `created_at` | `DATETIME(3)` | 创建时间，UTC |
| `updated_at` | `DATETIME(3)` | 更新时间，UTC |

数据库只能保存 Refresh Token 摘要。原始 Refresh Token 只在签发和客户端传输过程中存在，不得写入日志、响应之外的持久化字段或数据库。

## 4. 后续同步事项

实施上述表时需要同步：

1. 增加两张表及外键、索引和中文 COMMENT。
2. 将数据库表数量从 28 更新为 30。
3. 更新 `database/verify.sql` 的表数量检查。
4. 增加幂等记录和 Refresh Token 会话初始化/清理策略。
5. 更新 `docs/05-database-design.md`、部署说明和数据库验证报告。
6. 增加并发重复请求、请求体变化、Token 轮换和 Token 重放测试。

本提案当前不执行，现有 MySQL 数据库保持不变。

## 5. 第二轮评审修正

### 5.1 api_idempotency_record

增加字段：`operation_id VARCHAR(128) NOT NULL`。

最终唯一约束调整为：

```text
UNIQUE(scope_tenant_id, operation_id, idempotency_key)
```

`request_hash` 必须覆盖规范化后的 HTTP 方法、规范化路径、规范化查询参数和规范化请求体。相同作用域、接口操作和幂等键的请求如果摘要不同，返回 `COMMON-1009`；已有请求仍处于处理中时返回 `COMMON-1010`。

### 5.2 auth_refresh_session

增加并确认以下约束和索引：

```text
UNIQUE(token_hash)
UNIQUE(previous_session_id)
INDEX(user_id, status, expires_at)
INDEX(family_id, status)
```

`UNIQUE(previous_session_id)` 保证同一个 Refresh Token 会话最多生成一个后继会话，防止并发刷新产生多个有效后继。数据库仍只保存 Refresh Token 摘要，不保存明文。
