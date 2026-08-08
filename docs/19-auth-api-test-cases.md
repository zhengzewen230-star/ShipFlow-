# ShipFlow 认证 API 自动化测试用例

## 1. 目标与边界

本阶段为 ShipFlow 第一个认证模块建立数据驱动的接口自动化测试用例，覆盖：

- `GET /api/v1/auth/csrf`
- `POST /api/v1/auth/login`
- `GET /api/v1/users/me`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

测试用例元数据只保存在独立数据库 `shipflow_qa`。建库和种子脚本不引用、不更新 `shipflow` 或 `shipflow_test`，也不包含业务表外键。

`shipflow_qa` 是用例仓库，不是被测系统的业务数据库。用户、租户、角色、Refresh Session 等状态型前置数据，后续必须由 pytest 在专用测试环境中通过受控 fixture 准备，并在用例结束后恢复或清理。禁止把禁用用户、重放 Token 等测试直接作用于生产或共享业务库。

当前已在独立的 `shipflow_qa` 中完成执行契约升级，并由 Windows pytest 执行器通过外部后端和 Ubuntu Docker MySQL 8.0 完成认证接口回归。测试不会修改 `shipflow_qa` 中的用例定义，也不通过 QA 数据库账号访问业务库。

## 2. 文件与执行顺序

按以下顺序执行：

1. `database/qa/001_create_api_test_case.sql`
2. `database/qa/002_seed_auth_api_test_cases.sql`
3. `database/qa/003_upgrade_api_test_case_execution_contract.sql`
4. `database/qa/004_fix_login_case_teardown.sql`
5. `database/qa/005_fix_auth_login_004_assertion.sql`

第一个脚本使用 `CREATE DATABASE IF NOT EXISTS` 和 `CREATE TABLE IF NOT EXISTS`。第二个脚本以 `case_no` 为唯一键执行 `ON DUPLICATE KEY UPDATE`，因此可重复执行，不会重复插入同一用例。

`003` 增加结构化 setup、extractor、teardown、断言和自动化状态契约。`004` 为两个成功登录用例补齐独立 logout teardown。`005` 将 `AUTH-LOGIN-004` 的最终安全断言固定为 Trace ID 存在且响应不包含 `passwordHash`。后两个脚本使用精确 `case_no` 更新，可以重复执行。

## 3. 用例数量

| 接口 | 用例数 |
|---|---:|
| `GET /api/v1/auth/csrf` | 5 |
| `POST /api/v1/auth/login` | 18 |
| `GET /api/v1/users/me` | 9 |
| `POST /api/v1/auth/refresh` | 12 |
| `POST /api/v1/auth/logout` | 8 |
| 合计 | 52 |

用例类型包含 `NORMAL`、`EXCEPTION`、`SECURITY`、`BOUNDARY` 和 `STATE_FLOW`，覆盖正常流程、参数校验、认证失败、租户隔离、CSRF、JWT 校验、Refresh Token 轮换与重放、Logout 幂等、Cookie 属性和敏感信息防泄漏。

## 4. 敏感数据规则

SQL 中只保存占位符，例如：

- `${ACCESS_TOKEN}`
- `${XSRF_TOKEN}`
- `${REFRESH_COOKIE}`
- `${VALID_PASSWORD}`
- `${TENANT_CODE_A}`

占位符的真实值必须在测试运行时从环境变量、fixture 或响应上下文注入。禁止把以下内容写入 `shipflow_qa`、测试报告或 Git：

- 明文密码和数据库密码；
- Access Token、原始 Refresh Token；
- Refresh Token HMAC 密钥或摘要；
- JWT 私钥；
- 真实客户数据。

测试失败日志应只显示 `case_no`、HTTP 状态、错误码、Trace ID 和脱敏后的断言差异。

## 5. Ubuntu MySQL 8.0 执行方法

以下命令由项目负责人在 Ubuntu 上人工执行。密码始终在 MySQL 客户端提示符中交互输入，不写入命令、脚本或 Git。

先上传 SQL 到现有 MySQL 8.0 容器：

```bash
docker cp database/qa/001_create_api_test_case.sql my-mysql-docker:/tmp/001_create_api_test_case.sql
docker cp database/qa/002_seed_auth_api_test_cases.sql my-mysql-docker:/tmp/002_seed_auth_api_test_cases.sql
docker cp database/qa/003_upgrade_api_test_case_execution_contract.sql my-mysql-docker:/tmp/003_upgrade_api_test_case_execution_contract.sql
docker cp database/qa/004_fix_login_case_teardown.sql my-mysql-docker:/tmp/004_fix_login_case_teardown.sql
docker cp database/qa/005_fix_auth_login_004_assertion.sql my-mysql-docker:/tmp/005_fix_auth_login_004_assertion.sql
```

进入现有容器中的 MySQL 客户端：

```bash
docker exec -it my-mysql-docker mysql --default-character-set=utf8mb4 -u root -p
```

