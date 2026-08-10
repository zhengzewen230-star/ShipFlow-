# 第二模块接口自动化测试用例设计

- 阶段：测试用例设计与生成准备，仅设计，不执行
- 范围：平台租户、店铺、用户、角色与权限
- 接口基线：20 个（租户 5、店铺 5、用户 6、RBAC 4）
- 预计用例总数：68 个
- 数据库边界：不连接数据库，不写入 `shipflow_qa`，不执行 SQL，不修改业务库
- 敏感信息边界：密码只引用环境变量名；不保存密码、Token、Cookie、HMAC、私钥或连接字符串

## 1. 设计依据和通用约束

依据 `docs/09-api-inventory.md`、`docs/10-error-codes.md`、`docs/14-auth-rbac-design.md`、OpenAPI、第一阶段生命周期文档、第二阶段 HTTP 验收报告和 `api-tests/` 当前执行器设计。

每个请求都应记录 HTTP 状态、`success`、`error.code`、`X-Trace-Id` 是否存在及必要的资源 ID/version。所有写请求需要新的 CSRF 会话；创建请求使用 `HTTP-ACCEPT-<date>-<UUID>` 格式的运行时幂等键。租户资源的归属只能来自 Access Token 的实时身份，不能由 body、query 或普通 Header 覆盖。

错误码基线：未认证为 401 `COMMON-1002` 或过期 Token 的 `COMMON-1003`；权限不足为 403 `COMMON-1004`；乐观锁冲突为 409 `COMMON-1005`；跨租户、已删除或不可见资源为 404 `COMMON-1006`；幂等键请求体冲突为 409 `COMMON-1009`；相同请求处理中为 409 `COMMON-1010`。模块错误码按接口表记录，实际实现若与 OpenAPI 不一致，应标记为契约偏差，不修改预期来迎合响应。

通用 teardown：优先通过业务 API 将动态资源置为 `DISABLED`，撤销角色/用户关系并退出会话；当前接口没有删除 API，因此不执行删除 SQL。失败中途产生的资源必须登记到运行时清单，供人工按业务 API 复核。固定共享账号、平台权限字典和系统角色不得由测试自动停用或修改。

## 2. 参与者、数据和执行分层

| 标识 | 参与者/数据 | 用途 |
|---|---|---|
| `P_ADMIN` | 平台管理员，密码来自 `SHIPFLOW_PLATFORM_TEST_PASSWORD` | 租户创建、平台租户查询/更新/停用 |
| `T_ADMIN_A` | 运行时创建租户 A 的初始管理员，密码来自安全环境变量或运行时安全注入 | 店铺、用户、RBAC 正向及停用实时拒绝 |
| `T_ADMIN_B` | 运行时创建租户 B 的初始管理员 | 跨租户隔离对照 |
| `T_OPERATOR_A` | 租户 A 中具备读取权限的用户 | `store:read` 正向、管理权限不足 |
| `T_USER_A` | 租户 A 普通用户 | 用户状态和角色替换影响验证 |
| `ROLE_A` | 租户 A 的 TENANT 角色 | 角色查询和权限替换 |
| `RESOURCE_A/B` | 分别属于租户 A/B 的 tenant、store、user、role | 404 跨租户断言 |

租户 A/B、店铺、用户、角色 ID、初始管理员账号、幂等键和请求名均必须运行时 UUID 化。不得把上一轮验收的真实 ID 当作新一轮固定前置数据。

执行分组：

1. `SETUP-A`：新 CSRF 会话、平台登录、创建租户 A/B，提取资源 ID、版本和管理员登录材料。
2. `TENANT`：平台租户 5 接口及平台权限边界。
3. `STORE`：店铺 5 接口及跨租户/停用状态。
4. `RBAC-READ`：权限字典、角色查询、角色权限替换。
5. `USER`：用户 6 接口、用户角色替换、用户停用。
6. `INVALIDATION`：停用角色/租户并使用同一尚未过期 Access Token 验证实时拒绝。
7. `TEARDOWN`：API 状态恢复和 logout；清理结果只记录，不执行 SQL。

