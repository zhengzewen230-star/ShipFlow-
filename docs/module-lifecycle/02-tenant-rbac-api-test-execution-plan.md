# 第二模块真实接口自动化执行准备方案

状态：评审稿。`USER-018`、`USER-019` 继续排除；`shipflow_qa.api_test_case` 中的 66 条场景保持 `BLOCKED`。本方案只基于仓库代码、文档和已导入用例进行检查，未运行 pytest、未连接或修改数据库、未提交 Git。

## 1. 执行目标与边界

第二模块测试平台租户生命周期，以及 Tenant A/B 内店铺、用户、角色、权限与绑定关系。平台管理员创建租户；租户管理员在自身 `tenant_id` 范围内创建和管理资源；无权限身份只用于验证授权边界。测试必须同时断言 HTTP 合同与独立业务测试库中的落库状态，尤其是 `tenant_id`、角色作用域、幂等、版本和审计。

认证、CSRF、登录和身份切换只负责建立运行时会话，不计为第二模块冻结的 20 个业务接口。`GET /api/v1/users/me` 的 USER-018/019 仍归认证/当前用户范围，禁止被重新纳入本模块的 66 条正式自动化用例。

真实运行的唯一目标是受控的独立后端和独立业务测试库；严禁连接、写入、清理或复用 `shipflow`、`shipflow_test`、`shipflow_qa`。`shipflow_qa` 只保存用例元数据，不能充当业务 fixture 或断言数据库。

## 2. 四类测试身份与最小权限

| 身份 | 必要范围 | 最小权限 | 使用场景 |
|---|---|---|---|
| 平台管理员 `P_ADMIN` | `scope:PLATFORM`，无 tenant 归属 | `tenant:create`、`tenant:read`、`tenant:manage` | Tenant A/B 的创建、列表、详情、更新、状态及平台路径 401/403 边界。 |
| Tenant A 管理员 `T_ADMIN_A` | `scope:TENANT`，仅 Tenant A | `store:read/manage`、`user:read/manage`、`role:read/manage`、`permission:read` | Tenant A 的店铺、用户、用户角色、角色权限及版本/幂等场景。 |
| Tenant B 管理员 `T_ADMIN_B` | `scope:TENANT`，仅 Tenant B，且 `tenant_id != Tenant A` | 与 `T_ADMIN_A` 相同 | 建立 Tenant B 资源，并作为跨租户不可见、不可写断言的资源所有者。 |
| 无权限身份 `NO_PERMISSION` | 已认证；范围与待测路径一致 | 明确缺少被测操作所需权限，不能额外拥有替代性管理权限 | 已认证但无权限的 `403 COMMON-1004`；不能用匿名 `401` 替代。 |

所有凭据、Token、Cookie 和临时密码必须由受控运行时 Secret 注入。测试报告仅记录身份别名、脱敏运行 ID、HTTP 状态、错误码和 Trace ID；不记录认证材料。

## 3. 动态资源图、创建顺序与隔离命名

### 3.1 创建顺序

1. 生成一次 `RUN_ID`（UUID），创建请求级 `Idempotency-Key`，建立平台会话和 Tenant A/B/无权限会话。
2. `P_ADMIN` 创建 Tenant A，再创建 Tenant B；保存每个 tenant ID、tenant code、初始管理员身份、当前 `version` 和创建请求摘要。
3. 分别以 `T_ADMIN_A`、`T_ADMIN_B` 创建/选取各自隔离的 Role A/B；读取并保存角色当前权限 ID 集合、角色作用域、所属 tenant ID 和 `version`。
4. 各租户创建自己的 Store 和 User；User 仅绑定其本租户 Role。保存每个资源 ID、`tenant_id`、状态、版本、创建幂等键和恢复快照。
5. 执行正常、权限、跨租户、参数、幂等、版本、停用和并发场景。每次成功写入后立即从响应提取新 `version`；每次需要数据库断言的写入由同一运行 ID 关联。
6. 先恢复绑定关系和状态，再由批准的 reset 机制清理全部运行资源；清理成功后才允许报告该运行可重复。

