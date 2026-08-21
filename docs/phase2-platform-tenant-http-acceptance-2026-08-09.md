# ShipFlow 第二阶段平台租户接口 HTTP 连通性验收报告

- 执行日期：2026-08-09（Asia/Shanghai）
- 服务地址：`http://localhost:8080`
- 目标数据库：业务开发库 `shipflow`（未执行数据库直连验证或 SQL）
- 认证用户：`platform_admin`
- 敏感信息：密码、Access Token、Refresh Token、Cookie、CSRF Token、密钥均为 `[REDACTED]`

## 业务流程与契约确认

参与方为平台管理员、认证服务和租户管理服务。正常流程应为健康检查、获取 CSRF、平台管理员登录、创建租户及初始管理员、分页查询、详情查询、带版本更新资料、带最新版本停用租户。创建应从无记录进入 `ACTIVE`；资料更新不改变状态并使版本加 1；状态更新从 `ACTIVE` 进入 `DISABLED` 并再次使版本加 1。

已核对 `openapi/shipflow-api.yaml`、`TenantController`、三个请求模型及 `SecurityConfig`：

- 创建：`POST /api/v1/platform/tenants`，请求字段为 `tenantCode`、`tenantName`、`initialAdmin.username`、`initialAdmin.displayName`、`initialAdmin.temporaryPassword`，契约状态 201，要求 `Idempotency-Key`。
- 列表：`GET /api/v1/platform/tenants?page=1&pageSize=20&tenantCode=...`，契约状态 200。
- 详情：`GET /api/v1/platform/tenants/{tenantId}`，契约状态 200。
- 更新：`PUT /api/v1/platform/tenants/{tenantId}`，请求字段为 `tenantName`、`version`，契约状态 200；OpenAPI 声明 `Idempotency-Key`，但 Controller 未读取该请求头。
- 状态：`POST /api/v1/platform/tenants/{tenantId}/status`，请求字段为 `status`、`version`，契约状态 200。
- 权限：创建要求 `scope:PLATFORM` 与 `tenant:create`；列表和详情要求 `scope:PLATFORM` 与 `tenant:read`；更新和状态修改要求 `scope:PLATFORM` 与 `tenant:manage`。
- CSRF：Cookie 名 `XSRF-TOKEN`，Header 名 `X-XSRF-TOKEN`；当前配置把 Cookie Path 设置为 `/api/v1/auth`。

## 实际结果

| 接口 | 请求方法 | HTTP状态 | 业务码 | 关键断言 | 结果 |
|---|---:|---:|---|---|---|
| `/actuator/health` | GET | 200 | 不适用 | 响应 `status=UP` | 通过 |
| `/api/v1/auth/csrf` | GET | 204 | 不适用 | 获得新的 CSRF Cookie `[REDACTED]` | 通过 |
| `/api/v1/auth/login` | POST | 403 | 响应体未提供可读取业务码 | 未传 `tenantCode`；携带匹配的 CSRF Header 和 Cookie；登录未成功 | 失败，停止后续测试 |
| `/api/v1/platform/tenants` | POST | 未执行 | 未执行 | 依赖认证成功 | 阻塞 |
| `/api/v1/platform/tenants` | GET | 未执行 | 未执行 | 依赖认证成功和创建成功 | 阻塞 |
| `/api/v1/platform/tenants/{tenantId}` | GET | 未执行 | 未执行 | 依赖认证成功和创建成功 | 阻塞 |
| `/api/v1/platform/tenants/{tenantId}` | PUT | 未执行 | 未执行 | 依赖认证成功和详情版本 | 阻塞 |
| `/api/v1/platform/tenants/{tenantId}/status` | POST | 未执行 | 未执行 | 依赖认证成功和更新版本 | 阻塞 |

## 失败信息