## 3. 平台租户管理用例（17 个）

| 编号 | 接口/前置条件/请求 | 测试数据和执行顺序 | 预期 | 提取变量 | teardown/API 驱动 | DB 驱动 | 人工确认 |
|---|---|---|---|---|---|---|---|
| TENANT-001 | POST `/api/v1/platform/tenants`；`P_ADMIN` | 合法租户 A、初始管理员、唯一幂等键；SETUP-A 首个写请求 | 201，`success=true` | `TENANT_A_ID`、`TENANT_A_VERSION`、管理员标识 | 是，记录资源；无删除 API | 是 | 否 |
| TENANT-002 | GET `/api/v1/platform/tenants`；已登录 | page/pageSize、tenantCode 精确筛选 | 200，列表含 A | `TENANT_A_ID`、version | 否 | 是 | 否 |
| TENANT-003 | GET `/api/v1/platform/tenants/{id}`；已登录 | A 的 ID | 200，字段和状态正确 | version | 否 | 是 | 否 |
| TENANT-004 | PUT `/api/v1/platform/tenants/{id}`；平台管理权限 | 新名称、当前 version、唯一幂等键 | 200，version +1 | 新 version | 恢复名称或停用 | 是 | 否 |
| TENANT-005 | POST `/api/v1/platform/tenants/{id}/status`；平台管理权限 | 当前 version，DISABLED | 200，状态 DISABLED、version +1 | version/status | 可通过 API 恢复或保留并登记 | 是 | 否 |
| TENANT-006 | POST 创建；无 Access Token | 合法 body、幂等键 | 401 `COMMON-1002` | trace only | 无资源 | 是 | 否 |
| TENANT-007 | GET 列表；无/过期 Token | 固定无效或过期 Token | 401 `COMMON-1002/1003` | trace only | 无 | 是 | 否 |
| TENANT-008 | POST 创建；租户管理员 | 使用 T_ADMIN_A | 403 `COMMON-1004` | trace only | 无 | 是 | 否 |
| TENANT-009 | GET 列表；租户管理员 | 使用 T_ADMIN_A | 403 `COMMON-1004` | trace only | 无 | 是 | 否 |
| TENANT-010 | GET 详情；租户管理员 | 指向 A 或随机平台租户 ID | 403 `COMMON-1004` | trace only | 无 | 是 | 否 |
| TENANT-011 | POST 创建；非法 body | 缺 tenantCode、超长字段、非法初始管理员字段 | 400，`COMMON-1008` 或契约校验错误 | trace only | 无 | 是 | 否 |
| TENANT-012 | POST 创建；重复租户编码 | 新幂等键、已存在 tenantCode | 409 `TENANT-1001` | trace only | 无 | 是 | 否 |
| TENANT-013 | POST 创建；同幂等键同 body | 第一次 201 后原样重放 | 201 或契约定义的原响应，不能新增资源 | 原 ID/version | 不重复清理 | 是 | 需确认重放状态码 |
| TENANT-014 | POST 创建；同幂等键不同 body | 相同 key 修改名称或管理员 | 409 `COMMON-1009` | trace only | 不应新增资源 | 是 | 否 |
| TENANT-015 | PUT 更新；旧 version | 先更新一次，再用旧 version | 409 `COMMON-1005` | 当前 version | 否 | 是 | 否 |
| TENANT-016 | POST 状态；停用租户后再改资料 | 使用已停用租户的当前 version | 422 `TENANT-1002/1003` | trace only | API 恢复或登记 | 是 | 需确认二者适用边界 |
| TENANT-017 | 并发创建/相同 key处理中 | 两个并发相同请求，动态 key | 一个成功，其余 `COMMON-1010` 或原结果 | ID、错误码 | API 状态清理 | 否，需并发执行器 | 是 |

## 4. 店铺管理用例（17 个）