### 3.2 命名规则

所有可写资源使用同一个不含秘密的前缀：`m2_<UTC-YYYYMMDDTHHMMSSZ>_<RUN8>_<kind>_<suffix>`。示例仅说明形状，不是固定数据：

| 对象 | 规则 |
|---|---|
| 租户编码/名称 | `m2_<run>_tenant_a`、`m2_<run>_tenant_b` |
| 店铺编码/名称 | `m2_<run>_store_a`、`m2_<run>_store_b` |
| 用户名/显示名 | `m2_<run>_user_a`、`m2_<run>_user_b` |
| 角色代码/名称 | `m2_<run>_role_a`、`m2_<run>_role_b` |
| 幂等键 | `HTTP-ACCEPT-<CASE_NO>-<UUID>` |

`RUN_ID` 必须保存到内存上下文和脱敏报告中。清理、数据库断言和失败升级只能匹配本次 `RUN_ID`；不得用模糊前缀、固定 ID 或共享样例数据扩大范围。

## 4. 独立后端与业务测试库

### 4.1 拓扑

后端需以专用 profile 连接独立业务测试库，例如逻辑名称 `shipflow_module2_it`。该库必须由环境负责人独占、可整体重置，并且与 `shipflow`、`shipflow_test`、`shipflow_qa` 使用不同的数据库名、数据卷和生命周期。测试进程只能通过 `SHIPFLOW_MODULE2_BASE_URL` 调用这个后端。

后端 profile 的数据源由安全环境变量注入，建议变量仅记录名称：`SHIPFLOW_MODULE2_IT_DB_URL`、`SHIPFLOW_MODULE2_IT_DB_USERNAME`、`SHIPFLOW_MODULE2_IT_DB_PASSWORD`。启动门禁需要在后端健康检查中暴露非敏感的环境标识或由环境负责人提供等效证明，确认不连接三个禁止数据库。不得把连接串或凭据写入 YAML、Python、SQL、报告或 Git。

### 4.2 两类数据库账号

| 账号 | 权限边界 | 用途 |
|---|---|---|
| 后端运行账号 | 仅独立业务测试库，满足应用运行所需最小权限 | 服务通过 API 创建和恢复资源。 |
| 数据库断言只读账号 | 仅独立业务测试库，限批准的 `SELECT` 表/列 | 每个核心场景的数据库断言；不得具备 DDL、写入或跨库权限。 |

QA 用例库账号不得用于业务库断言；业务断言账号不得写入 `shipflow_qa.api_test_case`。

## 5. 数据库断言范围

| 场景类别 | 必须断言 | 允许的只读范围 |
|---|---|---|
| Tenant 创建/更新/状态 | 租户唯一编码、状态、`version`、初始管理员 tenant 归属、审计记录 | tenant、user、audit 的最小字段集。 |
| Store 创建/读取/更新/状态 | `tenant_id` 等于调用方 Tenant A、编码唯一性、状态、`version`、软删除过滤 | store、audit；跨租户场景只查询本次 A/B ID。 |
| User 与用户角色 | 用户 `tenant_id`、用户名作用域唯一性、状态、`version`、user-role 仅指向同租户角色 | user、user-role、role、audit。 |
| Role 与权限替换 | role scope/tenant 归属、role-permission 精确集合、替换前后快照、版本、审计 | role、permission、role-permission、audit。 |
| 幂等/并发 | 同一 `(tenant_id, idempotency_key)` 仅一条业务效果或幂等记录；同键异体冲突无第二资源；审计不重复 | 对应资源、幂等记录、audit。 |
| 乐观锁与失败事务 | 失败请求不改变 `version`、业务字段、关联关系或审计；成功请求仅加一版 | 对应资源、关联表、audit。 |
| 跨租户与权限 | Tenant A 不能读/写 Tenant B 数据；`403` 不产生数据副作用；`404` 不泄露 B | A/B 资源的 ID、`tenant_id`、状态和必要审计。 |