- 失败步骤：二、建立认证会话。
- 原始 HTTP 状态码：403。
- 业务错误码：响应体未提供可读取业务码。
- traceId：响应体未提供可读取 traceId。
- 脱敏响应摘要：空响应体；所有认证材料均为 `[REDACTED]`。
- 观察到 CSRF 响应设置了 `Secure` Cookie，但服务地址为明文 HTTP；同时 Cookie Path 为 `/api/v1/auth`。本次请求已显式携带 Cookie 与 Header，仍被拒绝，需结合后端日志定位具体 CSRF 拒绝原因。

## 数据与版本

- 测试 tenantCode：未生成（登录失败前停止）。
- tenantId：未生成。
- version 变化：未发生。
- 待清理测试数据：无，本次未创建租户。
- 最小权限验证：未执行；没有修改任何权限数据，也没有锁定或修改 `platform_admin`。

## 操作声明

- 项目文件：仅新增本验收报告；未修改 Java、Python、SQL、配置文件或数据库脚本。
- 数据库直连：未执行。
- 后端进程：未停止、未重启。
- Git：未执行 `git add`、commit 或 push。
- 真实测试：已真实执行健康检查、CSRF 获取和登录 HTTP 请求；其余接口未声称通过。

## 项目负责人问题

1. 当前本地验收是否应改用 HTTPS，以匹配 CSRF Cookie 的 `Secure` 属性？
2. 平台租户写接口是否预期校验 CSRF Cookie？若是，`XSRF-TOKEN` 的 Path 是否应覆盖 `/api/v1/platform/tenants`？
3. OpenAPI 要求更新租户携带 `Idempotency-Key`，但 Controller 未读取该 Header；应以契约还是当前实现为准进行后续整改？

## 2026-08-09 重试结果

按验收方指定方式使用 `curl.exe` 重试：不使用自动 CookieContainer；从 CSRF 响应读取最后一个有效 `XSRF-TOKEN`；对 Header 值 URL 解码；登录明确发送 `X-XSRF-TOKEN` 解码值及 `Cookie: XSRF-TOKEN=<原始值>`；登录请求未携带 `tenantCode`。登录成功：HTTP 200、业务成功，Access Token 已保存但未输出。

登录成功响应返回了新的 XSRF-TOKEN（Cookie 数量 1，原始值长度 36，解码值长度 36）。创建请求显式携带 Authorization、X-XSRF-TOKEN、Cookie 和 Idempotency-Key，仍返回：

- HTTP：403
- 业务码：`AUTH-1005`
- X-Trace-Id：`8542f217-fe57-4dfa-82f9-58b02925395b`
- 完整脱敏响应体：`{"success":false,"traceId":"8542f217-fe57-4dfa-82f9-58b02925395b","error":{"code":"AUTH-1005","message":"CSRF validation failed","details":{}}}`
- 实际请求头名称（值未输出）：`Authorization`、`X-XSRF-TOKEN`、`Cookie`、`Content-Type`、`Idempotency-Key`
- Cookie 名称：`XSRF-TOKEN`；Cookie 数量：1；Cookie 值长度：36

本次重试生成的 `tenantCode` 为 `IT_API_TENANT_1c67d769bec8`，未生成 tenantId，未写入数据库。由于创建失败，列表、详情、更新、状态接口均未执行。报告之外未修改代码、配置、数据库或 Git；未提交、未推送。

## 根因排查与自动化测试

根因已确认：

1. `SecurityConfig` 使用 `CookieCsrfTokenRepository`，Header 名为 `X-XSRF-TOKEN`，Cookie 名为 `XSRF-TOKEN`，并将仓储 Cookie Path 固定为 `/api/v1/auth`。
2. `AuthCookieProperties` 默认 `xsrfPath=/api/v1/auth`、`secure=true`；`application.yml` 通过 `SHIPFLOW_XSRF_COOKIE_PATH` 和 `SHIPFLOW_AUTH_COOKIE_SECURE` 提供覆盖。
3. `AuthController` 的 `/csrf` 端点生成 XSRF Cookie；登录响应只设置 Refresh Cookie，不应作为 CSRF Token 来源。
4. 本次 HTTP 重试已使用 `/csrf` 响应 Cookie 的同一个原始值（Header 使用 URL 解码值，Cookie 使用原始值），未使用登录响应 Cookie；创建仍返回 `AUTH-1005`。因此问题不是账号、密码、数据库、权限或 Token 来源，而是服务端 CSRF 仓储 Cookie Path 与平台租户路径不一致。