| 编号 | 接口/前置条件/请求 | 测试数据和执行顺序 | 预期 | 提取变量 | teardown/API 驱动 | DB 驱动 | 人工确认 |
|---|---|---|---|---|---|---|---|
| STORE-001 | POST `/api/v1/stores`；T_ADMIN_A | 合法店铺 A、唯一 key、平台账号占位符 | 201 | `STORE_A_ID`、version | 停用 | 是 | 否 |
| STORE-002 | GET `/api/v1/stores`；T_ADMIN_A | status/platformCode 筛选 | 200，含 A | ID/version | 否 | 是 | 否 |
| STORE-003 | GET `/api/v1/stores/{id}`；T_ADMIN_A | A ID | 200 | version | 否 | 是 | 否 |
| STORE-004 | PUT `/api/v1/stores/{id}`；T_ADMIN_A | 新名称、当前 version、key | 200，version +1 | version | 停用 | 是 | 否 |
| STORE-005 | POST `/api/v1/stores/{id}/status`；T_ADMIN_A | DISABLED、当前 version | 200，version +1 | status/version | API 恢复或登记 | 是 | 否 |
| STORE-006 | POST 创建；无 Token | 合法 body | 401 `COMMON-1002` | trace | 无 | 是 | 否 |
| STORE-007 | GET 列表；无权限用户 | T_OPERATOR_A 无 `store:read` | 403 `COMMON-1004` | trace | 无 | 是 | 否 |
| STORE-008 | GET 详情；跨租户 | T_ADMIN_A 请求 STORE_B_ID | 404 `COMMON-1006` | trace | 无 | 是 | 否 |
| STORE-009 | PUT/状态；跨租户 | T_ADMIN_A 请求 STORE_B_ID | 404 `COMMON-1006` | trace | 无 | 是 | 否 |
| STORE-010 | POST 创建；非法 body | 缺 storeCode、platformCode、账号或超长字段 | 400 `COMMON-1008` | trace | 无 | 是 | 否 |
| STORE-011 | POST 创建；重复编码/平台账号 | 当前租户重复数据 | 409 `STORE-1001` | trace | 无 | 是 | 否 |
| STORE-012 | POST 创建；同 key同 body | 原请求重放 | 原响应或 201，不新增 | ID/version | 不重复清理 | 是 | 需确认状态码 |
| STORE-013 | POST 创建；同 key不同 body | 修改店铺名 | 409 `COMMON-1009` | trace | 无 | 是 | 否 |
| STORE-014 | PUT 更新；旧 version | 先更新后使用旧 version | 409 `COMMON-1005` | current version | 否 | 是 | 否 |
| STORE-015 | PUT 更新；店铺已停用 | 当前 version | 422 `STORE-1002` | trace | API 恢复或登记 | 是 | 否 |
| STORE-016 | GET 详情；已停用店铺 | T_ADMIN_A 查询本租户已停用店铺 | 200 或业务约定拒绝；不得猜测 | trace/ID | 否 | 是 | 是 |
| STORE-017 | POST 创建；外键/租户归属污染 | platformCode 不存在或伪造 tenantId body/header | 400/404；请求 tenantId 不得改变归属 | trace | 无 | 否，需运行时构造 | 是 |

## 5. 用户管理用例（20 个）

