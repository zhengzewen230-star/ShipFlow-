# ShipFlow HTTP 连通性验收报告

- 执行日期：2026-08-09（Asia/Shanghai）
- 服务地址：`http://localhost:8080`
- 目标数据库：`shipflow`
- 验收方式：Windows PowerShell，通过 HTTP API；未执行 SQL、迁移或删除操作
- 敏感信息：密码、Access Token、Refresh Cookie、CSRF Token 均未写入本报告

## 业务流程与验收边界

参与方为平台管理员、认证服务、平台租户管理服务、新租户管理员、店铺服务、用户服务和 RBAC 服务。

正常流程应为：健康检查 -> 获取 CSRF -> 平台管理员登录 -> 创建租户及初始管理员 -> 租户查询、更新和状态切换 -> 新租户管理员登录 -> 店铺五接口 -> 用户六接口 -> RBAC 四接口。

创建接口应使用唯一 `Idempotency-Key`，更新和状态变更应携带当前 `version`，服务端成功更新后版本递增。异常路径包括认证失败、权限不足返回 `403`、跨租户资源不可见并返回 `404`，以及重复请求不得重复创建资源。

本次在平台管理员登录阶段被 CSRF 校验阻断。由于后续接口依赖 Access Token 和租户资源 ID，未绕过认证继续调用，也未修改数据库数据。

## 环境与数据库确认

| 检查项 | 结果 |
|---|---|
| `DB_URL` 数据库名 | `shipflow` |
| `SHIPFLOW_IT_DB_URL` 数据库名 | `shipflow_test`，未使用 |
| 密码变量 | `SHIPFLOW_PLATFORM_TEST_PASSWORD` 已存在，仅进程内读取，未输出 |
| 后端进程 | Java 21，IntelliJ 启动；未重启或修改 |

## 实际执行结果

### 基础与认证

| 方法 | 路径 | 状态码 | `success` | `error.code` | 结果 |
|---|---|---:|---|---|---|
| GET | `/actuator/health` | 200 | 不适用 | 不适用 | 通过，响应 `status=UP` |
| GET | `/api/v1/auth/csrf` | 204 | 不适用 | 不适用 | 通过，返回两个 `XSRF-TOKEN` Cookie；值已脱敏 |
| POST | `/api/v1/auth/login` | 403 | false | `AUTH-1005` | 失败，CSRF validation failed |

登录请求使用管理员账号 `platform_admin`，密码仅从 `SHIPFLOW_PLATFORM_TEST_PASSWORD` 读取。未获得可用 Access Token、Refresh Cookie 或 CSRF 会话。

为定位客户端取值问题，使用 `curl.exe` 和 PowerShell 内存请求分别尝试了响应中的两个 CSRF Cookie、原始值/URL 解码值及其组合，全部返回 `403 AUTH-1005`。未打印任何 Cookie 或 Token 值。

### 平台租户接口（5 个）

| 方法和路径 | 状态码 | `success` / `error.code` | 资源 ID | version | 结果 |
|---|---:|---|---|---|---|
| POST `/api/v1/platform/tenants` | 未执行 | - | - | - | 依赖登录失败 |
| GET `/api/v1/platform/tenants` | 未执行 | - | - | - | 依赖创建失败 |
| GET `/api/v1/platform/tenants/{tenantId}` | 未执行 | - | - | - | 依赖创建失败 |
| PUT `/api/v1/platform/tenants/{tenantId}` | 未执行 | - | - | - | 依赖创建失败 |
| POST `/api/v1/platform/tenants/{tenantId}/status` | 未执行 | - | - | - | 依赖创建失败 |

### 店铺接口（5 个）

| 方法和路径 | 状态码 | `success` / `error.code` | 资源 ID | version | 结果 |
|---|---:|---|---|---|---|
| POST `/api/v1/stores` | 未执行 | - | - | - | 依赖租户管理员登录失败 |
| GET `/api/v1/stores` | 未执行 | - | - | - | 依赖创建失败 |
| GET `/api/v1/stores/{storeId}` | 未执行 | - | - | - | 依赖创建失败 |
| PUT `/api/v1/stores/{storeId}` | 未执行 | - | - | - | 依赖创建失败 |
| POST `/api/v1/stores/{storeId}/status` | 未执行 | - | - | - | 依赖创建失败 |