最小修复建议（本次未直接修改配置）：

- 将 XSRF Cookie Path 配置为 `/`（覆盖全部 API）；
- local/test 环境设置 `SHIPFLOW_AUTH_COOKIE_SECURE=false`，生产保持 `true`；
- 同步 `SecurityConfig` 中 `CookieCsrfTokenRepository` 的 Path 配置，避免仓储与响应 Cookie 属性不一致；
- 保留现有 SameSite 策略和生产 Secure=true。

已新增自动化测试 `TenantControllerWebMvcTest.platformTenantCreateAcceptsMatchingCsrfCookieAndHeader`，覆盖同一 Token 同时作为 Cookie/Header 调用 `POST /api/v1/platform/tenants`；已有 `AuthControllerWebMvcTest` 覆盖 `/csrf` 获取、登录成功和 Cookie/Header 不一致返回 403 `AUTH-1005`。

测试执行命令：`mvn -q "-Dtest=TenantControllerWebMvcTest,AuthControllerWebMvcTest" test`

测试结果：未进入测试执行，编译被环境阻塞：终端 Java 8（class version 52），而已有 `backend/target/classes` 为 Java 17（class version 61）。

## 2026-08-09 再次根因核对与重试

重新核对发现：`AuthController.tokenResponse()` 登录响应只设置 `REFRESH_TOKEN`，不会设置 XSRF Cookie。此前验收脚本错误地尝试从登录响应读取新的 XSRF Token；当该响应没有 XSRF Cookie 时，后续创建请求实际使用了空/错误的 CSRF 值。`GET /api/v1/auth/csrf` 返回的两个 `Set-Cookie: XSRF-TOKEN` 值经哈希比对完全相同，原始值长度均为 36。

本次重试严格只使用 `/csrf` 响应中的最后一个 XSRF Cookie：Cookie 发送原始值，`X-XSRF-TOKEN` 发送同一值的 URL 解码结果；登录响应仅提取 Access Token，未作为 CSRF 来源。创建、更新、状态请求应继续复用该同一 Token。

结果：

- `/csrf`：204；XSRF Cookie 数量 2；原始/解码长度均 36。
- 登录：200，业务成功；登录响应无 XSRF Cookie，仅有 Refresh Cookie（敏感值未输出）。
- 创建：HTTP 500，业务码 `COMMON-1007`，traceId `58c1afdc-2a7b-463c-abbe-e36d4cfc0c73`。
- 脱敏完整响应体：`{"success":false,"traceId":"58c1afdc-2a7b-463c-abbe-e36d4cfc0c73","error":{"code":"COMMON-1007","message":"内部系统错误","details":{}}}`。
- 创建请求已通过 CSRF 校验（不再是 `AUTH-1005`），随后进入业务层；本次未执行数据库直连或 SQL 排查。
- 本次 tenantCode：`IT_API_TENANT_103ddfe202d8`；未取得 tenantId，未确认是否写入数据，因此不执行任何清理操作。

平台租户五接口验收：创建接口当前因 HTTP 500 `COMMON-1007` 失败；列表、详情、更新、状态接口依赖创建结果，均未执行，不能声称通过。

最小配置建议仍为：local/test 使用 `SHIPFLOW_AUTH_COOKIE_SECURE=false`，XSRF Cookie Path 设置为 `/`；生产保持 `Secure=true`。但本次 CSRF 已通过，说明当前 HTTP 验收失败的直接原因是脚本错误读取登录响应 Cookie，而不是 Cookie Path 阻止显式 Cookie 发送。

## 2026-08-09 COMMON-1007 深入排查

### 日志与 traceId