每条查询必须带本次资源 ID 或 `RUN_ID`、`tenant_id`、状态/软删除等完整谓词，禁止扫描共享数据或输出真实客户数据。

## 6. 清理顺序与失败恢复

### 6.1 正常清理

1. 恢复 role-permission 到进入测试前的 ID 快照，并读取新 `version`。
2. 恢复 user-role 到快照，再恢复 User、Store、Tenant 的原状态（均使用当前 `version`）。
3. 调用经批准的 reset API 或由环境负责人整体重置专用业务库；验证仅本次 `RUN_ID` 资源被移除/重置。
4. 使用只读断言确认不存在本次运行的残留资源、关联关系、幂等记录和非预期审计副作用。

当前公开合同没有删除端点或 reset API，所以“状态恢复”不是完整清理。未取得 reset 能力前，任何留下 tenant/store/user/role 的真实写场景仍必须跳过。

### 6.2 失败策略

- 请求失败：停止该场景的依赖步骤，保存脱敏 case、运行 ID、状态、错误码、Trace ID 和已登记资源；不继续假设 ID 或版本。
- 恢复失败：不重试共享数据；标记环境污染风险，禁止下一轮使用同一环境，交由 reset 责任人整体重置。
- 并发/轮询失败：使用有界轮询和明确超时；保存每个请求的脱敏结果与最终数据库快照，不使用固定长时间 `sleep`。
- 数据库断言失败：视为场景失败，即使 HTTP 响应成功；冻结后续同一资源图场景，先完成 reset 和根因分析。

## 7. `BLOCKED` 改为 `READY` 的准入条件

66 条用例只能在以下条件全部满足后，由经过评审的、仅操作 `shipflow_qa.api_test_case` 的状态变更脚本分批改为 `READY`：

1. 隔离后端已证明连接独立、可重置的业务测试库，且没有指向 `shipflow`、`shipflow_test` 或 `shipflow_qa`。
2. 四类身份及最小权限完成非敏感核验，运行时 Secret 注入与日志脱敏已经实现。
3. Tenant A/B、角色、权限和资源创建 API 的合同、允许的权限集合、`version` 规则和停用状态响应矩阵已冻结。
4. 每个写场景有 API-only 恢复动作与最终 reset 责任人；无自动删除时，整体 reset 已演练成功。
5. 数据库断言只读账号、允许表/字段、按 `RUN_ID`/ID 的限定查询和失败保留策略已书面批准。
6. 幂等、同键异体、并发、版本冲突和停用后的状态传播均具备同步/有界轮询方案。
7. 对应 Python fixture、API client、数据库断言和 teardown 已通过代码审查与框架单元测试；随后在隔离环境完成真实 HTTP + 数据库双断言。
8. 执行报告确认无残留资源、无越界访问、无秘密泄露，且负责人批准该批次状态提升。

推荐按风险分批：先只读的 9 条，再具备完整 reset 的单资源写场景，最后跨租户、幂等、并发、角色权限和停用场景。任何一项失败都保持该批次 `BLOCKED`。

## 8. 后续代码、配置与 SQL 变更清单（本轮不实施）

