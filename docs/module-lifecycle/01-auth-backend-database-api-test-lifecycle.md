# 第一功能模块生命周期：认证、数据库、接口自动化与 CI

## 1. 模块范围

本模块包含四条相互配合的链路：

1. 后端认证与当前用户：登录、Access Token、Refresh Token、CSRF、登出、实时身份和权限加载。
2. 数据库：基础结构、初始化账号与角色权限、认证相关迁移。
3. `api-tests`：基于 pytest、HTTP Client、数据库用例仓库和场景执行器的接口自动化框架。
4. Windows Jenkins 与 Allure/JUnit：使用固定外部后端和 QA MySQL 执行回归。

本文件只记录第一模块。租户、店铺、用户管理和 RBAC 管理接口属于第二阶段，不作为第一模块完成度的依据。

## 2. 后端实现

### 2.1 分层与关键文件

认证后端位于 `backend/src/main/java/com/shipflow/auth/`，安全基础设施位于 `backend/src/main/java/com/shipflow/security/`。

| 层次 | 实际文件 | 作用 |
|---|---|---|
| Controller | `auth/api/AuthController.java` | 提供 CSRF、登录、刷新、登出，并设置安全 Cookie、Cache-Control 和统一成功响应 |
| Controller | `auth/api/CurrentUserController.java` | 从认证后的 JWT subject 和 tenant claim 加载当前用户 |
| 请求模型 | `auth/api/model/AuthLoginRequest.java` | 校验 username、password、tenantCode 请求 |
| 应用服务 | `auth/application/LoginApplicationService.java` | 校验登录命令、加载身份、执行 BCrypt 校验、签发 Access Token |
| 应用服务 | `auth/application/AuthApplicationService.java` | 组合登录与 Refresh Session；处理 refresh、logout、currentUser |
| 身份服务 | `auth/service/LoginIdentityService.java` | 按平台/租户范围查询用户，校验用户和租户状态，加载实时角色权限 |
| 密码服务 | `auth/service/PasswordAuthenticationService.java` | 使用项目配置的 `PasswordEncoder` 校验密码，失败统一为认证异常 |
| Mapper | `auth/mapper/SysUserMapper.java`、`UserAuthorityMapper.java` | 查询平台用户、租户用户和有效权限视图 |
| Mapper XML | `resources/mapper/auth/SysUserMapper.xml`、`UserAuthorityMapper.xml` | 显式列查询、tenantCode/username 绑定、deleted/status/作用域过滤 |
| 当前调用者 | `security/authorization/CurrentCaller.java`、`CurrentCallerService.java` | 从数据库实时重载 userId、tenantId、scope 和 permission codes，并映射为 GrantedAuthority |
| JWT | `security/jwt/JwtCryptoConfiguration.java`、`JwtProperties.java`、`PemKeyLoader.java` | 加载外部 RSA 密钥，创建 JwtEncoder/JwtDecoder，并校验 issuer、audience、kid、typ、时间窗口 |
| Access Token | `security/jwt/AccessTokenService.java`、`AccessTokenPrincipal.java` | 生成短时 JWT；JWT 不保存 roles/permissions |
| Refresh Token | `security/refresh/RefreshTokenSessionService.java` 及同包类 | 保存 Token 摘要和会话状态，支持轮换、重放检测、撤销和过期 |
| 安全配置 | `security/SecurityConfig.java` | 公开认证入口和健康检查；保护当前用户及管理路径；未知 `/api/**` 默认要求认证 |
| 统一响应 | `common/api/ApiResponse.java`、`ApiErrorResponse.java` | 返回 success、traceId、message、data 或 error |
| 异常与链路 | `common/exception/GlobalExceptionHandler.java`、`common/trace/TraceIdFilter.java`、`TraceId.java` | 统一错误码、HTTP 状态和 `X-Trace-Id` 响应头/响应体 |

第一模块相关实现提交主要为：`3c1a616`、`3fd9263` 和 `121e891`。具体提交记录见第 8 节。

### 2.2 登录流程