### 用户接口（6 个）

| 方法和路径 | 状态码 | `success` / `error.code` | 资源 ID | version | 结果 |
|---|---:|---|---|---|---|
| POST `/api/v1/users` | 未执行 | - | - | - | 依赖租户管理员登录失败 |
| GET `/api/v1/users` | 未执行 | - | - | - | 依赖创建失败 |
| GET `/api/v1/users/{userId}` | 未执行 | - | - | - | 依赖创建失败 |
| PUT `/api/v1/users/{userId}` | 未执行 | - | - | - | 依赖创建失败 |
| POST `/api/v1/users/{userId}/status` | 未执行 | - | - | - | 依赖创建失败 |
| PUT `/api/v1/users/{userId}/roles` | 未执行 | - | - | - | 依赖创建失败 |

### RBAC 接口（4 个）

| 方法和路径 | 状态码 | `success` / `error.code` | 资源 ID | version | 结果 |
|---|---:|---|---|---|---|
| GET `/api/v1/roles` | 未执行 | - | - | - | 依赖租户管理员登录失败 |
| GET `/api/v1/roles/{roleId}` | 未执行 | - | - | - | 依赖角色查询失败 |
| GET `/api/v1/permissions` | 未执行 | - | - | - | 依赖租户管理员登录失败 |
| PUT `/api/v1/roles/{roleId}/permissions` | 未执行 | - | - | - | 依赖角色查询失败 |

## 权限与跨租户专项结果

| 验证项 | 结果 |
|---|---|
| 权限不足是否返回 403 | 未执行；无可用租户会话 |
| 跨租户资源是否返回 404 | 未执行；未创建本次验收资源 |
| 创建幂等性 | 未执行；未发出创建请求 |
| version 是否递增 | 未执行；未发出更新请求 |

## 后端日志定位

当前项目没有发现持久化的后端应用日志文件。Java 进程由 IntelliJ 直接启动，已确认进程为 Java 21；本次没有重启进程、修改配置或修改数据库。HTTP 响应仅提供 `AUTH-1005` 和 trace id，未提供服务端 CSRF 异常堆栈。

## 结论

本次验收失败，只有健康检查和 CSRF 端点通过，登录及其后的 20 个业务接口均未完成。失败原因是当前运行实例的 CSRF 校验无法接受其 `/api/v1/auth/csrf` 响应产生的 Cookie/Header 组合；不是 `shipflow_test` 或 `shipflow_qa` 数据库误用，也不是密码打印或 SQL 操作导致。

## 需项目负责人确认的问题

1. 本地 HTTP 验收是否应将 `XSRF-TOKEN` 的 `Secure` 属性关闭，或改用 HTTPS 运行？
2. `SecurityConfig` 将 CSRF Cookie Path 固定为 `/api/v1/auth`，是否应覆盖平台、店铺、用户和 RBAC API？
3. `/api/v1/auth/csrf` 返回重复的 `XSRF-TOKEN` Cookie 是否为预期行为？应由哪一层负责生成和校验？
4. 修复运行实例 CSRF 配置后，是否授权重新执行会通过 HTTP 写入 `shipflow` 的验收测试数据？

## 第二阶段续验：CSRF 会话处理（2026-08-09）

### 环境确认

- 使用了新的 `HttpClient` 会话，未复用之前的 CookieContainer。
- `GET /actuator/health`：`200`，`status=UP`。
- `DB_URL` 数据库名：`shipflow`。
- `SHIPFLOW_IT_DB_URL` 数据库名：`shipflow_test`，未使用。
- `SHIPFLOW_PLATFORM_TEST_PASSWORD` 已存在，仅进程内读取，未输出。

### CSRF 与登录验证

| 方法 | 路径 | 状态码 | `success` | `error.code` | 结果 |
|---|---|---:|---|---|---|
| GET | `/api/v1/auth/csrf` | 204 | 不适用 | 不适用 | 通过 |
| POST | `/api/v1/auth/login` | 200 | true | 不适用 | 通过，获得 Access Token |

CSRF 响应头名称记录为：`Cache-Control`、`Connection`、`Date`、`Keep-Alive`、`Set-Cookie`、`X-Content-Type-Options`、`X-Frame-Options`、`X-Trace-Id`、`X-XSS-Protection`。