| 编号 | 接口/前置条件/请求 | 测试数据和执行顺序 | 预期 | 提取变量 | teardown/API 驱动 | DB 驱动 | 人工确认 |
|---|---|---|---|---|---|---|---|
| USER-001 | POST `/api/v1/users`；T_ADMIN_A | 合法用户 A、ROLE_A、唯一 key | 201 | `USER_A_ID`、version | 停用用户 | 是 | 否 |
| USER-002 | GET `/api/v1/users`；T_ADMIN_A | page/status/username | 200，含 A | ID/version | 否 | 是 | 否 |
| USER-003 | GET `/api/v1/users/{id}`；T_ADMIN_A | A ID | 200 | roleIds/version | 否 | 是 | 否 |
| USER-004 | PUT `/api/v1/users/{id}`；T_ADMIN_A | displayName、当前 version、key | 200，version +1 | version | 停用 | 是 | 否 |
| USER-005 | POST `/api/v1/users/{id}/status`；T_ADMIN_A | DISABLED、当前 version | 200，version +1 | status/version | API 恢复或登记 | 是 | 否 |
| USER-006 | PUT `/api/v1/users/{id}/roles`；T_ADMIN_A | ROLE_A 到另一有效角色，当前 version | 200，角色替换且 version +1 | roleIds/version | 恢复原角色 | 是 | 否 |
| USER-007 | POST 创建；无 Token | 合法 body | 401 `COMMON-1002` | trace | 无 | 是 | 否 |
| USER-008 | GET 列表；无 `user:manage` | T_OPERATOR_A | 403 `COMMON-1004` | trace | 无 | 是 | 否 |
| USER-009 | GET 详情；跨租户 | T_ADMIN_A 请求 USER_B_ID | 404 `COMMON-1006` | trace | 无 | 是 | 否 |
| USER-010 | PUT/状态；跨租户 | T_ADMIN_A 请求 USER_B_ID | 404 `COMMON-1006` | trace | 无 | 是 | 否 |
| USER-011 | POST 创建；非法 body | 缺 username/password/displayName、空 roleIds、超长字段 | 400 `COMMON-1008` | trace | 无 | 是 | 否 |
| USER-012 | POST 创建；用户名重复 | 当前租户已有 username | 409 `USER-1001` | trace | 无 | 是 | 否 |
| USER-013 | POST 创建；同 key同 body | 原请求重放 | 原响应或 201，不新增 | ID/version | 不重复清理 | 是 | 需确认状态码 |
| USER-014 | POST 创建；同 key不同 body | 修改 username/displayName | 409 `COMMON-1009` | trace | 无 | 是 | 否 |
| USER-015 | PUT 更新；旧 version | 并发/顺序制造旧版本 | 409 `COMMON-1005` | current version | 否 | 是 | 否 |
| USER-016 | PUT 角色；不存在角色 ID | roleIds 含随机或其他租户角色 | 422 `ROLE-1002` 或 404 `COMMON-1006` | trace | 无 | 是 | 是 |
| USER-017 | PUT 角色；混入平台角色 | roleIds 含 PLATFORM scope role | 422 `ROLE-1002` 或 403 | trace | 无 | 是 | 是 |
| USER-018 | 已停用用户继续调用 | 停用 USER_A 后使用其未过期 Token | 401 `COMMON-1002` | trace | API 恢复或登记 | 否，需动态会话 | 否 |
| USER-019 | 已停用角色下用户继续调用 | 停用 ROLE_A 后使用用户 Token | 401 `COMMON-1002` 或 403 `COMMON-1004` | trace | API 恢复或登记 | 否，需动态状态 | 是 |
| USER-020 | 用户创建外键/租户归属校验 | roleIds 属于 B；body/header 伪造 tenantId | 404/422；不得跨租户绑定 | trace | 无 | 否，需动态创建 B | 是 |

## 6. RBAC 用例（14 个）