```text
POST /api/v1/auth/login
  -> AuthController.login
  -> AuthApplicationService.login
  -> LoginApplicationService.login
  -> LoginIdentityService.lookup
  -> SysUserMapper.xml
  -> UserAuthorityMapper.xml
  -> PasswordAuthenticationService.authenticate
  -> AccessTokenService.issue
  -> RefreshTokenSessionService.issueInitial
  -> ApiResponse<AuthTokenResponse> + Refresh Cookie
```

平台用户使用 `tenant_id IS NULL` 查询，不需要 tenantCode；租户用户必须通过 tenantCode 关联 `tenant`，再以 `sys_user.tenant_id = tenant.id` 查询。用户、租户、角色、角色权限均需满足有效状态和 `deleted = 0` 等条件。用户不存在、密码错误、作用域不匹配、租户停用或角色无效时，不向调用方泄露具体原因，统一返回认证失败。

### 2.3 CSRF、JWT、刷新和登出

- `GET /api/v1/auth/csrf`：通过 Spring Security CSRF repository 生成 `XSRF-TOKEN`，返回 `204` 和非 HttpOnly CSRF Cookie。
- `POST /api/v1/auth/login`：需要有效 CSRF 请求上下文；成功返回短时 Access Token，并设置 Refresh Token Cookie。原始密码、Token 和 Cookie 不写入日志或响应之外的持久化数据。
- `GET /api/v1/users/me`：需要 Bearer Access Token。Controller 使用 JWT 的 subject/tenant claim，服务层再次从数据库实时加载用户、租户、角色和权限。
- `POST /api/v1/auth/refresh`：读取 Refresh Cookie，通过摘要查找会话；成功轮换旧会话和新会话，重新实时加载身份并签发 Access Token。重放、无效、过期或撤销会话返回 `AUTH-1002`。
- `POST /api/v1/auth/logout`：撤销对应 Refresh Session，并清理 Refresh Cookie 与 CSRF Cookie。接口使用 `Cache-Control: no-store`。
- JWT 只携带身份和租户范围所需 claims，不携带 roles/permissions；每次受保护请求通过 `CurrentCallerService` 实时取得权限。
- GrantedAuthority 同时包含 `scope:PLATFORM` 或 `scope:TENANT` 与权限码。平台权限不能由租户 scope 复用。

### 2.4 关键错误和响应

成功结构由 `ApiResponse<T>` 提供，包含 `success`、`traceId`、`message`、`data`。错误结构由 `ApiErrorResponse` 提供，包含 `success=false`、`traceId` 和 `error.code/message/details`。

| 场景 | HTTP | 错误码 |
|---|---:|---|
| 登录身份、密码或登录范围无效 | 401 | `AUTH-1001` |
| Refresh Token 无效、过期、撤销或重放 | 401 | `AUTH-1002` |
| 未携带受保护接口认证 | 401 | `COMMON-1002` |
| CSRF 校验失败 | 403 | `AUTH-1005` |
| 已认证但权限不足 | 403 | `COMMON-1004` |
| 参数校验失败 | 400 | `COMMON-1001` |
| 请求体格式错误 | 400 | `COMMON-1008` |
| 幂等键冲突 | 409 | `COMMON-1009` |
| 未预期内部异常 | 500 | `COMMON-1007` |

所有错误响应通过 TraceId 机制保持响应头 `X-Trace-Id` 与响应体 `traceId` 一致。认证失败不区分用户不存在、租户不存在或密码错误。

### 2.5 后端完成度

已实现并有仓库测试覆盖：认证 Controller、登录和身份服务、BCrypt 校验、JWT Bean/签发与校验、Refresh Session、CSRF、Cookie、Trace ID、实时权限加载和认证错误结构。

未完成或不属于第一模块最终验收的内容：

- 本轮未重新启动后端或执行 HTTP/Postman 验收。
- 当前工作区有集成测试和 `application-integration.yml` 的未提交修改，不能视为已提交的第一模块变更。
- 第一模块以外的租户、店铺、用户、角色管理接口不纳入本生命周期结论。

## 3. 数据库实现