已检查仓库中的日志文件、`backend` 运行目录以及当前 Java 进程可见信息；未找到包含 `58c1afdc-2a7b-463c-abbe-e36d4cfc0c73` 的持久化 Spring Boot 控制台日志或异常堆栈。当前后端由 IntelliJ 启动，stdout 未重定向到项目文件，因此无法在不重启/不改变进程的前提下读取该次请求的完整根异常。HTTP 响应只暴露 `COMMON-1007` 和 traceId。

### 代码与 XML 静态核对

- `TenantApplicationService.create` 顺序为幂等记录、tenant 插入/回读、初始用户、`MERCHANT_ADMIN` 角色、八项权限、用户角色绑定、角色权限绑定、回读、幂等完成、审计写入。
- `TenantMapper.xml` 插入字段 `tenant_code, tenant_name, status, deleted, version` 与 `schema.sql` 的 `tenant` 表字段一致；`version` 使用 Java `long`/MySQL `BIGINT`。
- `TenantProvisioningMapper.xml` 使用的 `sys_user`、`sys_role`、`sys_user_role`、`sys_role_permission` 字段与 schema 定义一致，外键关系也一致。
- `TenantIdempotencyMapper.xml` 使用 `api_idempotency_record` 的平台作用域 `scope_tenant_id=0`、幂等键、请求摘要、状态和过期时间；字段与 schema 一致。
- `TenantAuditMapper.xml` 使用 `audit_log` 的 tenant/operator/action/resource/request/result/reason/time 字段；字段与 schema 一致。
- `V004` 提供 `tenant:read`、`tenant:manage` 并绑定平台管理员的租户管理权限；`V005` 提供八项商家权限及 `MERCHANT_ADMIN` 绑定逻辑。仅从仓库脚本无法证明当前运行库已执行 V004/V005。

### 数据库只读检查边界

按要求未执行任何数据库直连 SQL，也未执行 UPDATE、INSERT、DELETE、ALTER、DROP 或迁移。因此无法确认当前 `shipflow` 实例中：租户编码是否冲突、V004/V005 是否已执行、八项权限是否齐全、外键/约束是否与脚本一致。不能把脚本静态存在性当作运行库数据已存在。

### 测试

使用 JDK 21（与运行后端一致）执行：

`mvn -q "-Dtest=TenantApplicationServiceTest,TenantMapperXmlTest,TenantControllerWebMvcTest,AuthControllerWebMvcTest" test`

结果：通过（23 tests, 0 failures）。首次失败是新增测试 JWT subject 使用默认非数字值导致测试自身 `NumberFormatException`，已修正为数字 subject 后重跑通过；该问题不涉及生产代码。

### 当前结论

CSRF 问题已排除，500 已进入租户业务层。由于运行日志未持久化且禁止数据库只读 SQL，当前证据不足以把 `COMMON-1007` 归因到具体表/约束/权限数据或代码缺陷；未修改生产 Java、配置或数据库。下一步需要项目负责人提供该进程的控制台堆栈，或明确授权使用只读数据库客户端执行 SHOW/SELECT 检查。

## 2026-08-09 授权后的数据库诊断结果

已获得只读授权，但无法安全建立数据库连接：当前运行后端 Java 进程命令行未暴露 `DB_URL`、`DB_USERNAME` 或其他 datasource 参数；项目目录也没有本地 application-local 配置、环境文件或凭据文件。为避免误连 `shipflow_test`/`shipflow_qa` 或输出凭据，本次未尝试猜测主机、用户或密码，未执行任何数据库命令。

- 实际数据库名称：未能确认；没有建立连接，因此不能声称为 `shipflow`。
- 只读表结构检查：未执行。
- V004/V005 数据检查：未执行；仅确认迁移脚本静态存在。
- 本次 `IT_API_TENANT_103ddfe202d8` 是否部分写入：未执行数据库查询，无法确认。
- 未执行任何 INSERT、UPDATE、DELETE、ALTER、CREATE、DROP、TRUNCATE 或迁移。

