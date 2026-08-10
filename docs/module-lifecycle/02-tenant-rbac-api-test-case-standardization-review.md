# 第一、第二模块 `api_test_case` 元数据规范性审查

状态：只读审查。审查对象为第一模块源 SQL `002–005` 形成的认证用例契约，以及第二模块已导入来源 `007_seed_module2_frozen_20_api_test_cases.sql` 的 66 条 `BLOCKED` 用例。未连接数据库、未读取实际行、未修改既有文件、未提交 Git。

## 1. 审查结论

第一模块有 52 个认证用例：`NORMAL` 5、`EXCEPTION` 4、`SECURITY` 26、`BOUNDARY` 5、`STATE_FLOW` 12；其中 P0 44、P1 8。第二模块有 66 个用例：`NORMAL` 25、`EXCEPTION` 29、`SECURITY` 12，全部为 P1 和 `BLOCKED`。两模块均能保存 JSON 头、Cookie、请求体和结构化执行契约，但成熟度不同：

- 第一模块的初始 `002` 种子包含自然语言断言；`003` 再补齐结构化 assertion、setup、extractor、teardown、状态和环境字段，`004–005` 作最终修正。因此不能把 `002` 单独当作最终执行合同。
- 第二模块 `007` 已使用结构化 JSON 字段和 66 个范围对齐编号，但所有用例的 `setup_steps`、`teardown_steps` 大多为空，且运行时资源、身份、CSRF、版本、请求 ID、数据库断言没有可由通用数据库驱动执行器自动建立的合同。
- 现有 `PlaceholderResolver` 可递归解析字符串、JSON 对象和数组中的 `${UPPER_SNAKE_CASE}`，`RequestExecutor` 可以解析 headers、cookies 和 body；问题不在语法解析，而在第二模块没有把所需变量放入 `ScenarioContext`。

结论：当前不应变更任何数据库行，也不应将第二模块改为 `READY`。规范获得批准后，才应以精确 case_no 的可审查更新/upsert 同步元数据。

## 2. 字段逐项比较

| 字段 | 第一模块现状 | 第二模块现状 | 规范性结论与建议 |
|---|---|---|---|
| `case_no` | `AUTH-CSRF-001`、`AUTH-LOGIN-001` 等，域和子域清晰。 | `TENANT-001`、`STORE-001`、`USER-001`、`RBAC-001`，域清晰但没有子域。 | 统一为 `DOMAIN[-SUBDOMAIN]-NNN`；三位顺序号不可复用、不因状态改变。第二模块短域名可保留，不必为形式而重编号。 |
| `module` | `AUTH_CSRF`、`AUTH_LOGIN`、`AUTH_CURRENT_USER` 等全大写 snake case。 | `TENANT`、`STORE`、`USER`、`RBAC`。 | 统一使用全大写 snake case，表示执行归属而非 URL；允许单段模块名。维护受控枚举与 case_no 域的一对一映射。 |
| `title` | 以中文业务描述为主，夹杂 `CSRF`、`Token`、`DISABLED`。 | 全英文小写短语，如 `create tenant`。 | 统一采用中文业务标题，保留通用技术术语（CSRF、JWT、Idempotency-Key、version、HTTP 状态码）原文；格式为“对象 + 动作/条件 + 预期结果”，不含真实数据。 |
| `test_type` | 覆盖 NORMAL/EXCEPTION/SECURITY/BOUNDARY/STATE_FLOW。 | 仅 NORMAL/EXCEPTION/SECURITY。 | 保持五类枚举：正常流程 NORMAL、输入边界 BOUNDARY、合同/校验 EXCEPTION、认证授权/隔离 SECURITY、状态/并发/幂等 STATE_FLOW。第二模块幂等、版本、停用、并发不应全部折入 NORMAL 或 EXCEPTION。 |
| `priority` | P0 比例为 44/52，安全性优先合理但过宽。 | 全部 P1，未区分隔离、权限提升和并发重复创建风险。 | P0 只用于租户越权、认证/CSRF 绕过、幂等导致重复写入、角色权限越权、不可恢复状态和关键会话安全；一般正向、参数校验和常规列表为 P1。P2/P3 留给低风险兼容性/可观测性。 |
| `precondition` | 初始种子多为自然语言；最终结构化 setup 由 `003–005` 补齐。 | 历史草稿统一写为“需要运行时夹具”，信息不足；现已由中文最终化脚本补齐动态前置条件。 | 采用可机器审查的前置字段：身份别名、scope/tenant、资源图、状态、版本来源、幂等键规则、CSRF 需要、数据库断言标签、清理能力、环境门禁。自然语言只能作说明，不能代替 setup。 |
| headers/cookies/body | 认证用例明确标注 CSRF/Refresh 占位符及畸形 JSON。 | 统一使用 Bearer、Request ID、Idempotency-Key；Cookie 均为空，body 只含 `runtime_case` 示意。 | 为每个接口写入真实可解析的请求模板；未认证用例必须没有 Authorization header；写接口按已冻结 CSRF 合同明确 header/cookie；畸形 JSON 允许以字符串 body 保存。 |
| assertions/extractors | 最终迁移为结构化 JSON。 | 只有 `X-Trace-Id exists` 的通用断言；资源用例部分带 ID/version extractor。 | 每条用例至少有 `success`/错误 envelope、Trace ID、核心资源字段/副作用断言；写入、跨租户、幂等、版本场景必须加专用 HTTP 与数据库断言标签。 |
| setup/teardown | 认证已有 `get_csrf`、`login`、`logout` 等动作。 | 空数组；只能依赖未建立的外部上下文。 | 统一为动作数组，明确 API-only setup、快照、恢复、cleanup owner 和失败升级；没有可恢复清理合同的用例不得 READY。 |
| `automation_status`/`enabled` | READY/BLOCKED/DEFERRED 有明确执行器语义。 | 66 条 `enabled=1`、`BLOCKED`，符合“可见但不可执行”。 | `enabled` 表示是否纳入管理，`automation_status` 表示是否可自动执行；READY 必须可独立完成 setup/request/assertion/teardown，BLOCKED 说明明确外部缺口，DEFERRED 说明长期缺少合同/能力。 |