### 3.1 认证相关表

基础结构由 `database/schema.sql` 定义，认证链路使用或关联以下表：

| 表 | 关键字段/约束 | 认证用途 |
|---|---|---|
| `tenant` | `id`、`tenant_code`、`status`、`deleted`、`version`；`tenant_code` 唯一 | 租户登录范围和 ACTIVE 状态 |
| `sys_user` | `tenant_id`、生成列 `scope_tenant_id`、`username`、`password_hash`、`status`、`deleted`、`version`；范围+用户名唯一 | 平台/租户用户和 BCrypt 摘要 |
| `sys_role` | `tenant_id`、`role_scope`、`role_code`、`status`、`deleted`、`version`；范围+角色编码唯一 | 平台或租户角色状态 |
| `sys_permission` | `permission_code`、`permission_name`、`description`；权限编码唯一 | 全局权限字典 |
| `sys_user_role` | `tenant_id`、`user_id`、`role_id`；用户角色唯一 | 用户与角色关系及租户一致性 |
| `sys_role_permission` | `role_id`、`permission_id`；角色权限唯一 | 角色有效权限关系 |
| `auth_refresh_session` | `user_id`、`tenant_id`、`token_hash`、`family_id`、`previous_session_id`、`status`、过期/撤销时间；摘要和前序会话唯一 | Refresh Token 会话轮换和重放控制 |
| `api_idempotency_record` | 作用域、operation、幂等键、请求摘要、处理状态、资源 ID、过期时间；作用域+操作+键唯一 | 认证相关写操作及后续业务接口幂等基础 |
| `audit_log` | tenant/operator、action、resource、request_id、result、occurred_at；tenant/operator 外键 | 可审计操作记录 |

主要索引包括用户租户状态、角色租户作用域、Refresh 用户状态/过期时间、Refresh family 状态、幂等状态/过期时间和审计租户时间。外键连接用户、租户、角色、权限、Refresh Session 和审计操作者，防止孤立关系。

状态字段主要为：

- 租户：`PENDING`、`ACTIVE`、`DISABLED`；
- 用户、角色和店铺：`ACTIVE`、`DISABLED`；
- Refresh Session：`ACTIVE`、`ROTATED`、`REVOKED`、`EXPIRED`；
- 幂等记录：`PROCESSING`、`SUCCEEDED`、`FAILED`、`EXPIRED`；
- 审计结果：`SUCCESS`、`FAILURE`、`REJECTED`。

时间字段使用 `DATETIME(3)`，项目约定以 UTC 处理；应用层使用 `ClockConfig` 提供 UTC Clock。

### 3.2 数据库边界

| 数据库 | 用途 | 允许的第一模块操作 |
|---|---|---|
| `shipflow` | 本地/业务开发库，后端和人工 Postman 使用 | 后端业务运行目标；本轮未连接或写入 |
| `shipflow_test` | Java MySQL 集成测试库 | `*IT` 的受控测试数据；本轮未连接或写入 |
| `shipflow_qa` | Python API 用例库 | 只读读取 `api_test_case`；不是后端业务库 |

禁止把后端指向 `shipflow_qa`，也禁止把 QA 读账号用于业务写操作。当前文档不记录密码、完整连接串或实际连接凭据。

### 3.3 初始化与迁移