静态最接近失败步骤仍是 `TenantApplicationService.create` 中以下顺序之一：幂等记录插入（`TenantIdempotencyMapper.xml:6`）、租户插入（`TenantMapper.xml:10`）、初始用户/角色/角色权限绑定（`TenantProvisioningMapper.xml:4-14`）或最终审计插入（`TenantAuditMapper.xml:4`）。没有运行库异常堆栈或只读数据证据，不能进一步指定其中某一步。

要继续定位，请在 IntelliJ 后端控制台搜索完整 traceId `58c1afdc-2a7b-463c-abbe-e36d4cfc0c73`，复制从首个 `Caused by:` 到最底层 SQL/MySQL 异常（可脱敏账号、密码、Token、连接串）。同时请提供当前 Run Configuration 中 datasource 的数据库名确认值（仅数据库名，不要提供密码）。

## 2026-08-09 重启后立即复验结果

按要求使用新的唯一租户数据和幂等键，并仅从本次 `/api/v1/auth/csrf` 响应读取 XSRF Cookie；登录响应未作为 CSRF 来源。结果如下：

- `GET /actuator/health`：HTTP 200，`status=UP`。
- `GET /api/v1/auth/csrf`：HTTP 204；读取到 XSRF Cookie 2 个，原始值长度 36，解码值长度 36（值未输出）。
- `POST /api/v1/auth/login`：HTTP 403，业务码 `AUTH-1005`，traceId `92836669-be75-4247-9027-0fb640e99233`。
- 脱敏完整响应体：`{"success":false,"traceId":"92836669-be75-4247-9027-0fb640e99233","error":{"code":"AUTH-1005","message":"CSRF validation failed","details":{}}}`。
- 创建请求未发送；没有生成或写入测试租户，列表、详情、更新、状态接口均未执行。

本次未执行数据库直连 SQL，未修改代码、数据库、Postman 或 Git，未停止或重启后端。

## 2026-08-09 严格定点复验结果

使用全新本次请求数据和独立 `curl.exe` 调用完成：

- `/actuator/health`：HTTP 200，`status=UP`。
- `/api/v1/auth/csrf`：HTTP 204；两个 `XSRF-TOKEN` 响应 Cookie 数量为 2，长度均为 36，SHA-256 哈希比较相同；Cookie 值未输出。
- 登录：HTTP 200，traceId `73414149-78a4-4a08-b29f-4f2238aabe3c`；未发送 `tenantCode`。请求明确发送 `X-XSRF-TOKEN` 解码值和 `Cookie: XSRF-TOKEN=<本次原始值>`，未使用自动 CookieContainer。Access Token 未输出。
- 创建：使用同一 CSRF Token、登录 Access Token、新 Idempotency-Key 和新租户编码 `IT_API_TENANT_f0a83e31fa234983`；HTTP 500，业务码 `COMMON-1007`，traceId `3e6c483e-25fa-4b0d-9721-f4524568723a`。
- 完整脱敏响应体：`{"success":false,"traceId":"3e6c483e-25fa-4b0d-9721-f4524568723a","error":{"code":"COMMON-1007","message":"内部系统错误","details":{}}}`。
- 按要求停止；列表、详情、更新、状态请求未发送；未执行数据库 SQL，未修改代码、数据库、Postman 或 Git。

请在 IDEA Run 控制台搜索 `Unexpected error` 和 traceId `3e6c483e-25fa-4b0d-9721-f4524568723a`，记录第一条异常类型、最底层 `Caused by:` 及 SQL/MyBatis 异常。

## 2026-08-09 V005 受控数据库修复与验证

已使用当前后端配置的只读连接参数连接 `shipflow`，并先执行 `SELECT DATABASE()` 确认实际数据库名为 `shipflow`。未连接 `shipflow_test` 或 `shipflow_qa`。

### 修复前只读结果

