# 第二模块租户、店铺、用户、RBAC 真实接口自动化执行报告

## 执行状态

状态：环境预检中，尚未执行真实 HTTP、pytest、JUnit 或 Allure。

## 运行边界

| 资源 | 允许用途 |
|---|---|
| `shipflow_http_test` | 后端业务数据库与测试后的数据库断言。 |
| `shipflow_qa` | 仅读取 `api_test_case` 等测试用例定义。 |
| `shipflow`、`shipflow_test` | 禁止连接、读取、写入、清理或作为后端目标。 |

## 后续记录格式

后续将记录环境门禁、最小冒烟、66 条场景的实际通过/失败/跳过数量、残留数据、JUnit XML 路径、Allure 结果路径以及未解决问题。所有身份凭据、密码、令牌、Cookie、私钥、HMAC 和完整连接串均不写入本报告。

## 2026-08-10 本次真实执行结果

状态：预检失败后已立即停止。所有操作均未超出当前会话的环境变量名称与 JDBC 库名解析；未连接任何数据库，未调用后端。

| 项目 | 实际结果 |
|---|---|
| 预检 | 失败：四个模块二身份 Token 环境变量在当前 Codex 会话中均不存在 |
| 已验证的库名门禁 | `SHIPFLOW_QA_DB_URL` 解析为 `shipflow_qa`；`SHIPFLOW_HTTP_TEST_DB_URL` 和 `SHIPFLOW_MODULE2_BUSINESS_DB_NAME` 均为 `shipflow_http_test` |
| 后端实际目标、健康检查、业务库基础数据 | 未执行；必须在身份门禁通过后才可执行 |
| HTTP 冒烟 | 未执行：0 通过、0 失败、0 跳过 |
| 第二模块 66 条场景 | 未执行：0 通过、0 失败、0 跳过、66 朡执行 |
| 从 `BLOCKED` 转为可执行 | 0 条；未修改仓库源 SQL |
| 失败用例、错误码与 traceId | 无；未发送测试请求 |
| 清理与残留数据 | 无需清理；未创建任何资源 |
| JUnit XML 与 Allure 结果 | 未生成 |

需要项目负责人手工处理：将四个 `SHIPFLOW_MODULE2_*_ACCESS_TOKEN` 变量注入到实际运行 pytest 的同一个 Codex 会话，且不要在聊天、仓库、报告或命令行中暴露其值。完成后重新从预检开始，依次验证后端目标、健康检查、数据库基础数据和冒烟链路。

## 2026-08-10 动态身份认证实现与重新预检

四个 `SHIPFLOW_MODULE2_*_ACCESS_TOKEN` 不再是环境前置。框架已改为仅使用受控的 `SHIPFLOW_PLATFORM_TEST_PASSWORD` 创建四类运行时会话，并为每个身份隔离认证客户端。相关单元测试实测 `31 passed`（仅有 pytest 缓存目录权限警告）。

重新预检已验证 `SHIPFLOW_QA_DB_URL` 为 `shipflow_qa`，`SHIPFLOW_HTTP_TEST_DB_URL` 与 `SHIPFLOW_MODULE2_BUSINESS_DB_NAME` 为 `shipflow_http_test`；但唯一长期登录密码 `SHIPFLOW_PLATFORM_TEST_PASSWORD` 在当前会话缺失。因此已立即停止：未调用健康检查、未连接任何数据库、未调用真实 HTTP、未生成 JUnit/Allure、未修改用例状态。

## 2026-08-10 环境预检与框架实施结果

### 数据库与服务门禁

- `SHIPFLOW_HTTP_TEST_DB_*` 已用只读连接验证为 `shipflow_http_test`：30 张基表；租户 2、用户 10、角色 10、权限 19；无 `flyway_schema_history`。
- `shipflow_qa` 的用户名与密码变量存在，但主机、端口和库名变量缺失，因此未读取测试用例定义。
- 后端 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 未配置；8080 无监听进程。无法证明后端连接 `shipflow_http_test`，故没有发送真实 HTTP 请求。
- 第二模块开关、HTTP 根地址、业务库声明和四身份令牌均未配置。

### 本地 Python 单元测试

| 命令环境 | 实际结果 |
|---|---|
| 全局 `D:\python\python.exe` | 失败：未安装 `pytest`；未触发 HTTP。 |
| `api-tests/.venv/Scripts/python.exe` | `32 passed`，1 条 pytest 缓存目录权限警告；未触发 HTTP。 |