| 文件 | 作用 | 本模块状态 |
|---|---|---|
| `database/schema.sql` | 基础表、索引、外键和检查约束 | 已存在，作为结构来源；本轮未执行 |
| `database/init_data.sql` | 平台/租户演示用户、角色、权限及基础数据 | 已存在，作为初始化来源；本轮未执行 |
| `database/migrations/V002__add_api_support_tables.sql` | 增加 `api_idempotency_record` 和 `auth_refresh_session` | 历史迁移文件；执行状态需以目标库 Flyway 历史确认 |
| `database/migrations/V003__repair_v002_comments.sql` | 修正 V002 表和字段注释 | 历史迁移文件；执行状态需以目标库 Flyway 历史确认 |
| `database/qa/001_create_api_test_case.sql` | 创建 QA 用例表 | 历史执行证据来自既有文档，不在本轮重跑 |
| `database/qa/002_seed_auth_api_test_cases.sql` | 写入认证用例种子 | 历史执行证据来自既有文档，不在本轮重跑 |
| `database/qa/003_upgrade_api_test_case_execution_contract.sql` | 增加 setup/extractor/assertion/teardown 等可执行契约 | 历史执行证据来自既有文档，不在本轮重跑 |
| `database/qa/004_fix_login_case_teardown.sql` | 修正登录成功用例 teardown | 历史执行证据来自既有文档，不在本轮重跑 |
| `database/qa/005_fix_auth_login_004_assertion.sql` | 修正登录敏感信息和 Trace ID 断言 | 历史执行证据来自既有文档，不在本轮重跑 |

第一模块不包含第二阶段的 V004/V005 权限迁移；它们属于租户/RBAC 阶段。所有数据库执行结论必须以实际 SQL 输出为准，不能从脚本存在推断目标库已经执行。

## 4. 接口自动化测试

### 4.1 目录与职责

`api-tests` 的实际结构如下：

| 目录/文件 | 职责 |
|---|---|
| `clients/` | `HttpClient`、`AuthClient`、`UserClient`，封装 HTTP、认证入口和当前用户调用 |
| `common/` | 配置加载、断言、JSONPath、占位符、日志、场景上下文和 Token 上下文 |
| `executors/` | action、request、extractor、assertion、scenario 执行器，将数据库契约转为可执行步骤 |
| `models/` | `ApiTestCase` 数据模型 |
| `repositories/` | `ApiTestCaseRepository`，只读查询 QA 数据库中的 READY 用例 |
| `tests/auth/` | CSRF、登录、登出、Refresh Token 接口测试 |
| `tests/users/` | 当前用户接口测试 |
| `tests/framework/` | 框架各执行器、解析器和上下文的单元测试 |
| `tests/test_database_driven_auth.py` | 读取 QA 用例并使用场景执行器参数化执行 |
| `tests/test_case_repository.py` | 验证 QA 用例仓库读取 |
| `tests/test_environment.py` | 验证环境配置和秘密变量名称检查 |
| `config/` | `config.yaml` 默认入口和 `config/env/ci.yaml` CI 配置 |
| `data/auth_data.yaml` | 可提交的非敏感认证测试数据/占位配置 |
| `conftest.py` | fixtures、QA repository、上下文、占位变量和动态参数化 |
| `pytest.ini` | pytest 默认测试路径及报告/标记相关配置 |
| `requirements.txt` | requests、PyMySQL、pytest、Allure 等依赖 |

### 4.2 执行流程

```text
SHIPFLOW_ENV=ci
  -> config_loader 读取 config/env/ci.yaml
  -> QA repository 只读查询 shipflow_qa.api_test_case
  -> pytest_generate_tests 按 execution_order/case_no 参数化 READY 用例
  -> fixture 获取 CSRF、登录凭据和上下文
  -> placeholder resolver 注入运行时 Token/Cookie/密码等值
  -> scenario executor 执行 action/request/extractor/assertion/teardown
  -> pytest 结果 + Allure raw results + JUnit XML
```

`api_test_case` 保存用例元数据、请求方法/路径、请求模板、断言、提取器和 teardown 契约。凭据和运行时 Token 不写入 QA 用例表；测试通过环境变量和响应上下文注入。失败日志只能保留 case 编号、HTTP 状态、错误码、Trace ID 和脱敏差异。

主要环境变量名称及用途：

| 变量 | 用途 |
|---|---|
| `SHIPFLOW_ENV` | 选择 local/ci 配置，CI 使用 `ci` |
| `SHIPFLOW_API_BASE_URL` | 外部后端 HTTP 基地址 |
| `SHIPFLOW_QA_DB_HOST`、`SHIPFLOW_QA_DB_PORT`、`SHIPFLOW_QA_DB_NAME` | QA 用例库连接位置；端口在 Jenkins 中为参数化配置 |
| `SHIPFLOW_QA_DB_USERNAME`、`SHIPFLOW_QA_DB_PASSWORD` | QA 只读账号及其秘密凭据 |
| `SHIPFLOW_TEST_PASSWORD` | 租户演示账号的运行时测试密码 |
| `SHIPFLOW_PLATFORM_TEST_PASSWORD` | 平台演示账号的运行时测试密码 |