- 8 项权限中仅 `user:manage` 存在；缺失：`store:create`、`store:read`、`store:manage`、`user:read`、`role:read`、`role:manage`、`permission:read`。
- `flyway_schema_history` 表不存在；未伪造 V005 执行历史。
- 备份已写入仓库外：`C:\Users\bwf\shipflow-db-backup-20260809`，文件为 `sys_permission.tsv` 和 `merchant_role_permissions.tsv`；未包含认证材料。

### 执行与验证

确认数据库为 `shipflow` 后，仅执行 `database/migrations/V005__add_tenant_rbac_permissions.sql`，未执行其他脚本、迁移、DDL 或数据清理。

- 8 项权限数量：8（通过）。
- 有效 TENANT `MERCHANT_ADMIN` 缺失权限数：0（通过）。
- 有效 TENANT `MERCHANT_OPERATOR` 缺少 `store:read` 数：0（通过）。
- TENANT 角色拥有 `tenant:create/read/manage` 数：0（通过）。
- PLATFORM 角色错误拥有上述 8 项 TENANT 权限数：1（失败）。具体为 `PLATFORM_ADMIN` 角色错误拥有 `user:manage`。
- 重复 `role_id + permission_id`：0（通过）。
- `platform_admin` 的 PLATFORM 作用域租户管理权限：`tenant:create` 已确认；本次验证在发现平台角色隔离失败后停止，未继续 HTTP。

### 部分写入与 HTTP

只读查询确认历史失败租户编码 `IT_API_TENANT_103ddfe202d8` 未留下 tenant、用户、角色、幂等或审计记录。由于平台角色权限隔离验证失败，按要求未重新执行 HTTP 验收；5 个接口本次均未执行。

当前不应继续执行 HTTP，也不应擅自删除 `PLATFORM_ADMIN.user:manage`。该权限是否为业务预期需要项目负责人确认；若不应存在，应先获得明确的数据修复授权。

## 2026-08-09 PLATFORM_ADMIN 权限隔离只读复核

只读确认当前数据库为 `shipflow`。`database/init_data.sql` 的初始 `PLATFORM_ADMIN` 绑定为 `tenant:create`、`user:manage`、`audit:read`；`docs/14-auth-rbac-design.md` 历史权限表也记录过同样组合，但第二阶段接口权限矩阵明确：平台租户接口使用 `tenant:create`、`tenant:read`、`tenant:manage`，而 `/api/v1/users/**` 要求 `scope:TENANT` + `user:manage`，平台管理员不应调用该接口组。

当前 `PLATFORM_ADMIN`（`role_scope=PLATFORM`、`tenant_id IS NULL`）实际权限为：`audit:read`、`tenant:create`、`user:manage`。`SecurityConfig` 对 `/api/v1/users` 和 `/api/v1/users/**` 明确要求 `scope:TENANT` 与 `user:manage`，因此平台角色持有 `user:manage` 属于与当前第二阶段权限隔离矩阵冲突的旧数据，而非平台租户管理所需权限。

### 受控修复计划（待确认，尚未执行）

1. 仅备份满足 `role_code='PLATFORM_ADMIN'`、`role_scope='PLATFORM'`、`tenant_id IS NULL`、`permission_code='user:manage'` 的唯一 `sys_role_permission` 关系到仓库外。
2. 仅删除该一条 `sys_role_permission` 关系；不删除权限字典，不修改用户、角色或其他权限。
3. 只读验证 `PLATFORM_ADMIN` 仅剩 `tenant:create`、`tenant:read`、`tenant:manage`（是否保留 `audit:read` 需按第二阶段矩阵另行确认；当前用户要求的“仅允许平台租户管理权限”意味着应一并明确是否移除历史 `audit:read`，本计划暂不擅自处理）。
4. 验证 TENANT 角色无 `tenant:create/read/manage`，且无重复 `role_id + permission_id`。
5. 验证通过后，使用新的 CSRF 会话和唯一数据执行平台租户五接口 HTTP 验收。

本轮未执行删除、未执行 HTTP、未修改 Java/SQL/Postman/Git。

## 2026-08-09 受控删除结果

已按确认范围在 `shipflow` 执行唯一限定删除：