本轮修正了模块二身份上下文的秘密泄漏测试断言，使其验证访问令牌不会出现在展示文本中。

### 真实执行统计

| 项目 | 通过 | 失败 | 跳过 | 未执行 | 说明 |
|---|---:|---:|---:|---:|---|
| 最小冒烟链路 | 0 | 0 | 0 | 1 | 后端与身份门禁未满足。 |
| 第二模块 66 条场景 | 0 | 0 | 0 | 66 | 未经冒烟验证，保持不可执行。 |

JUnit XML：未生成。Allure 结果：未生成。残留数据：本轮未发送 HTTP 写请求，未新增测试数据。下一步是按 runbook 第 13.2 节配置并证明后端数据源后，重新进行环境门禁和最小冒烟。

## 2026-08-10 授权后真实 HTTP 自动化执行结果

状态：预检已通过环境、数据库名和健康检查，但现有执行器无法满足清理与 66 场景执行契约，已在首个业务写请求前停止。

| 项目 | 实际结果 |
|---|---|
| 环境、库名与断言库 | 通过：`shipflow_qa` 仅作为定义库；断言库实际选中 `shipflow_http_test` |
| 后端健康检查 | 通过：HTTP 200 / `UP` |
| 后端实际业务库 | 未证明：健康端点不暴露数据源名称，需通过可清理冒烟的定向断言证明 |
| 冒烟业务请求 | 0 执行，0 通过，0 失败，0 跳过 |
| 冻结的 66 条场景 | 0 执行，0 通过，0 失败，0 跳过，66 未执行 |
| 失败用例编号 | 无；未进入业务用例执行 |
| `BLOCKED` 状态 | 0 条修改 |
| 清理与残留数据 | 无清理，无残留；未创建任何业务测试数据 |
| JUnit XML / Allure 结果路径 | 未生成（未运行 pytest） |

阻断原因：现有真实模块二 pytest 入口仅有 20 个静态基线参数，不包含 66 条冻结场景的受控覆盖执行路径；写场景没有 API 清理契约且不能使用推断的 SQL 删除。继续动态认证和冒烟写入会遗留测试数据，与本次授权的清理边界冲突。

## 2026-08-10 恢复基线策略实施记录

- 已增加 `common/module2_reset.py`：仅实现 reset 前的精确门禁，不含任何 DDL/DML、不会连接数据库。
- 门禁要求 `SHIPFLOW_MODULE2_ALLOW_RESET=1`、严格 `shipflow_http_test` JDBC 路径、`SELECT DATABASE()` 精确结果、`CURRENT_USER()` 精确专用 reset 账号以及两个显式 API URL 的全值相等。不使用默认值、字符串替换或模糊匹配。
- 本轮未配置 reset 账号、未设置允许开关、未执行 reset，因此无重置行数、无实例数据变化。
- 本地单元测试：`17 passed` 和 1 条 `.pytest_cache` 目录权限警告；未触发真实 HTTP 或 reset。
## 2026-08-10 最终本地真实接口执行结果

| 项目 | 实际结果 |
|---|---|
| API 目标与数据源一致性 | `SHIPFLOW_MODULE2_BASE_URL` 已从旧的 8080 目标纠正为 `http://localhost:18080`；同一 HTTP Client 健康检查返回 `200/UP`，受控创建返回 `201`，且资源在 `shipflow_http_test` 只读查询中存在。 |
| RBAC 实时状态验证 | `RBAC-012`：`1 passed`；`RBAC-013`：`1 passed`。 |
| 冻结 66 条真实接口用例 | `collected 66`、`passed 66`、`failed 0`、`skipped 0`。 |
| JUnit | `module2-acceptance-artifacts-20260810-221640/junit.xml`；核验 tests `66`、failures `0`、errors `0`、skipped `0`。 |
| Allure 原始结果 | `module2-acceptance-artifacts-20260810-221640/allure-results/`；66 个 result 文件。 |
| 数据库边界 | 仅将 `shipflow_qa` 用于用例定义、将 `shipflow_http_test` 用于业务断言；未连接 `shipflow` 或 `shipflow_test`。 |

本地结果已满足 Jenkins 集成准入。Jenkins 运行仍须由 Credentials 注入平台管理员密码、QA 用例库连接和 HTTP 测试库连接；不得将密码、Token、私钥、Cookie 或 JDBC 完整连接串写入仓库。Jenkins 构建与 Allure 页面发布尚未在本地声称完成，必须以实际 Job 结果为准。