密码变量只记录名称，不记录任何值。`Jenkinsfile` 还通过 Jenkins Credentials 注入三个 Secret Text，凭据 ID 为 `shipflow-qa-db-password`、`shipflow-test-password`、`shipflow-platform-test-password`。

### 4.3 第一模块实际覆盖

实际测试文件包括：

- `tests/auth/test_csrf.py`：CSRF 获取、状态和 Cookie 行为；
- `tests/auth/test_login.py`：成功、失败、租户范围、错误响应、敏感信息保护和 Trace ID；
- `tests/auth/test_refresh.py`：刷新、轮换、失效/异常场景；
- `tests/auth/test_logout.py`：登出、Cookie 清理和会话失效；
- `tests/users/test_current_user.py`：当前用户和授权后的用户摘要；
- `tests/test_database_driven_auth.py`：从 QA 表读取认证用例，执行数据库驱动场景；
- `tests/framework/*.py`：执行器、上下文、占位符、断言、提取器和请求层测试；
- `tests/test_case_repository.py`、`tests/test_environment.py`：数据仓库和运行环境契约。

既有仓库文档记录：`SHIPFLOW_ENV=ci` 下完整接口自动化为 **90 passed**，Allure 生成 90 个用例且 100% 通过，JUnit 结果正常生成。这个数量是历史验证记录，不是本轮重新执行结果。

尚未覆盖或不应由第一模块文档宣称完成的内容：

- 当前工作区未重新运行 API 自动化；
- 第二阶段租户、店铺、用户和 RBAC 管理 API 自动化尚未纳入第一模块用例；
- 真实 HTTP/Postman 验收不由本次文档整理执行；
- QA 数据库当前状态、用例数量和 Jenkins 最新构建号本轮未查询。

## 5. Jenkins、Allure 与 JUnit

根目录 `Jenkinsfile` 是 Windows Declarative Pipeline：

1. 使用 label `windows` 的 Agent，30 分钟超时，保留 40 次构建，禁止并行构建。
2. Checkout SCM。
3. `Prepare Python` 使用固定 `D:\python\python.exe` 创建/复用 `api-tests\.venv`，不依赖用户级 `py` 启动器或 PATH 中的 Python。
4. `Check External Environment` 请求外部后端 `/actuator/health`，并使用 QA 只读账号查询 READY 用例数量。
5. `Run API Tests` 使用 `.venv\Scripts\python.exe -m pytest`，生成 `reports/allure-results` 和 `reports/junit.xml`。
6. `post { always { ... } }` 即使 pytest 失败也尝试发布已有 Allure results、JUnit，并归档 JUnit。
7. cleanup 删除 pytest 缓存，不删除项目外文件或数据库数据。

后端和 MySQL 是固定外部依赖，Jenkins 不启动后端、不执行 schema/init/migration。人工测试与 Jenkins 使用互斥窗口，避免共享认证会话和 Refresh Session 状态相互影响。

Allure raw results 是构建产物，不应提交 Git；Jenkins 通过 Allure Plugin 生成 HTML 报告。JUnit 是机器可读结果，用于 Jenkins 测试趋势和失败显示。首次构建没有历史趋势属于正常情况。

## 6. 生命周期状态

状态仅针对第一模块，不延伸到第二阶段租户/RBAC 管理功能。