| 类别 | 文件 | 后续变更 |
|---|---|---|
| Python 环境门禁 | `api-tests/common/module2_environment.py` | 增加 Tenant B、无权限身份和独立业务断言配置的变量映射；在真实运行前验证 reset/断言门禁。 |
| Python 运行时 | `api-tests/common/module2_runtime.py` | 将单值资源上下文扩展为 A/B 资源图、权限/绑定快照、每资源当前 version、运行 ID 和受控恢复登记。 |
| Python 客户端 | `api-tests/clients/module2_client.py` | 保持 20 接口覆盖；补充多身份 client/session 切换、写请求 CSRF/幂等上下文、响应与 Trace ID 的脱敏诊断。 |
| Python fixture | `api-tests/tests/module2/conftest.py` | 建立四身份 fixture、动态资源图、reset 所有权门禁、只读数据库断言 fixture 和失败隔离。 |
| Python 场景 | `api-tests/tests/module2/test_module2_api.py` | 将 20 个基线入口与 66 条数据库驱动场景建立显式映射；依赖资源未创建或恢复失败时跳过后续场景。 |
| Python 数据驱动执行 | `api-tests/conftest.py`、`api-tests/repositories/api_test_case_repository.py`、`api-tests/executors/action_executor.py` | 新增受控的模块二 `BLOCKED` 预演选择和获批 `READY` 执行路径；实现 API-only setup、快照、轮询、并发和数据库断言动作，禁止 SQL fixture 写入。 |
| Python 测试 | `api-tests/tests/module2/test_module2_environment.py`、`test_module2_runtime.py`、`test_module2_client.py` | 覆盖新增门禁、资源图、快照、脱敏和失败恢复；真实 HTTP 测试另行在隔离环境运行。 |
| 后端配置 | 建议新增 `backend/src/main/resources/application-module2-it.yml` | 仅引用独立业务测试库的环境变量；启用测试环境标识、受控 reset 能力和安全日志。不得修改生产/共享库配置。 |
| API 测试配置 | 建议新增 `api-tests/config/env/module2-isolated.yaml` | 只保存变量名、超时、轮询上限和非敏感环境标识；不得保存连接串或 Token。 |
| QA SQL | 未来建议新增 `database/qa/008_promote_module2_ready_cases.sql` | 仅在准入通过后，将获批 case_no 的 `automation_status` 从 `BLOCKED` 更新为 `READY`；必须限定 `shipflow_qa.api_test_case` 和精确 case_no，不能触及业务库。 |
| 生命周期文档 | `docs/module-lifecycle/02-tenant-store-user-rbac-api-test-lifecycle.md`、本文件 | 在真实执行后更新实际导入事实、执行证据和剩余门禁；不得预先声称通过。 |

## 9. 负责人待决问题

1. 独立业务测试库和整体 reset 的所有者、可用窗口及失败升级人是谁？
2. 角色创建/停用 fixture 由何种受控 API 或环境模板提供？当前公开 20 接口不含角色创建和状态接口。
3. 停用租户、店铺、用户、角色后的准确 HTTP 状态码和错误码矩阵是否已冻结？
4. 是否批准只读断言账号访问上述表/字段，以及审计和幂等数据的保留期限？

## 10. 当前基础设施实现快照

本轮已实现可复用基础设施，但没有打开真实 HTTP 执行开关：

- `module2_environment.py` 现在要求明确声明业务库名为 `shipflow_http_test`，并支持平台管理员、Tenant A、Tenant B、低权限身份的四个 runtime-only Token 变量。
- `module2_runtime.py` 增加运行 ID 资源图、隔离名称、四身份上下文、Tenant A/B 创建、角色权限/用户角色快照、API-only 绑定恢复、Request ID、幂等重放、版本不变、403 和跨租户 404 断言。
- `module2_database_assertions.py` 只接受由未来只读 fixture 注入的行数据，提供 tenant_id、version、状态、软删除、跨租户、幂等唯一效果和失败不改版断言；它不打开连接、不构造写 SQL。
- `module2-isolated.yaml.example` 只记录变量名和业务库固定值，不含任何凭据、连接串或 Token。

角色资源仍只能从已批准的隔离 fixture 中查询和快照，因为公开合同没有创建/删除角色 API。任何真实运行仍必须满足第 7 节准入条件，66 条 `shipflow_qa.api_test_case` 保持 `BLOCKED`。

## 11. 学习笔记

数据驱动的 `READY` 不仅表示 HTTP client 能发送请求；它要求每个占位符都有受控来源、每个状态变化有可恢复路径、每个核心写入都有 HTTP 与独立业务库的双断言。将资源图和只读断言做成纯运行时/注入式组件，可以防止 QA 元数据存储被误用为业务 fixture 或清理通道。