| 编号 | 接口/前置条件/请求 | 测试数据和执行顺序 | 预期 | 提取变量 | teardown/API 驱动 | DB 驱动 | 人工确认 |
|---|---|---|---|---|---|---|---|
| RBAC-001 | GET `/api/v1/roles`；T_ADMIN_A | 无 status 和 status=ACTIVE 两次查询 | 200，只有当前租户 TENANT 角色 | `ROLE_A_ID`、version | 否 | 是 | 否 |
| RBAC-002 | GET `/api/v1/roles/{id}`；T_ADMIN_A | ROLE_A ID | 200，权限列表正确 | permissionIds/version | 否 | 是 | 否 |
| RBAC-003 | GET `/api/v1/permissions`；T_ADMIN_A | 无请求体 | 200，公共权限字典 | permission IDs/codes | 否 | 是 | 否 |
| RBAC-004 | PUT `/api/v1/roles/{id}/permissions`；T_ADMIN_A | 替换为合法权限集、当前 version | 200，权限集合替换、version +1 | version/permissionIds | 恢复原权限 | 是 | 否 |
| RBAC-005 | GET 角色；无 Token | 无认证 | 401 `COMMON-1002` | trace | 无 | 是 | 否 |
| RBAC-006 | GET 角色；缺 `role:read` | T_OPERATOR_A | 403 `COMMON-1004` | trace | 无 | 是 | 否 |
| RBAC-007 | GET 角色详情；跨租户 | T_ADMIN_A 请求 ROLE_B_ID | 404 `COMMON-1006` | trace | 无 | 是 | 否 |
| RBAC-008 | PUT 权限；跨租户 | T_ADMIN_A 请求 ROLE_B_ID | 404 `COMMON-1006` | trace | 无 | 是 | 否 |
| RBAC-009 | PUT 权限；缺 `role:manage` | 只读角色用户 | 403 `COMMON-1004` | trace | 无 | 是 | 否 |
| RBAC-010 | PUT 权限；非法权限 ID | 随机 ID、其他租户伪造 ID | 422 `ROLE-1002` 或 404 `COMMON-1006` | trace | 无 | 是 | 是 |
| RBAC-011 | PUT 权限；旧 version | 先成功替换，再用旧 version | 409 `COMMON-1005` | current version | 恢复原权限 | 是 | 否 |
| RBAC-012 | GET 角色；角色已停用 | 停用 ROLE_A 后查询 | 404/401/403，按实现与契约确认 | trace | API 恢复或登记 | 是 | 是 |
| RBAC-013 | PUT 权限；角色已停用 | 当前 version、合法权限 | 422 `ROLE-1002` | trace | API 恢复或登记 | 是 | 否 |
| RBAC-014 | 角色租户/外键校验 | 尝试绑定平台权限之外的不存在权限；不能改 tenant_id | 422/404；不得产生越权关系 | trace | 无 | 否，需动态数据 | 是 |

## 7. 跨模块实时拒绝和一致性场景（补充 0 个独立编号）

以上 68 个用例已覆盖停用、跨租户、外键、角色权限/用户角色替换和版本冲突。执行时必须把以下断言作为跨用例检查，而不是另造固定数据：

- 租户停用后，T_ADMIN_A 的新业务请求立即被拒绝；恢复后才允许继续。
- 用户停用后，旧但未过期 Token 不能继续访问；不能仅通过 Token 过期测试替代数据库状态重载。
- 角色停用或权限回收后，已认证用户的下一请求必须重新加载状态并被拒绝或降权。
- 同一事务内失败的创建/替换不能留下半成品关系；只能通过 API 后续查询验证，不用直接 SQL 断言。
- 当前租户的角色、用户、店铺和权限绑定不得出现另一租户 ID；固定 QA 数据驱动不能代替动态双租户场景。

## 8. 现有 api-tests 能力差距（只记录，不修改）

当前框架可复用：`AuthClient` 的 CSRF/登录/logout、`HttpClient` 通用 method、JSON/字符串 body、headers/cookies、占位符解析、JSON/header/cookie/body 提取器、统一状态和错误码断言、setup/extractor/teardown 生命周期。

第二模块开始前建议新增但本阶段不实现：

1. `TenantClient`、`StoreClient`、`UserClient`、`RbacClient`，或统一资源 Client 的 PUT/POST 状态/GET 便捷方法。
2. 运行时 UUID、幂等键、随机业务编码和安全临时账号生成器；密码仍只能来自受控环境变量/安全注入。
3. 资源创建动作和资源状态变更动作，支持提取 ID/version 并在 teardown 通过 API 恢复状态。
4. 并发执行器，用于同 key 并发、乐观锁竞争和同资源状态竞态；固定 `sleep` 不可作为同步方案。
5. 动态双租户 fixture、权限集合快照/恢复、角色/用户关系快照恢复；平台系统角色不得自动改写。
6. 请求体冲突、响应 envelope 字段类型、版本严格递增和资源归属断言的专用 assertion helper。
7. 对 HTTP 400/401/403/404/409/422 的响应统一脱敏日志；禁止输出请求密码、Token、Cookie 或连接信息。