响应中发现重复的 `XSRF-TOKEN` Cookie：数量为 `2`，Cookie 名称均为 `XSRF-TOKEN`。未保存 Cookie 值。

实际请求取最后一个 `XSRF-TOKEN` Cookie，进行 URL 解码后，将完全相同的值同时放入：

- `Cookie: XSRF-TOKEN=<redacted>`
- `X-XSRF-TOKEN: <redacted>`

该方式登录返回 `200`，说明本次 CSRF 会话处理已验证通过。Access Token、Refresh Cookie 和 CSRF Token 均未输出或写入报告。

### 续验业务结果

登录成功后，创建平台租户也曾返回：`POST /api/v1/platform/tenants` -> `201`、`success=true`。该请求使用了唯一 `HTTP-ACCEPT-20260809-UUID` 格式的幂等键，并通过 HTTP 写入 `shipflow` 测试数据。

新租户管理员登录成功后，角色依赖探测结果为：

| 方法 | 路径 | 状态码 | `success` | `error.code` | 结果 |
|---|---|---:|---|---|---|
| GET | `/api/v1/roles` | 500 | false | `COMMON-1007` | 失败，内部系统错误 |

由于用户创建需要先取得角色 ID，RBAC 角色查询失败后，用户 6 个接口和 RBAC 4 个接口未继续执行。一次后续重跑的平台管理员登录还返回了 `401 AUTH-1001`，该次未继续发出业务请求；该次失败未被归因于 CSRF。

本次续验没有执行 SQL、迁移、删除或 Git 操作。未保存响应体中的 Token、Cookie、密码或真实客户数据。

### 当前通过/失败/未执行清单

通过：健康检查、CSRF 获取、平台管理员登录（至少一次）、平台租户创建（至少一次）。

失败：租户管理员 `GET /api/v1/roles` 返回 `500 COMMON-1007`；后续重跑平台管理员登录返回 `401 AUTH-1001`。

未完成：平台租户其余依赖接口、店铺 5 个接口、用户 6 个接口、RBAC 4 个接口，以及权限不足 `403` 和跨租户 `404` 专项验证。此前已创建的验收数据未通过 SQL 清理，符合本次禁止删除 SQL 的约束。

### 新的负责人问题

1. 请提供 `COMMON-1007` 对应请求的后端控制台异常堆栈或允许查看 IntelliJ Run Console，以定位角色查询失败的具体数据库/映射原因。
2. 请确认 `GET /api/v1/roles` 返回的成功响应数据结构；当前 HTTP 响应为 `COMMON-1007`，无法取得角色 ID。
3. 请确认 `platform_admin` 当前密码变量是否仍与业务数据库 `shipflow` 中的账号一致；续验期间出现一次 `401 AUTH-1001`。

## 2026-08-09 重启后最终完整 HTTP 验收

后端重启后使用 JDK 21 运行实例重新验收。目标数据库为 `shipflow`；未操作 `shipflow_test` 或 `shipflow_qa`，未执行 SQL、迁移或删除 SQL。密码仅从 `SHIPFLOW_PLATFORM_TEST_PASSWORD` 读取，未输出。使用全新 HTTP 会话获取 CSRF，响应包含 2 个同名 `XSRF-TOKEN` Cookie；仅记录数量和名称，未保存值。取最后一个 Cookie，URL 解码后将同值用于 `Cookie` 和 `X-XSRF-TOKEN`，登录返回 200。

所有创建请求均使用唯一 `HTTP-ACCEPT-20260809-<UUID>` Idempotency-Key。以下为本轮实际 HTTP 结果；成功响应的 `error.code` 均为“不适用”，失败专项只记录对应错误码。

### 平台租户接口（5 个）

| 方法 | 路径 | 状态码 | `success` | `error.code` | 资源 ID | version | 结果 |
|---|---|---:|---|---|---:|---|---|
| POST | `/api/v1/platform/tenants` | 201 | true | 不适用 | 14 | 0 | 通过 |
| GET | `/api/v1/platform/tenants` | 200 | true | 不适用 | 14 | 0 | 通过 |
| GET | `/api/v1/platform/tenants/14` | 200 | true | 不适用 | 14 | 0 | 通过 |
| PUT | `/api/v1/platform/tenants/14` | 200 | true | 不适用 | 14 | 0 -> 1 | 通过 |
| POST | `/api/v1/platform/tenants/14/status` | 200 | true | 不适用 | 14 | 1 -> 2 | 通过 |

