# 第二模块真实 HTTP 自动化测试执行前置条件

## 1. 目的与当前边界

本文冻结“租户、店铺、用户与 RBAC”第二模块真实 HTTP 自动化测试的执行门槛。当前仅完成测试框架与静态检查；本文不构成真实 HTTP、SQL 或数据库验证的证据。

本轮及未获得环境负责人书面确认前，禁止：

- 连接或写入 `shipflow`、`shipflow_test`、`shipflow_qa`；
- 执行 `database/qa/006_seed_tenant_store_user_rbac_api_test_cases.sql`；
- 直连数据库清理、猜测 `DELETE` 端点、修改共享账号或共享业务数据；
- 将 Access Token、Cookie、密码、私钥、HMAC 密钥或完整连接串写入代码、文档、日志、报告或 Git。

当前静态事实：模块二客户端覆盖 20 个接口；公开契约没有资源删除接口，也没有测试环境 reset API。`tests/module2/conftest.py` 的 teardown 仅清理内存登记，不会自动删除远端资源；`Module2Runtime.cleanup()` 也只允许显式注册的恢复动作，绝不推断删除请求。

## 2. 必须配置的运行时变量

所有值必须由受控 CI Secret、PowerShell 进程环境或等效安全注入方式提供；文档只记录变量名，不记录值。

| 类别 | 必须变量 | 用途与约束 |
|---|---|---|
| 执行开关 | `SHIPFLOW_MODULE2_RUN` | 必须为 `1`，才允许真实模块二 HTTP fixture 启动。 |
| 环境隔离 | `SHIPFLOW_MODULE2_ISOLATED_ENV` | 必须为 `1`，声明目标为可回收的专用隔离环境。 |
| 后端地址 | `SHIPFLOW_MODULE2_BASE_URL` | 隔离后端的绝对 HTTP(S) 地址；不得指向受保护的 `shipflow`、`shipflow_test`、`shipflow_qa` 环境。 |
| 平台身份 | `SHIPFLOW_MODULE2_PLATFORM_ACCESS_TOKEN` | 平台管理员 Access Token，仅运行时使用。 |
| Tenant A 身份 | `SHIPFLOW_MODULE2_TENANT_A_ACCESS_TOKEN` | Tenant A 管理员 Access Token，仅运行时使用。 |
| Tenant B 身份 | `SHIPFLOW_MODULE2_TENANT_B_ACCESS_TOKEN` | Tenant B 管理员 Access Token，仅运行时使用。 |
| 无权限身份 | `SHIPFLOW_MODULE2_NO_PERMISSION_ACCESS_TOKEN` | 已认证但缺少目标操作权限的 Access Token，用于断言 `403 COMMON-1004`。 |

运行时还必须在内存中维护下列资源上下文；这些可以由 setup 响应动态取得，不能硬编码为共享环境 ID：

| 资源 | 必需运行时值 |
|---|---|
| Tenant A/B | `tenantId`、`version`、唯一 `tenantCode`、创建请求 `Idempotency-Key`、初始管理员用户名。 |
| Store A/B | `storeId`、`version`、唯一 `storeCode`、平台账号/编码占位值来源、创建请求 `Idempotency-Key`。 |
| User A/B | `userId`、`version`、唯一用户名、角色 ID 集合、创建/更新请求 `Idempotency-Key`、临时密码变量名（仅运行时注入）。 |
| Role A/B | `roleId`、`version`、角色作用域、所属 `tenantId`、绑定前权限 ID 快照。 |
| Permission | `permissionId`、权限码、作用域适用性，以及角色权限替换前后的 ID 集合。 |

所有测试编码、用户名、店铺编码、租户编码和幂等键必须带本次运行 UUID 前缀；每次写操作后记录返回的 ID 和 `version`。测试报告只能记录脱敏标识，不得记录 Token、密码或 Cookie。

## 3. 四类测试身份与最小权限

| 身份 | 所属范围 | 必须具备的权限/Authority | 主要用途 |
|---|---|---|---|
| 平台管理员 | `scope:PLATFORM` | `tenant:create`、`tenant:read`、`tenant:manage` | 创建、查询、更新和停用测试 Tenant A/B；验证平台路径授权。 |
| Tenant A 管理员 | `scope:TENANT`，且绑定 Tenant A | `store:create/read/manage`、`user:read/manage`、`role:read/manage`、`permission:read` | Tenant A 的店铺、用户、角色与权限关系场景。 |
| Tenant B 管理员 | `scope:TENANT`，且 `tenantId` 必须与 Tenant A 不同 | 与 Tenant A 管理员相同的最小权限集合 | 资源隔离、跨租户不可见和跨租户写入拒绝场景。 |
| 无权限账号 | 已认证；平台或租户归属由具体场景指定 | 明确缺少目标操作所需权限 | 验证已认证但无权限统一返回 `403 COMMON-1004`。 |

不得用 Tenant A Token 冒充 Tenant B 或无权限身份；不得只依赖前端隐藏菜单替代后端权限断言。每个 Token 的 `scope`、`tenantId` 归属、角色状态和权限集合必须由环境负责人提供可审计的非敏感说明。

## 4. 动态资源创建与执行规则

真实 HTTP 测试必须在隔离环境按以下顺序创建并登记资源：