## 3. 占位符与框架可执行性

### 3.1 现有框架可解析的形式

`PlaceholderResolver` 支持 `${UPPER_SNAKE_CASE}`：完整占位符可保留原始值类型，嵌入字符串会转为标量。`RequestExecutor` 可递归解析 `headers_template`、`cookie_template` 和 body，并要求 header/cookie value 为标量。因此下列形式在语法层可解析：

- `Bearer ${ACCESS_TOKEN}`、`${TENANT_A_ID}`、`${VERSION}`、`${IDEMPOTENCY_KEY}`；
- JSON body 内的 `${...}`；
- Cookie/headers 中的 `${XSRF_TOKEN}`、`${REFRESH_COOKIE}`；
- 请求体为合法 JSON object/list，或作为 string 保留的畸形 JSON 用例。

### 3.2 第一模块缺口

认证初始种子含 `ACCESS_TOKEN`、`XSRF_TOKEN`、Refresh Cookie、测试身份和特殊 JWT/会话状态占位符。最终 `003–005` 已为部分 READY 用例补入 CSRF、登录、Token fixture 与 logout 动作；其余涉及生产 Cookie、可信密钥、禁用状态、会话族或数据库内部状态的用例应维持 BLOCKED/DEFERRED，直到受控 fixture 与只读断言到位。

### 3.3 第二模块缺口

第二模块 66 条出现的变量为 `ACCESS_TOKEN`、`REQUEST_ID`、`IDEMPOTENCY_KEY`、`VERSION`、`TENANT_A_ID`、`STORE_A_ID`、`STORE_B_ID`、`USER_A_ID`、`USER_B_ID`、`ROLE_A_ID`、`ROLE_B_ID`。现有通用 setup 动作没有提供模块二 API 创建、Tenant A/B 身份切换、请求 ID、版本快照、角色/权限快照或数据库断言动作；因此它们会在解析或业务前置阶段失败，而不是成为可执行测试。

还存在如下模板问题，须在获批实施时修正：

1. `TENANT-006/007`、`STORE-006`、`USER-007`、`RBAC-005` 是未认证 401 场景，却继承了 Authorization header 模板；应明确为空 header 或只保留不含认证的追踪头。
2. 403 用例需要 `NO_PERMISSION_ACCESS_TOKEN` 或与场景相符的 `TENANT_A/B_ACCESS_TOKEN`，不能都使用泛化的 `${ACCESS_TOKEN}`。
3. 资源创建、更新、状态和角色绑定 body 当前仅有 `runtime_case` 示意，缺少符合接口合同的字段、当前 version、动态编码/用户名、角色/权限 ID 集合。
4. 写接口是否要求 CSRF header/cookie 必须由已冻结安全合同确认；确认后必须把需要的 Cookie/header 和 setup 动作一致地写入，不能依赖隐式 `HttpClient` 状态。