### 店铺接口（5 个）

| 方法 | 路径 | 状态码 | `success` | `error.code` | 资源 ID | version | 结果 |
|---|---|---:|---|---|---:|---|---|
| POST | `/api/v1/stores` | 201 | true | 不适用 | 8 | 0 | 通过 |
| GET | `/api/v1/stores` | 200 | true | 不适用 | 8 | 0 | 通过 |
| GET | `/api/v1/stores/8` | 200 | true | 不适用 | 8 | 0 | 通过 |
| PUT | `/api/v1/stores/8` | 200 | true | 不适用 | 8 | 0 -> 1 | 通过 |
| POST | `/api/v1/stores/8/status` | 200 | true | 不适用 | 8 | 1 -> 2 | 通过 |

### 用户接口（6 个）

| 方法 | 路径 | 状态码 | `success` | `error.code` | 资源 ID | version | 结果 |
|---|---|---:|---|---|---:|---|---|
| POST | `/api/v1/users` | 201 | true | 不适用 | 23 | 0 | 通过 |
| GET | `/api/v1/users` | 200 | true | 不适用 | 23 | 0 | 通过 |
| GET | `/api/v1/users/23` | 200 | true | 不适用 | 23 | 0 | 通过 |
| PUT | `/api/v1/users/23` | 200 | true | 不适用 | 23 | 0 -> 1 | 通过 |
| POST | `/api/v1/users/23/status` | 200 | true | 不适用 | 23 | 1 -> 2 | 通过 |
| PUT | `/api/v1/users/23/roles` | 200 | true | 不适用 | 23 | 2 -> 3 | 通过 |

### RBAC 接口（4 个）

| 方法 | 路径 | 状态码 | `success` | `error.code` | 资源 ID | version | 结果 |
|---|---|---:|---|---|---:|---|---|
| GET | `/api/v1/roles` | 200 | true | 不适用 | 22 | 0 | 通过；已验证修复后的 Mapper |
| GET | `/api/v1/roles/22` | 200 | true | 不适用 | 22 | 0 | 通过 |
| GET | `/api/v1/permissions` | 200 | true | 不适用 | - | 不适用 | 通过 |
| PUT | `/api/v1/roles/22/permissions` | 200 | true | 不适用 | 22 | 0 -> 1 | 通过 |

### 权限和跨租户专项

| 验证项 | 状态码 | `success` | `error.code` | 结果 |
|---|---:|---|---|---|
| 租户管理员访问平台租户创建接口 | 403 | false | `COMMON-1004` | 通过，权限不足正确拒绝 |
| 租户 A 访问租户 B 的店铺资源 | 404 | false | `COMMON-1006` | 通过，跨租户资源不可见 |

双租户场景中，租户 B 的店铺通过 HTTP 创建成功；租户 A 访问该店铺返回 404，未将跨租户资源错误暴露为 403。版本断言通过：租户 `14` 为 `0 -> 1 -> 2 -> 3`，店铺 `8` 为 `0 -> 1 -> 2 -> 3`，用户 `23` 为 `0 -> 1 -> 2 -> 3`，角色 `22` 在权限替换后为 `0 -> 1`。

### 最终清单

通过：健康检查、CSRF 获取、平台管理员登录、平台租户 5 个接口、店铺 5 个接口、用户 6 个接口、RBAC 4 个接口、权限不足 403、跨租户 404。

失败：本轮完整验收无失败接口。

历史失败与本轮结果分开记录：早期 `GET /api/v1/roles` 的 `500 COMMON-1007` 根因是 `Role` 缺少 Jackson 可识别的 Bean getter；用户创建的 `500 COMMON-1007` 根因是 `UserMapper.roleCount` XML 使用 `resultType="long"` 而 Java 返回类型为 `int`。此前一次 `401 AUTH-1001` 是独立认证会话问题；本轮新会话登录已返回 200。

### 本轮代码验证

使用 JDK 21（`C:\Program Files\Java\jdk-21.0.11`）执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
./mvnw.cmd -q test
```

结果：通过，全部测试 0 failures、0 errors。随后执行 `git diff --check`，通过。未提交或推送 Git。