1. 平台管理员使用 UUID 租户编码和唯一幂等键创建 Tenant A、Tenant B；每次响应记录租户 ID 和 `version`。
2. Tenant A、Tenant B 分别创建其独立店铺、普通用户和可操作的租户角色；所有编码与用户名使用本次运行 UUID 前缀。
3. 用户创建与用户角色绑定、角色权限替换只在各自所属租户内进行；角色权限关系只能绑定契约允许的权限 ID。
4. 所有更新、状态切换、用户角色替换与角色权限替换读取最新 `version`；并发场景使用受控同步工具和有界轮询，不得固定长时间 `sleep`。
5. 每个创建/资料修改请求使用独立 `Idempotency-Key`；重放和同键异体请求必须在同一资源上下文内验证。

创建后的资源仅可由已批准的 reset/恢复能力回收。不得以停用替代删除后仍将共享环境视为干净；不得对基础租户、基础用户、基础角色、权限字典或非本次 UUID 前缀资源进行操作。

## 5. 清理与环境隔离门槛

清理方案按以下优先级选择：

1. **测试环境专用 reset API**：最优方案。API 必须仅处理本次运行 UUID 资源，且能够恢复或删除租户、店铺、用户、角色、绑定关系、幂等记录和审计副作用，并输出可审计的清理结果。
2. **独立、可整体重置的测试数据库/环境**：没有 reset API 时的最低可接受方案。环境所有者负责在每次运行后整体重置；该环境不得共享业务开发库、Java 集成测试库或 Python QA 用例库。
3. **经批准的专用 fixture/清理服务**：仅当其范围可证明限定为本次 UUID 资源，且不通过直接 SQL 或猜测 DELETE 路径操作共享库时可使用。

当前没有删除接口或 reset API，因此不能声称 HTTP 测试会自动清理远端资源，也不能对共享业务库执行真实写入测试。

## 6. 当前必须跳过的场景

以下矩阵是执行门槛，不得以伪造成功、复用共享 ID 或省略清理来绕过。

| 缺失前置能力 | 必须跳过的场景 | 原因 |
|---|---|---|
| 没有 reset API、专用 fixture 或可整体重置的隔离环境 | 动态资源创建后的资源清理断言；所有会留下 tenant/store/user/role 资源的真实写场景 | 当前无删除端点，无法保证无残留。 |
| 没有 Tenant B 独立身份和资源 | 跨租户读取/更新/状态/角色权限绑定隔离场景 | 无法证明 A/B 的 `tenantId` 不同，也无法断言 `404 COMMON-1006`。 |
| 没有可恢复的用户、租户和角色状态 fixture 或 reset | 停用后的实时拒绝场景 | 无法安全恢复停用状态，也无法重复执行。 |
| 没有隔离并发协调、资源 reset 和可重复 version fixture | `version` 乐观锁冲突、并发创建与并发幂等场景 | 需要两个受控请求、确定初始版本和运行后恢复。 |
| 没有隔离数据库的只读断言账号及批准查询范围 | 所有数据库断言 | HTTP 响应不能替代 tenant_id、关联表、审计、幂等和事务落库断言。 |
| 没有无权限账号 | 已认证但无权限的 `403 COMMON-1004` 场景 | 不能以匿名 `401` 替代授权不足。 |
| 没有平台管理员权限集合证明 | 平台租户管理正向与授权边界场景 | 无法证明 `scope:PLATFORM` 与 `tenant:*` 权限同时生效。 |

在上述任何前置条件缺失时，只能运行 fake client、框架单元测试和静态检查；不得运行 `database/qa/006_seed_tenant_store_user_rbac_api_test_cases.sql` 或真实 HTTP 模块二场景。

## 7. 负责人交付清单

负责人必须提供以下非敏感、可验证信息后，才可批准真实 HTTP 运行：

- 隔离后端地址，以及该地址所连数据库/环境不属于 `shipflow`、`shipflow_test`、`shipflow_qa` 的隔离证明；
- 四类身份的安全注入方式、`scope`、tenant 归属、角色状态和最小权限集合；
- Tenant A/B、无权限账号以及可恢复状态 fixture 的所有权与生命周期；
- 动态租户、店铺、用户、角色和权限关系的批准创建范围，以及 UUID 命名规范；
- reset API、整体环境重置或专用 fixture 的责任人、调用窗口、失败升级路径和清理验收标准；
- 可用于数据库断言的隔离只读账号、允许表/字段范围和数据保留责任人；
- 幂等与 `version` 并发的协调方案、轮询上限，以及各场景 HTTP 状态码/错误码矩阵；
- 测试失败后保留的脱敏资源标识、审计追踪与人工清理责任人。

## 8. 执行批准清单

开始真实 HTTP 自动化前，执行负责人必须逐项确认：

- `SHIPFLOW_MODULE2_RUN=1` 与 `SHIPFLOW_MODULE2_ISOLATED_ENV=1` 已在受控进程设置；
- `SHIPFLOW_MODULE2_BASE_URL` 已验证为隔离环境；
- 四个 Access Token 均已安全注入并完成最小权限核验；
- 资源创建、状态恢复、清理与失败升级责任已经落实；
- 所需动态 ID、`version`、编码、幂等键和权限 ID 可从本次执行 setup 获取；
- 不会连接、写入或清理 `shipflow`、`shipflow_test`、`shipflow_qa`；
- 未获得上述确认时，真实 HTTP 场景保持 skip，并在报告中注明缺失前置条件。