## 4. 第二模块 READY 缺失项

| 缺失类别 | 必需内容 | 受影响用例 |
|---|---|---|
| 环境 | 独立业务后端、可整体 reset 的业务测试库、禁止连接三类共享库的证明 | 全部 66 条。 |
| 身份 | 平台管理员、Tenant A、Tenant B、无权限身份；最小权限和 runtime secret 注入 | 正向、401/403、跨租户的全部场景。 |
| 资源图 | 动态 Tenant A/B、Store A/B、User A/B、Role A/B、允许权限集、当前状态/version | 资源读取/写入、跨租户、角色权限和停用场景。 |
| 请求上下文 | 每请求 Request ID、幂等键、当前 version、CSRF 会话/Token（如合同要求） | 全部写接口、幂等、版本、并发场景。 |
| setup/extractor | API-only create/query/identity-switch/snapshot 动作，ID/version/权限集提取 | 全部依赖 `${..._ID}`、`${VERSION}` 的场景。 |
| HTTP 断言 | 成功/错误 envelope、Trace ID、资源字段、同键重放、同键异体、403/404 不泄露 | 全部 66 条，尤其跨租户和幂等。 |
| 数据库断言 | tenant_id、scope、deleted、版本、唯一性、关联关系、审计、事务回滚 | 写入、隔离、RBAC、幂等、并发和版本场景。 |
| 清理 | 绑定快照恢复、状态恢复、reset owner、失败后环境隔离策略 | 所有动态写入及状态/绑定改变场景。 |

## 5. 应提升为 P0 的第二模块用例

下列建议是风险分级，不是本轮数据修改。它们涉及越权、跨租户泄露、重复业务效果或角色权限提升：

| 建议 P0 | 原因 |
|---|---|
| TENANT-008、TENANT-009、TENANT-010 | Tenant 管理员不得访问平台租户创建、列表和详情。 |
| TENANT-013、TENANT-014、TENANT-017 | 平台租户创建的同键重放、同键异体冲突与并发去重。 |
| STORE-008、STORE-009 | Tenant A 不得读取或更新 Tenant B 店铺。 |
| USER-009、USER-010 | Tenant A 不得读取或更新 Tenant B 用户。 |
| USER-016、USER-017 | 用户角色不得绑定跨租户角色或平台作用域角色。 |
| RBAC-007、RBAC-008、RBAC-009 | 角色详情/权限替换的跨租户隔离和缺少角色管理权限的拒绝。 |

其余正常 CRUD、列表筛选、输入格式、重复名称和一般停用合同建议先保持 P1；若负责人将停用后的实时身份失效定义为安全阻断，可在冻结错误码矩阵后提升相关停用场景。

## 6. 建议统一后的字段标准

| 字段 | 统一标准 |
|---|---|
| `case_no` | `DOMAIN[-SUBDOMAIN]-NNN`，全大写、三位编号、稳定不可重用。 |
| `module` | 受控全大写 snake case；必须与 case_no 域映射。 |
| `title` | 中文“对象 + 动作/条件 + 预期”，技术术语保留原文；不含秘密和固定客户数据。 |
| `test_type` | 严格采用 NORMAL、BOUNDARY、EXCEPTION、SECURITY、STATE_FLOW；幂等/并发/停用优先 STATE_FLOW。 |
| `priority` | P0=隔离、权限、认证、幂等重复写、不可恢复状态；P1=主流程/合同校验；P2/P3=低风险兼容性或可观测性。 |
| `precondition` | 写明 identity alias、scope/tenant、资源/状态/version 来源、环境门禁、清理能力和 DB assertion tag。 |
| templates | JSON 对象仅含标量 header/cookie；placeholder 仅 `${UPPER_SNAKE_CASE}`；未认证模板不得含 Authorization；body 必须对应真实合同或有意保存为畸形 string。 |
| `assertions` | 数组元素均为 `{source,path,operator,expected}`；每条至少验证 envelope/Trace ID，写入场景另有资源/version/副作用合同。 |
| setup/extractors/teardown | 全为结构化数组；setup 只调用 API/受控 fixture，extractor 显式保存变量，teardown 有恢复或 reset owner；禁止直接 SQL 清理。 |
| `data_dependency`/`tags` | 记录非敏感资源图、RUN_ID、DB assertion category、风险类型和状态；不保存 Token、Cookie、密码或连接串。 |
| `enabled`/status/scope/order | `enabled` 管理纳入；READY 仅表示可独立真实执行；BLOCKED 必须有具体 blocker；environment scope 和 execution order 必填且可排序。 |