在 MySQL 中执行：

```sql
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SOURCE /tmp/001_create_api_test_case.sql;
SOURCE /tmp/002_seed_auth_api_test_cases.sql;
SOURCE /tmp/003_upgrade_api_test_case_execution_contract.sql;
SOURCE /tmp/004_fix_login_case_teardown.sql;
SOURCE /tmp/005_fix_auth_login_004_assertion.sql;
```

执行后只读核验：

```sql
SELECT COUNT(*) AS total_case_count
FROM shipflow_qa.api_test_case
WHERE enabled = 1 AND case_no LIKE 'AUTH-%';

SELECT request_path, http_method, COUNT(*) AS case_count
FROM shipflow_qa.api_test_case
WHERE enabled = 1 AND case_no LIKE 'AUTH-%'
GROUP BY request_path, http_method
ORDER BY request_path, http_method;

SELECT case_no, COUNT(*) AS duplicate_count
FROM shipflow_qa.api_test_case
GROUP BY case_no
HAVING COUNT(*) > 1;
```

预期总数为 52，重复编号查询返回 0 行。不得停止、删除或重建现有容器，不得操作 MySQL 5.7 和 Jira 容器，也不得执行 Docker 清理命令。

## 6. pytest 数据驱动方案

pytest 通过专用只读 QA 数据库账号读取启用用例。连接信息只从环境变量获取：

```text
SHIPFLOW_QA_DB_HOST
SHIPFLOW_QA_DB_PORT
SHIPFLOW_QA_DB_NAME
SHIPFLOW_QA_DB_USERNAME
SHIPFLOW_QA_DB_PASSWORD
SHIPFLOW_API_BASE_URL
SHIPFLOW_TEST_PASSWORD
SHIPFLOW_PLATFORM_TEST_PASSWORD
```

推荐读取查询：

```sql
SELECT
    case_no,
    module,
    title,
    test_type,
    priority,
    precondition,
    http_method,
    request_path,
    headers_template,
    cookie_template,
    request_body_template,
    expected_status,
    expected_error_code,
    assertions,
    data_dependency
FROM shipflow_qa.api_test_case
WHERE enabled = 1
  AND module IN ('AUTH_CSRF', 'AUTH_LOGIN', 'AUTH_CURRENT_USER', 'AUTH_REFRESH', 'AUTH_LOGOUT')
ORDER BY case_no;
```

pytest 执行器的建议流程：

1. 从 `shipflow_qa.api_test_case` 读取用例并按 `case_no` 参数化。
2. fixture 在专用被测数据库准备随机租户、用户、角色和会话数据，不依赖固定生产账号。
3. 调用 `/auth/csrf` 获取运行时 `${XSRF_TOKEN}`。
4. 正常登录后从 JSON 获取 `${ACCESS_TOKEN}`，从 Cookie Jar 获取 `${REFRESH_COOKIE}`；禁止输出其原值。
5. 通过占位符解析器替换 Header、Cookie 和请求体模板。
6. 执行 HTTP 断言，并对轮换、重放、禁用和 Logout 用例增加数据库状态断言。
7. 在 `finally` 或 fixture teardown 中清理本次随机测试数据；异步验证使用轮询，禁止固定长时间 `sleep`。

`headers_template`、`cookie_template` 和 `assertions` 是 JSON 字段；`request_body_template` 使用文本字段，以支持故意构造的畸形 JSON。用例执行器不得使用 `eval` 解析模板，应采用明确的 JSON 解析和白名单占位符替换。

## 7. 特殊测试数据说明

- Access Token 的过期、错误 issuer 和错误 audience 场景，应使用 QA 环境专用 RS256 密钥和可控 Clock 生成；私钥保存在仓库外，不进入数据库或测试报告。
- Refresh Token 轮换和 family 重放用例必须串行执行，并同时断言 HTTP 响应和 `auth_refresh_session` 状态。
- 用户、租户和角色禁用场景必须使用本次测试创建的随机数据，测试结束后恢复状态。
- 跨租户用例至少准备两个租户中的同名用户，确认查询不会回退到平台范围或其他租户。
- 生产 Cookie 属性用例应在生产等价安全配置中执行；本地 HTTP 可以关闭 `Secure`，但不能据此判定生产 Cookie 安全测试通过。

## 8. 当前验证结果与边界

- `SHIPFLOW_ENV=ci` 下完整接口自动化为 90 passed，Allure 和 JUnit 结果均已生成。
- `shipflow_qa_reader` 仅具有 `shipflow_qa.*` 的 `SELECT` 权限。
- 后端和 MySQL 是固定外部依赖，Jenkins 不负责启动后端或执行数据库迁移。
- 固定测试环境采用 Jenkins 与人工测试互斥使用窗口，避免认证会话状态互相影响。
- `BLOCKED` 和 `DEFERRED` 用例仍需专用业务 fixture、受信 JWT 签发能力或生产等价 Cookie 配置，不能标记为当前已自动执行。