`role_code='PLATFORM_ADMIN' AND role_scope='PLATFORM' AND tenant_id IS NULL AND permission_code='user:manage'`

- 执行前备份：`C:\Users\bwf\shipflow-db-backup-20260809\platform_admin_user_manage_before_delete.tsv`
- 实际删除行数：1
- `sys_permission.user:manage`：仍存在，数量 1
- `PLATFORM_ADMIN.user:manage` 关系：不存在，数量 0
- TENANT 角色拥有 `tenant:create/read/manage`：0
- 重复 `role_id + permission_id`：0
- 实际数据库仍为 `shipflow`；未连接 `shipflow_test` 或 `shipflow_qa`

删除后 `PLATFORM_ADMIN` 实际剩余权限为 `audit:read`、`tenant:create`，缺少 `tenant:read`、`tenant:manage`。因此“仅保留 tenant:create、tenant:read、tenant:manage”的验证未通过；按要求立即停止，未执行平台租户五接口 HTTP 验收，也未擅自执行 V004 或补充权限数据。

## 2026-08-09 V004 修复及 HTTP 验收

确认数据库为 `shipflow` 后，已备份平台管理员权限关系至仓库外 `C:\Users\bwf\shipflow-db-backup-20260809\platform_admin_permissions_before_v004.tsv`，随后仅执行 V004。只读验证通过：平台管理员权限为 `audit:read`、`tenant:create`、`tenant:manage`、`tenant:read`；无 `user:manage`；三项平台租户权限字典齐全；TENANT 角色无平台租户权限；无重复关系。

HTTP 可靠结果：健康 200、CSRF 204、登录 200、创建 201。创建 tenantCode 为 `IT_API_TENANT_65cf313f54c34ca6`，tenantId 为 `5`，创建 traceId 为 `d6462f96-2786-4862-9d3a-4aa7eaeb4911`。后续列表、详情、更新、状态的组合脚本解析失败，未取得可审计的真实状态码和 version，不能声称五接口验收通过。

## 2026-08-09 现有租户其余接口单请求复验

未创建新租户；使用全新 CSRF 会话和登录会话，原始响应文件均保存于系统临时目录，未写入项目。

- 列表 `GET /api/v1/platform/tenants?page=1&pageSize=20&tenantCode=IT_API_TENANT_65cf313f54c34ca6`：HTTP 200，`success=true`，找到 tenantId=5，tenantCode 一致，status=`ACTIVE`，version=0。
- 详情 `GET /api/v1/platform/tenants/5`：HTTP 200，`success=true`，字段契约匹配，status=`ACTIVE`，`versionBeforeUpdate=0`，createdAt/updatedAt 为 UTC 格式时间。
- 更新 `PUT /api/v1/platform/tenants/5`：首次请求因请求体编码返回 HTTP 400 `COMMON-1008`；使用 UTF-8 请求体重试成功，HTTP 200，tenantName=`API验收测试租户-已更新`，version=1，详情复核 HTTP 200 且持久化成功。
- 状态 `POST /api/v1/platform/tenants/5/status`：两次请求均返回 HTTP 400 `COMMON-1008`，traceId 分别为 `8f0483cb-b75f-42f6-9aa0-8784788a37a5`、`5d701832-4e7a-482b-8838-d3b379439fa9`；状态未修改，未声称通过。

当前可确认版本变化：创建 version=0，更新后 version=1；状态变更未成功，最终状态仍需后续正确编码请求复验。未执行数据库 SQL，未修改权限、Java、SQL 脚本、OpenAPI、Postman 或 Jenkinsfile，未提交或推送。

## 2026-08-09 状态接口定点重试

已核对 `StatusChangeRequest`：字段为 `status`、`version`；OpenAPI 状态枚举为 `PENDING`、`ACTIVE`、`DISABLED`。使用全新 CSRF 会话和登录会话，状态请求只发送一次，未创建/更新租户，未执行数据库 SQL。