## 7. 后续需要修改的源 SQL 与 Python 文件

以下是获批后的实施清单，不是本轮改动：

| 类别 | 文件 | 需要的修改 |
|---|---|---|
| 第一模块历史种子 | `database/qa/002_seed_auth_api_test_cases.sql`、`003_upgrade_api_test_case_execution_contract.sql`、`004_fix_login_case_teardown.sql`、`005_fix_auth_login_004_assertion.sql` | 将最终合同集中为可追溯的结构化标准；避免仅靠多份历史 UPDATE 才能理解一条 case 的最终元数据。 |
| 第二模块种子 | `database/qa/007_seed_module2_frozen_20_api_test_cases.sql` | 改 title/type/priority、未认证与无权限模板、真实 body、setup/extractors/teardown、断言、tags 和明确 blocker；仍保持 BLOCKED，直至准入完成。 |
| 后续状态提升 | 建议新增 `database/qa/008_promote_module2_ready_cases.sql` | 仅精确更新获批的 case_no、`automation_status`、tags 和更新时间；只能操作 `shipflow_qa.api_test_case`。 |
| 占位符/上下文 | `api-tests/common/placeholder_resolver.py`、`api-tests/common/scenario_context.py` | 保持现有语法；增加身份别名、运行 ID、资源图和变量来源校验，避免泛化 ACCESS_TOKEN 被错误复用。 |
| 请求/动作/断言 | `api-tests/executors/request_executor.py`、`action_executor.py`、`extractor_executor.py`、`assertion_executor.py`、`scenario_executor.py` | 实现模块二 API-only setup、身份切换、CSRF/Request ID、版本和绑定快照、数据库只读断言、并发与有界轮询、失败后的受控 teardown。 |
| 模块二框架 | `api-tests/common/module2_environment.py`、`module2_runtime.py`、`api-tests/clients/module2_client.py`、`api-tests/tests/module2/conftest.py`、`test_module2_api.py` | 支持 A/B/no-permission 四身份、动态资源图、reset 门禁、脱敏诊断和 66 条用例映射。 |
| 数据驱动加载 | `api-tests/repositories/api_test_case_repository.py`、`api-tests/conftest.py` | 在批准的隔离环境中按模块读取 READY；不得为执行 BLOCKED 用例绕过现有 READY 检查。 |

## 8. 是否需要重新导入 66 条用例

该段为当时的只读审查结论，现已被后续最终化执行取代。66 条用例已通过 `007` 导入并由 `008_upgrade_module1_module2_api_test_cases_cn_final.sql` 同步中文元数据，全部继续保持 `BLOCKED`。后续状态提升必须另用独立、精确 case_no 的 SQL，不与模板标准化混合。

## 9. 负责人需确认的问题

1. 第一模块 P0 是否要从“多数安全场景”收紧到上述统一风险定义？
2. 第二模块写接口的 CSRF cookie/header 合同、停用错误码矩阵和 Role fixture API 是否已冻结？
3. 隔离业务测试库 reset、只读数据库断言账号和运行后资源保留责任由谁承担？

## 10. 最终化实施决定（2026-08-10）

本次采用新增 `008_upgrade_module1_module2_api_test_cases_cn_final.sql` 的方式，不改写历史 `007`。第二模块 66 条用例的标题、动态前置条件、数据依赖与清理说明改为中文；test_type 仍使用既有机器枚举，幂等、版本、停用与并发场景统一为 `STATE_FLOW`，跨租户场景统一为 `SECURITY`。高风险的跨租户、权限边界及重复写入场景提升为 `P0`，但不改变任何用例的 `BLOCKED` 状态。

第一模块检查结论：现有中文种子与认证请求模板兼容，无须以中文最终化为由修改已验证的请求模板或认证逻辑。第二模块的 66 条元数据已通过上述独立升级脚本完成同步；状态转 `READY` 必须使用后续单独、精确且经 HTTP 验收批准的脚本。