| 子系统 | 生命周期状态 | 证据/边界 |
|---|---|---|
| 后端认证、JWT、Refresh、CSRF、当前用户 | 已验证 | 仓库后端测试和既有认证验收记录；本轮未重跑 |
| 后端真实 MySQL `*IT` | 仅静态检查 | 集成测试类和 integration profile 存在；当前工作区集成变量未提供，本轮未连接数据库 |
| `schema.sql`、`init_data.sql`、V002/V003 | 已实现 / 仅静态检查 | 文件和 Mapper 依赖已检查；本轮未执行 SQL，不能据此断言目标库状态 |
| QA `api_test_case` 脚本 001-005 | 已实现 / 历史已执行记录 | 脚本存在，既有文档记录过执行；本轮未连接 QA 库或重放 SQL |
| api-tests 框架和第一模块用例 | 已验证 | 既有记录为 90 passed；本轮只读检查，未重跑 |
| Jenkins Windows Pipeline | 已实现 | `Jenkinsfile` 存在并包含 Agent、凭据、健康检查、Allure/JUnit 和失败后发布逻辑 |
| Jenkins 最新构建与 Allure UI | 尚未完成 | 仓库不保存构建编号；本轮未访问 Jenkins UI |
| 第一模块 HTTP 接口自动化验收 | 已验证 | 既有记录覆盖 5 个认证/当前用户接口和 90 条回归；本轮未执行手工 HTTP |
| 第二模块管理接口 HTTP 验收 | 尚未完成 | 不属于本模块；本轮明确不执行 |

## 7. 版本与工作区记录

第一模块相关提交：

| 提交 | 说明 |
|---|---|
| `3c1a616` | `feat(auth): complete authentication security handling`，完成认证后端安全处理 |
| `3fd9263` | `test(api): add database-driven authentication regression`，加入 api-tests、QA 契约和认证回归 |
| `121e891` | `ci: add Windows Jenkins API test pipeline`，加入 Windows Jenkins Declarative Pipeline |
| `b94365f` | `ci: use fixed Python path on Windows Jenkins`，修复 Jenkins 服务账号 Python 路径依赖 |
| `bee9ae0` | `test(auth): harden local auth and tenant HTTP verification`，强化本地认证和验证测试 |

当前仓库状态（文档整理前只读检查）：

- 当前分支：`feature/tenant-rbac`；
- HEAD：`6451d2f test(acceptance): verify phase2 http api acceptance`；
- HEAD 与 `origin/feature/tenant-rbac` 一致；
- 已修改但未提交：5 个后端集成测试/集成配置文件；这些修改不属于本次文档；
- 未跟踪：`docs/20-auth-api-test-framework-guide.md`、`docs/login-401-diagnosis.md`；两者不属于本次文档提交范围；
- 本文件是本轮唯一计划新增文件，放在 `docs/module-lifecycle/`。

本文件不记录密码、Token、Cookie、HMAC、JWT 私钥、完整 password hash 或完整数据库连接字符串。

## 8. 验收与下一阶段边界

本模块文档整理完成后，第一模块的知识沉淀边界已形成，但不替代以下动作：

1. 在提供安全 `SHIPFLOW_IT_DB_URL`、`SHIPFLOW_IT_DB_USERNAME`、`SHIPFLOW_IT_DB_PASSWORD` 后，单独执行 `mvn -Pintegration verify` 并记录真实 Failsafe 结果。
2. 在外部后端运行且确认数据库为 `shipflow` 时，另行执行手工 HTTP/Postman 闭环。
3. 在 Jenkins UI 中确认 Windows Agent、Credentials、Allure Plugin、JUnit 归档和构建历史。
4. 第一模块文档完成前，不开始第二模块接口自动化测试开发；第二模块应另建生命周期或验收记录。

## 9. 中文最终用例兼容性（2026-08-10）

第一模块的面向人字段已采用中文，保留 case_no、module、test_type、priority、HTTP 方法、路径、JSON 字段名、占位符、错误码及权限码等机器合同。本次最终化不修改第一模块的 headers_template、cookie_template、request_body_template、认证执行逻辑或 READY 状态，因此不影响既有认证自动化验证结论。

认证、CSRF、登录与刷新会话仍是第二模块受保护接口的运行时前置步骤，而不是第二模块冻结的 20 个业务接口。第一模块仍须在独立认证验收中维护真实 HTTP 与数据库断言；本轮不运行 pytest、不启动后端。