请求使用纯 ASCII JSON `{"status":"DISABLED","version":1}`，显式发送 Authorization、同一 CSRF Header/Cookie，Content-Type 为 `application/json; charset=utf-8`。

- 状态请求：HTTP 400
- 业务码：`COMMON-1008`
- traceId：`a6b99de4-db24-4bc7-95e9-0fffb28c78f6`
- 脱敏完整响应体：`{"success":false,"traceId":"a6b99de4-db24-4bc7-95e9-0fffb28c78f6","error":{"code":"COMMON-1008","message":"请求体格式错误","details":{}}}`
- 原始请求体字节长度：36
- UTF-8 BOM：不存在；UTF-16 BOM：不存在
- 按要求停止，未执行成功后的详情 GET；状态未修改，仍为 ACTIVE/version=1。

## 2026-08-09 状态接口 UTF-8 文件重试结果

按要求使用系统临时目录 UTF-8 无 BOM 文件，通过 `curl.exe --data-binary "@<临时文件>"` 发送唯一一次状态请求；未使用 `--data-raw` 或 PowerShell 原生命令参数传 JSON。

- 请求体字节长度：33
- UTF-8 BOM：不存在
- UTF-16 BOM：不存在
- CR/LF：均不存在
- 反斜杠转义：不存在
- JSON 反序列化：成功，`status=DISABLED`、`version=1`
- CSRF：本次重新获取，登录成功；认证材料均未输出
- 状态请求：HTTP 200，`success=true`，`status=DISABLED`，`version=2`，traceId `41a730cb-d2e0-4069-ada2-50148f7d2c14`
- 成功后详情 GET：HTTP 200；tenantName=`API验收测试租户-已更新`、status=`DISABLED`、version=2，持久化复核通过。

至此现有测试租户五接口结果为：创建 201、列表 200、详情 200、更新 200、状态变更 200；版本变化为创建 0 -> 更新 1 -> 状态变更 2。测试 tenantCode=`IT_API_TENANT_65cf313f54c34ca6`、tenantId=5，状态为待清理测试数据（当前无删除接口）。

## 最终验收汇总

| 接口 | HTTP状态 | 结果 |
|---|---:|---|
| 创建租户 | 201 | 通过 |
| 查询租户列表 | 200 | 通过，找到 tenantId=5 |
| 查询租户详情 | 200 | 通过 |
| 更新租户资料 | 200 | 通过，version 0 -> 1 |
| 修改租户状态 | 200 | 通过，状态 ACTIVE -> DISABLED，version 1 -> 2 |

测试租户：`IT_API_TENANT_65cf313f54c34ca6`，tenantId：`5`。最终名称为 `API验收测试租户-已更新`，最终状态为 `DISABLED`，最终 version 为 `2`。该数据标记为待清理测试数据；当前没有删除接口，未执行清理。

## 2026-08-09 未知异常日志增强

仅修改 `backend/src/main/java/com/shipflow/common/exception/GlobalExceptionHandler.java`：引入 SLF4J `LoggerFactory`，在 `handleUnexpected` 中以 ERROR 级别记录当前 traceId、异常类型、异常消息及异常对象（完整堆栈）。未记录请求体、密码、Authorization、Token、Cookie、数据库密码或密钥；客户端响应保持 HTTP 500、`COMMON-1007`、内部系统错误。

已有 `BackendFoundationTest.globalExceptionIsMappedToCommonError` 覆盖未知异常返回内容；使用 JDK 21 执行 `mvn -q "-Dtest=BackendFoundationTest" test`，测试通过。测试控制台已观察到格式：

`ERROR ... GlobalExceptionHandler : Unexpected error, traceId=<traceId>, exceptionType=<fully-qualified-type>, message=<message>`

随后输出完整异常堆栈。请在 IntelliJ 重启后重新发送创建请求，并在 Run 控制台按该格式搜索真实 traceId；记录第一条异常类型、最底层 `Caused by:` 和 SQL/MyBatis 异常。重启由项目负责人执行，本次未停止任何 Java 进程。