当前 `ActionExecutor` 只支持认证和 Token fixture 动作，`RequestExecutor` 每次请求会清 Cookie，适合数据库驱动的独立认证场景，但不能独立完成本模块资源链路。建议先扩展框架并写框架单测，再生成本模块 QA 行。

## 9. DB 驱动适用性汇总

可数据库驱动：20 个接口的正常只读/写流程、固定参数错误、无认证、权限不足、跨租户（前提是 QA 数据契约提供 A/B 账号和资源变量）、幂等重放、请求体冲突、版本冲突、角色/用户绑定替换。

必须动态创建资源：租户创建及其后续全部链路、双租户跨租户资源、同 key 幂等、并发版本冲突、停用后实时拒绝、外键归属校验。原因是资源 ID、version、角色关系和状态必须来自当前运行，不可依赖固定 ID。

不能安全写入固定测试数据：会停用共享用户/租户/角色的用例；会修改平台管理员权限的用例；并发同 key/版本竞态；依赖当前时间或未确认状态码的停用后请求；需要真实密码或 Token 的数据。它们只能由运行时 API fixture 创建并在 API teardown 恢复，或由人工审批后执行。

## 10. 待业务确认问题

1. 同一幂等键、同一请求重放的成功状态码是否固定返回第一次响应（201/200），还是统一返回 200？
2. 更新接口的 `Idempotency-Key` 是否由后端真正实现；OpenAPI 要求而部分 Controller 旧实现可能未读取，验收以哪份契约为准？
3. 停用租户、用户、角色后，后续请求统一返回 401、403 还是模块 422；需冻结每条接口的状态码和错误码矩阵。
4. 已停用店铺/角色是否允许 GET 读取；若允许，是否仍返回资源摘要而只拒绝写操作？
5. 角色权限替换允许的权限集合边界是什么；是否允许 `audit:read`、平台权限或空集合？
6. 用户角色替换是否必须至少保留一个 ACTIVE TENANT 角色；空数组、停用角色和跨租户角色分别返回什么错误？
7. 店铺 `platformCode/platformAccount` 的外键/唯一约束和测试占位值由谁提供？
8. QA 数据库是否允许通过专用测试 fixture API 创建并恢复租户、账号、角色和权限，而不是直接写表？
9. teardown 只允许业务 API 时，停用后的资源是否保留为审计测试数据，还是由受控管理员流程清理？
10. 是否批准新增并发执行器以及运行时安全账号注入；若不批准，哪些并发用例降级为人工验收？

## 11. 生成阶段门槛

本节为历史设计边界。当前事实已变化：66 条冻结范围用例已经写入 `shipflow_qa.api_test_case` 并完成中文最终化；它们仍全部为 `BLOCKED`，未运行第二模块自动化测试。真实执行前仍须先完成框架能力、隔离后端、动态夹具、仅通过 API 的清理、业务库只读断言、有界轮询和并发合同验收。

## 最终修订说明

历史 68 条设计只作为范围追溯资料；正式范围为 66 条，USER-018、USER-019 排除。`database/qa/006_seed_tenant_store_user_rbac_api_test_cases.sql` 是评审草案，正式已导入文件为 `database/qa/007_seed_module2_frozen_20_api_test_cases.sql`，中文最终化文件为 `database/qa/008_upgrade_module1_module2_api_test_cases_cn_final.sql`。在动态夹具、资源客户端、API 清理、数据库断言、轮询与并发行为均已真实验证前，不得将任何用例改为 `READY`。
