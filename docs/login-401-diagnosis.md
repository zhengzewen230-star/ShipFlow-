# Login 401 AUTH-1001 诊断报告

## 执行信息

- 执行时间：2026-08-09 12:11:14 +08:00
- 当前分支：`feature/tenant-rbac`
- 诊断范围：只读数据库查询、代码/配置读取、BCrypt 定点验证和受控实例启动检查。
- 未记录数据库密码、完整 `password_hash`、JWT 私钥、HMAC 密钥、Access Token、Refresh Token 或 Cookie。

## 数据库连接

- 实际数据库名：`shipflow`
- hostname：`9b244d2d5246`
- port：`3306`
- `CURRENT_USER()`：`root@%`
- 已确认未连接 `shipflow_test` 或 `shipflow_qa`。

## 数据检查

### 租户

`TENANT_DEMO_001` 存在，`status = ACTIVE`，`deleted = 0`。

### 用户

`merchant_admin_001`：

- `tenant_id = 1`
- `scope_tenant_id = 1`
- `status = ACTIVE`
- `deleted = 0`

用户关联的租户 ID 为 `1`，租户状态为 `ACTIVE` 且未删除。

### 角色

- 角色：`MERCHANT_ADMIN`
- `role_scope = TENANT`
- 角色状态：`ACTIVE`
- 角色 `deleted = 0`
- 关联租户：`tenant_id = 1`
- 角色权限关联存在。

### 密码摘要脱敏信息

- `merchant_admin_001`：长度 `66`，前缀 `$2a$2a$`。
- `platform_admin`：长度 `60`，前缀 `$2a$10$`。

未记录任何完整数据库摘要。

## BCrypt 验证

使用项目依赖的 `BCryptPasswordEncoder`，候选明文仅为 `123456`：

- `merchant_admin_001` 数据库摘要：`matches=false`
- `database/init_data.sql` 中摘要：`matches=false`
- `platform_admin` 数据库摘要：`matches=false`

生成的待审批新摘要（不是数据库现有摘要）：

`$2a$10$SAdx34roAZNYsuH0jyCjeu0D3iF/nBikNAQfFNDpt.hErC8HJG/l.`

## 登录链路结论

失败步骤为 **D. BCrypt**。租户查询、用户查询、用户/租户状态校验和角色/权限查询均满足登录条件，未进入 Token/Refresh Session 阶段。

结论：可以确认本次 `401 AUTH-1001` 由数据库密码摘要数据导致。`merchant_admin_001` 摘要格式不是有效 BCrypt，且初始化脚本摘要也不匹配 `123456`。`platform_admin` 摘要格式正常但同样不匹配 `123456`，存在同类密码契约问题。

## 18080 受控实例

- 未停止或修改现有 `8080` 进程。
- 受控实例未成功启动，未监听 `18080`。
- 启动失败原因：Maven 使用 Java 8 编译测试源码，而现有 `target/classes` 包含 Java 17/21 字节码，报 `class file has wrong version 61.0, should be 52.0`。
- 因实例未启动，未执行 `/actuator/health`、`/api/v1/auth/csrf` 或 HTTP 登录请求。
- 未发送 POST 登录请求，避免可能写入 Refresh Session 或审计记录。

## 待审批 SQL

仅生成，未执行：

```sql
UPDATE shipflow.sys_user
SET password_hash = '$2a$10$SAdx34roAZNYsuH0jyCjeu0D3iF/nBikNAQfFNDpt.hErC8HJG/l.'
WHERE id = 2
  AND username = 'merchant_admin_001'
  AND tenant_id = 1
  AND scope_tenant_id = 1
  AND deleted = 0;
```

## 未完成项

- 18080 HTTP 健康、CSRF 和登录验证：受控实例因 Java/Maven 字节码版本冲突未启动。
- 现有 8080 进程的完整启动参数/profile：Windows 进程命令行未提供可用应用参数，无法据此确认其运行时 `DB_URL`。
- 未执行任何 `UPDATE`、`DELETE`、`DROP`、`ALTER`、`INSERT`、迁移、数据库重建、代码修改、提交或推送。

## Git 检查

- `git diff --check`：通过（无 whitespace 错误）。
- `git status`：工作区已有变更：`.gitignore`、`backend/src/test/java/com/shipflow/security/jwt/JwtCryptoTest.java`、`backend/src/test/java/com/shipflow/auth/InitDataPasswordContractTest.java`、`docs/20-auth-api-test-framework-guide.md`；本次仅新增本诊断文档。
