# 第二模块租户、店铺、用户、RBAC 真实接口自动化操作手册与执行记录

## 1. 当前阶段、目标与数据库边界

当前处于“环境与数据库预检、用户操作手册”阶段。本轮未初始化数据库、未启动后端、未运行 pytest、未执行第二模块 HTTP 自动化、未生成 JUnit 或 Allure，也未修改 `shipflow_qa.api_test_case` 的 `BLOCKED` 状态。

目标是在经过批准的隔离环境中，逐步完成第二模块 66 条用例对 20 个冻结业务接口的真实 HTTP 和业务数据库双断言。`shipflow_qa` 只保存测试用例定义；未来真实 HTTP 自动化的唯一业务数据库是 `shipflow_http_test`。禁止将后端指向或操作 `shipflow`、`shipflow_test`；禁止用 `shipflow_qa` 作为业务夹具、清理或业务断言数据库。

| 数据库 | 职责 | 本轮只读预检结果 |
|---|---|---|
| `shipflow_qa` | `api_test_case` 测试用例定义库 | 已通过当前 QA 只读连接确认存在；66 条第二模块用例已导入且均为 `BLOCKED`。 |
| `shipflow_http_test` | 未来真实 HTTP 自动化的隔离业务库 | 当前终端没有该库的连接配置或可见性，尚未确认是否存在、是否为空或是否完成初始化。 |
| `shipflow` | 业务库，禁止作为本模块测试目标 | 当前 QA 账号的只读可见性中未确认；不得连接或操作。 |
| `shipflow_test` | 非本模块目标库，禁止使用 | 当前 QA 账号的只读可见性中未确认；不得连接或操作。 |

`id` 是 `shipflow_qa.api_test_case` 的数据库自增主键，不要求连续；`case_no` 才是业务测试编号和状态变更的唯一依据。

## 2. 已执行的预检记录

| 项目 | 结果 | 结论 |
|---|---|---|
| 当前终端变量名检查 | 仅发现 `SHIPFLOW_QA_DB_URL`、`SHIPFLOW_QA_DB_USERNAME`、`SHIPFLOW_QA_DB_PASSWORD`，未读取其值 | 只能读取 QA 用例库，不能连接 `shipflow_http_test`。 |
| QA 连接 | 当前数据库为 `shipflow_qa`，当前账号为 `shipflow_qa_writer` | 仅用于用例定义；不能据此推断其他三库存在或可访问。 |
| 受控脚本检查 | 已检查 `schema.sql`、`init_data.sql`、V002、V003、V004、V005 | 见第 5 节；当前仓库文件不能直接安全初始化 `shipflow_http_test`。 |
| 后端配置检查 | `application.yml` 使用 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`；未覆盖端口 | 后端必须在 IDEA 或终端显式将 `DB_URL` 指向 `shipflow_http_test`。 |
| 第二模块运行门禁检查 | `module2_environment.py` 已要求 `SHIPFLOW_MODULE2_RUN`、`SHIPFLOW_MODULE2_ISOLATED_ENV`、`SHIPFLOW_MODULE2_BASE_URL`、`SHIPFLOW_MODULE2_BUSINESS_DB_NAME` | 框架会拒绝非 `shipflow_http_test` 的业务库声明。 |

## 3. 临时环境变量与填写位置

以下变量名来自当前仓库配置或现有模块二框架；示例只说明格式，不能填写真实密码、Token、Cookie、私钥或完整连接串。所有值仅放入当前 Windows 终端会话、IDEA Run Configuration 的 Environment variables 或受控密钥存储，禁止写入仓库文件。

| 变量名 | 用途 | 示例格式 | 填写位置 |
|---|---|---|---|
| `DB_URL` | 后端数据源；必须只指向 `shipflow_http_test` | `jdbc:mysql://<主机>:<端口>/shipflow_http_test?<参数>` | IDEA 后端 Run Configuration 或当前启动终端。 |
| `DB_USERNAME` | 后端隔离业务库账号 | `<隔离业务库账号>` | 同上。 |
| `DB_PASSWORD` | 后端隔离业务库密码 | `<仅在受控密钥存储中注入>` | 同上，不打印。 |
| `SHIPFLOW_MODULE2_RUN` | 第二模块真实运行显式开关 | `1` | 执行 pytest 的当前终端。 |
| `SHIPFLOW_MODULE2_ISOLATED_ENV` | 确认当前环境为隔离环境 | `1` | 执行 pytest 的当前终端。 |
| `SHIPFLOW_MODULE2_BASE_URL` | 隔离后端根地址 | `http://127.0.0.1:8080` | 执行 pytest 的当前终端。 |
| `SHIPFLOW_MODULE2_BUSINESS_DB_NAME` | 框架业务库安全门禁 | `shipflow_http_test` | 执行 pytest 的当前终端。 |
| `SHIPFLOW_MODULE2_PLATFORM_ACCESS_TOKEN` | 平台管理员运行时身份 | `<运行时注入的访问令牌>` | 仅受控密钥存储或当前终端。 |
| `SHIPFLOW_MODULE2_TENANT_A_ACCESS_TOKEN` | 租户 A 管理员运行时身份 | `<运行时注入的访问令牌>` | 同上。 |
| `SHIPFLOW_MODULE2_TENANT_B_ACCESS_TOKEN` | 租户 B 管理员运行时身份 | `<运行时注入的访问令牌>` | 同上。 |
| `SHIPFLOW_MODULE2_NO_PERMISSION_ACCESS_TOKEN` | 低权限身份 | `<运行时注入的访问令牌>` | 同上。 |
| `SHIPFLOW_MODULE2_ASSERT_DB_HOST` | 未来只读业务断言数据库主机 | `<主机名或地址>` | 仅执行数据库断言的当前终端。 |
| `SHIPFLOW_MODULE2_ASSERT_DB_PORT` | 未来只读业务断言数据库端口 | `<端口>` | 同上。 |
| `SHIPFLOW_MODULE2_ASSERT_DB_USERNAME` | 未来只读断言账号 | `<只读账号>` | 同上。 |
| `SHIPFLOW_MODULE2_ASSERT_DB_PASSWORD` | 未来只读断言密码 | `<仅在受控密钥存储中注入>` | 同上，不打印。 |
| `SHIPFLOW_MODULE2_RESET_APPROVED` | 允许已批准 reset 的显式门禁 | `1` | 仅在 reset 责任人书面批准后填写。 |
| `SHIPFLOW_MODULE2_RESET_OWNER` | reset 责任人标识 | `<责任人别名>` | 同上。 |
| `SHIPFLOW_ENV`、`SHIPFLOW_API_BASE_URL` | 现有通用 API 测试配置 | `ci`、`http://127.0.0.1:8080` | 仅在运行既有通用框架时填写。 |
| `SHIPFLOW_QA_DB_URL`、`SHIPFLOW_QA_DB_USERNAME`、`SHIPFLOW_QA_DB_PASSWORD` | 用例定义库连接 | 已由当前终端提供 | 仅用于 `shipflow_qa`，不得改作业务库连接。 |

仓库当前**未定义** `shipflow_http_test` 初始化专用的环境变量名；在未获负责人批准前，不自行发明、提交或固化变量名。初始化连接信息应由隔离环境责任人通过临时 MySQL 客户端配置提供。

## 4. Windows 终端与 IDEA 操作步骤

### 4.1 第一步：只读确认四个数据库

由具备**只读元数据权限**的隔离环境账号在 Windows PowerShell 执行；`-p` 只在本地提示输入密码，禁止把密码放入命令行。

```powershell
mysql.exe --protocol=TCP --host=<主机> --port=<端口> --user=<只读账号> -p --batch --skip-column-names -e "SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('shipflow_qa','shipflow_http_test','shipflow','shipflow_test') ORDER BY schema_name;"
```

预期结果：四个名称的可见性被分别记录；只把 `shipflow_http_test` 作为后续目标。若缺少 `shipflow_http_test`、账号看不到该库，或查询权限不明，停止并记录，不执行初始化。

### 4.2 第二步：只读检查 `shipflow_http_test`

```powershell
mysql.exe --protocol=TCP --host=<主机> --port=<端口> --user=<只读账号> -p --database=shipflow_http_test --batch --skip-column-names -e "SELECT DATABASE(); SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'; SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name;"

mysql.exe --protocol=TCP --host=<主机> --port=<端口> --user=<只读账号> -p --database=shipflow_http_test --batch --skip-column-names -e "SELECT (SELECT COUNT(*) FROM tenant) AS tenant_count, (SELECT COUNT(*) FROM sys_user) AS user_count, (SELECT COUNT(*) FROM sys_role) AS role_count, (SELECT COUNT(*) FROM sys_permission) AS permission_count;"
```

预期结果：第一条命令证明当前库严格为 `shipflow_http_test` 并列出表；第二条只有在四张基础表均存在时才执行。若库非空、表结构不完整、已有基础数据、迁移状态不明或任一查询失败，停止并将结果写入本节“实际执行记录”。

### 4.3 第三步：后端启动

在 IDEA 的 `shipflow-backend` Run Configuration 中设置第 3 节列出的 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。`DB_URL` 必须包含数据库名 `shipflow_http_test`。当前 `application.yml` 未显式设置服务端口，因此 Spring Boot 默认端口预期为 `8080`；若 Run Configuration 另设 `SERVER_PORT`，必须同步修改后续健康检查地址并记录。

```powershell
Set-Location D:\Projects\shipflow\backend
mvn spring-boot:run

curl.exe --fail --silent --show-error http://127.0.0.1:8080/actuator/health
```

预期结果：健康检查返回 HTTP 200 且 JSON 中 `status` 为 `UP`。失败时优先检查后端日志中的数据源名称、端口占用、迁移状态、认证密钥配置和数据库连通性；不得把 `DB_URL` 改为 `shipflow` 作为临时绕过。

### 4.4 第四步：pytest、JUnit 与 Allure（仅准入后）

```powershell
Set-Location D:\Projects\shipflow\api-tests
$env:SHIPFLOW_MODULE2_RUN='1'
$env:SHIPFLOW_MODULE2_ISOLATED_ENV='1'
$env:SHIPFLOW_MODULE2_BASE_URL='http://127.0.0.1:8080'
$env:SHIPFLOW_MODULE2_BUSINESS_DB_NAME='shipflow_http_test'
# 其余四个身份变量和只读断言变量由受控密钥存储注入，不在此处打印。

pytest -q tests/module2 --junitxml=reports/module2-junit.xml --alluredir=reports/module2-allure
allure serve reports/module2-allure
```

预期结果：先仅运行获批的冒烟 case_no，再运行完整 66 条；JUnit 文件与 Allure 结果目录生成。若出现跳过、失败或缺少变量，只记录 case_no、状态、错误码、Trace ID、RUN_ID 和脱敏信息，不能报告为通过。当前尚未满足本步骤准入条件，因此不得执行。

## 5. `shipflow_http_test` 初始化审查与阻塞

仓库中受控文件的逻辑顺序为：`database/schema.sql` → `database/init_data.sql` → `database/migrations/V002__add_api_support_tables.sql` → `V003__repair_v002_comments.sql` → `V004__add_tenant_management_permissions.sql` → `V005__add_tenant_rbac_permissions.sql`。

但是当前文件不能直接执行到 `shipflow_http_test`：

| 文件 | 已检查事实 | 当前决定 |
|---|---|---|
| `database/schema.sql` | 含 `CREATE DATABASE IF NOT EXISTS shipflow`、`USE shipflow` 及多条 `DROP TABLE` | 禁止执行。 |
| `database/init_data.sql` | 依赖已被 `USE shipflow` 选中的基础表 | 不得单独猜测目标库后执行。 |
| `V002__add_api_support_tables.sql` | 含 `USE shipflow` | 禁止执行。 |
| `V003__repair_v002_comments.sql` | 含 `USE shipflow` 和 DDL | 禁止执行。 |
| V004、V005 | 基础权限数据脚本；未见目标库选择语句 | 仅能在已证明目标为 `shipflow_http_test` 且前序结构完成后由批准流程执行。 |

因此，当前**初始化被阻塞**：需要负责人提供经审查、明确只作用于 `shipflow_http_test` 的初始化方案，或明确授权创建并审查安全的隔离库专用副本。不得通过替换命令行参数、执行 `USE shipflow`、连接 `shipflow`、删除现有数据或猜测迁移状态绕过此阻塞。

未来获批初始化后，每个文件必须分别记录：执行文件、目标数据库确认结果、执行前后表数量、tenant/sys_user/sys_role/sys_permission 关键基础数据数量、MySQL 受影响行数、失败信息及回滚/reset 责任人。重置只能使用已批准的 `shipflow_http_test` 专用 reset 方案；当前不存在可执行的仓库内 reset 命令。

## 6. 后续实施顺序与准入

1. 由隔离环境责任人完成第 4.1、4.2 节只读确认，并提供 `shipflow_http_test` 的空库/结构/基础数据证据。
2. 获得 `shipflow_http_test` 专用初始化和 reset 方案后，才可进入受控初始化。
3. 后端必须以 `DB_URL` 指向 `shipflow_http_test` 启动，并以 `/actuator/health` 验证。
4. 先补充 Python 框架单元测试，再验证四身份、动态租户/店铺/用户/角色/权限、唯一前缀、幂等键、版本、跨租户资源、setup、teardown、失败恢复和只读数据库断言。
5. 清理与 reset 经演练后，先执行冒烟用例，再执行完整 66 条；生成 JUnit 和 Allure。
6. 只有对应真实 HTTP 和数据库断言通过后，才可用单独、精确 case_no 的 SQL 将获批用例从 `BLOCKED` 更新为可执行状态。

## 7. 实际执行记录与未完成项

| 时间 | 操作 | 结果 |
|---|---|---|
| 本轮 | 环境变量名称、后端配置、模块二运行门禁、受控 SQL 文件只读检查 | 完成；未读取秘密值。 |
| 本轮 | 四库元数据确认 | 仅确认 `shipflow_qa`；其余三库因无隔离环境只读连接配置而未确认。 |
| 本轮 | `shipflow_http_test` 空库、表结构和基础数据检查 | 未执行；缺少连接配置。 |
| 本轮 | 初始化、后端启动、pytest、HTTP、JUnit、Allure | 未执行。 |
| 本轮 | `git diff --check` | 退出码 0；无空白错误。终端仅报告工作区既有文件的换行符警告。 |

未完成项：`shipflow_http_test` 专用只读/初始化连接、空库与迁移状态证据、安全初始化与 reset 方案、四身份最小权限与运行时凭据注入、CSRF 写合同、只读数据库断言账号、真实 HTTP 冒烟与完整回归。以上任一项缺失时，66 条用例继续保持 `BLOCKED`。

## 8. 2026-08-10 测试库受控初始化准备（仅只读）

### 实际检查结果

本轮从 `SHIPFLOW_HTTP_TEST_DB_URL` 解析的目标为数据库 `shipflow_http_test`、连接主机 `172.29.128.47`、连接端口 `3307`；环境变量中的账号和密码均已提供，但未打印密码或完整连接串。以该账号选择目标库时，MySQL 返回 `ERROR 1044`（无该库访问权限）。不选库的只读连接确认：服务器主机名为 `9b244d2d5246`、MySQL 服务端端口为 `3306`、当前连接账号为 `shipflow_qa_writer@172.29.0.1`、认证账号为 `shipflow_qa_writer@%`；授权仅覆盖 `shipflow_qa.api_test_case` 的 `SELECT`、`INSERT`、`UPDATE`。

因此，目标库对当前账号既不可见也不可访问，无法合法确认其是否存在、表数量、关键业务表、基础用户/角色/权限、`flyway_schema_history` 或残留数据。该结果是“权限不足，数据库状态未知”，不是“空库”。本轮未执行任何数据库写操作，未连接或修改 `shipflow_qa`，未启动后端、未运行 pytest、未提交 Git。

### 可评审初始化方案

新增 `database/http-test-control/`，不修改原始 `schema.sql`、`init_data.sql` 或 V002 至 V005。批准后只可在连接参数已选择 `shipflow_http_test` 的前提下依次执行：

1. `01_schema_empty_target.sql`：仅适用于基表数为 0 的目标；移除了 `CREATE DATABASE shipflow`、`USE shipflow`，并把原始全部 `DROP TABLE` 隔离为注释。
2. `02_init_data.sql`：导入基础租户、用户、角色和权限种子数据。
3. `03_V002__add_api_support_tables.sql`：移除 `USE shipflow`；当前基线已含两张表，使用 `IF NOT EXISTS` 保留来源并避免重复建表失败。
4. `04_V003__repair_v002_comments.sql`、`05_V004__add_tenant_management_permissions.sql`、`06_V005__add_tenant_rbac_permissions.sql`：均不含数据库切换语句，按原业务顺序执行。

每个文件执行后都使用连接已选定目标库的客户端验证，禁止 `--force`。建议验证 SQL：

```sql
SELECT DATABASE(), COUNT(*) AS base_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';

SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('tenant','sys_user','sys_role','sys_permission','sys_user_role','sys_role_permission','api_idempotency_record','auth_refresh_session','flyway_schema_history')
ORDER BY table_name;

SELECT
  (SELECT COUNT(*) FROM tenant) AS tenant_count,
  (SELECT COUNT(*) FROM sys_user) AS user_count,
  (SELECT COUNT(*) FROM sys_role) AS role_count,
  (SELECT COUNT(*) FROM sys_permission) AS permission_count;
```

### 回滚与重置条件

初始化任一步失败、目标基表数非 0、发现业务残留、存在不明 `flyway_schema_history` 记录、`SELECT DATABASE()` 非 `shipflow_http_test`、或执行账号权限不足时，立即停止，不在该库执行补救 DDL/DML。重置仅使用单独的 `99_RESET_DESTRUCTIVE__APPROVAL_REQUIRED.sql.disabled`：它标注破坏性操作、保持 `.disabled` 扩展名且首条语句强制中止，默认不可执行。只有负责人对这一具体文件给出明确批准、复核目标库并保留可审查备份后，才可由授权执行者处理。

### 后续由负责人执行的命令

先申请或改用仅限 `shipflow_http_test` 的受控账号（需要目标库 `SELECT`、建表及种子数据所需权限），然后在 PowerShell 中仅作只读预检：

```powershell
$env:MYSQL_PWD = $env:SHIPFLOW_HTTP_TEST_DB_PASSWORD
mysql.exe --protocol=TCP --host=172.29.128.47 --port=3307 --user=$env:SHIPFLOW_HTTP_TEST_DB_USERNAME --database=shipflow_http_test --batch --skip-column-names -e "SELECT DATABASE(); SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE';"
Remove-Item Env:MYSQL_PWD
```

预检证明空库且审批完成后，逐个执行 `database/http-test-control/01_schema_empty_target.sql` 至 `06_V005__add_tenant_rbac_permissions.sql`；每个文件前后执行本节验证 SQL。重置文件不在初始化批准范围内，必须另行对 `99_RESET_DESTRUCTIVE__APPROVAL_REQUIRED.sql.disabled` 明确批准。

### 本轮学习笔记

测试库 URL 指向正确并不等于账号可访问目标库；必须以 `SELECT DATABASE()`、目标库元数据查询和 `SHOW GRANTS` 的实测结果作为初始化准入证据。当前 `schema.sql` 是可重建基线而非可直接重放脚本：其内置建库、切库和删表语句必须在测试库专用副本中移除或隔离，且不能据此推断现有目标库为空。初始化批准与破坏性重置批准必须是两个独立的、可追溯的授权。

## 9. 2026-08-10 已授权初始化的执行记录

### 执行前只读确认

从当前 `SHIPFLOW_HTTP_TEST_DB_URL` 解析的数据库名为 `shipflow_http_test`，因此只尝试通过该目标库建立只读预检连接；未连接、切换或操作 `shipflow`、`shipflow_test`、`shipflow_qa`。MySQL 在选择目标库阶段返回 `ERROR 1044 (42000)`：当前配置账号没有 `shipflow_http_test` 访问权限。

该失败使以下强制准入条件均不能成立：无法确认 `SELECT DATABASE()` 的实际值；无法确认账号仅在目标库范围拥有初始化所需权限；无法统计目标库的表、业务数据、基础数据或 `flyway_schema_history`，故也无法判断为空库或数据来源明确。依照失败即停止规则，本轮没有执行任何初始化脚本，没有执行 reset 脚本，没有启动后端、没有运行 pytest，也没有修改 `shipflow_qa` 测试用例状态。

### 脚本执行结果

| 文件 | 结果 | 表数量/关键基础数据/迁移结果 |
|---|---|---|
| `01_schema_empty_target.sql` | 未执行：预检失败 | 未读取 |
| `02_init_data.sql` | 未执行：前置预检失败 | 未读取 |
| `03_V002__add_api_support_tables.sql` | 未执行：前置预检失败 | 未读取 |
| `04_V003__repair_v002_comments.sql` | 未执行：前置预检失败 | 未读取 |
| `05_V004__add_tenant_management_permissions.sql` | 未执行：前置预检失败 | 未读取 |
| `06_V005__add_tenant_rbac_permissions.sql` | 未执行：前置预检失败 | 未读取 |
| `99_RESET_DESTRUCTIVE__APPROVAL_REQUIRED.sql.disabled` | 未执行，且不在本次授权范围 | 不适用 |

`tenant`、`sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`flyway_schema_history` 以及目标库中不应存在的 `api_test_case` 均未验证；原因是目标库访问被拒绝。恢复条件是：更新受控连接账号的目标库授权后，先重新执行第 8 节的只读预检；只有确认目标库为空、账号权限范围仅限该库且满足初始化所需权限后，才可重新申请执行本节列出的六个初始化文件。

## 10. 2026-08-10 runner 账号只读预检记录

本轮仅使用 `SHIPFLOW_HTTP_TEST_DB_URL`、`SHIPFLOW_HTTP_TEST_DB_USERNAME` 和运行时密码建立 MySQL 只读连接；未打印密码或完整连接串，未执行初始化 SQL、DML、DDL、后端启动或 pytest。

| 检查项 | 实测结果 | 结论 |
|---|---|---|
| 连接身份与服务器 | 连接身份 `shipflow_http_test_runner@172.29.0.1`；认证身份 `shipflow_http_test_runner@%`；服务器 `9b244d2d5246:3306` | 已实际连通受控 runner 账号。 |
| 实际数据库名 | `SELECT DATABASE()` 返回 `shipflow_http_test` | 严格命中唯一允许的业务测试库；未连接 `shipflow`、`shipflow_test` 或 `shipflow_qa`。 |
| 基表数量与残留数据 | `information_schema.tables` 中目标库 `BASE TABLE` 数量为 `0`，未返回表名 | 空库；不存在业务表或基础数据残留。 |
| 基础数据 | `tenant`、`sys_user`、`sys_role`、`sys_permission` 均尚未建表，故不存在可计数的种子数据 | 符合在 `02_init_data.sql` 前的空库条件。 |
| 迁移状态 | 不存在 `flyway_schema_history`（目标库无任何基表） | 不存在不明的 Flyway 执行历史。 |
| runner 授权范围 | `USAGE ON *.*`；`SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, ALTER ON shipflow_http_test.*` | 权限限定在目标库，且覆盖 01--06 的建表、索引/结构调整和种子写入需要。 |

### 初始化准入结论

只读检查通过。结合 `database/http-test-control/` 的受控副本（不创建或切换数据库，且 `01` 中的原始 `DROP TABLE` 已隔离为注释），现在可以安全地按顺序执行 `01_schema_empty_target.sql` 至 `06_V005__add_tenant_rbac_permissions.sql`。每个脚本仍必须单独执行、禁止 `--force`，并在每一步后重新确认目标库、表数量、关键基础数据和迁移状态；`99_RESET_DESTRUCTIVE__APPROVAL_REQUIRED.sql.disabled` 不在本次许可范围内。

## 11. 2026-08-10 受控初始化执行记录

已在执行前重新通过受控 runner 只读确认 `SELECT DATABASE()` 为 `shipflow_http_test`、基表数为 `0`。本节的 SQL 执行严格使用该目标库，不使用 `--force`，且不连接、切换或操作 `shipflow`、`shipflow_test`、`shipflow_qa`。

| 顺序 | 文件 | 执行结果 | 紧随其后的只读核验 |
|---|---|---|---|
| 01 | `01_schema_empty_target.sql` | 失败，停止。MySQL 客户端启动器未将运行时密码传递给子进程，认证阶段返回 `ERROR 1045 (28000)`，SQL 未被服务器执行。 | 目标库仍为 `shipflow_http_test`；基表数 `0`；关键表数 `0`；`flyway_schema_history` 数量 `0`。 |
| 02 | `02_init_data.sql` | 未执行：第 01 步失败后立即停止。 | 不适用。 |
| 03 | `03_V002__add_api_support_tables.sql` | 未执行：第 01 步失败后立即停止。 | 不适用。 |
| 04 | `04_V003__repair_v002_comments.sql` | 未执行：第 01 步失败后立即停止。 | 不适用。 |
| 05 | `05_V004__add_tenant_management_permissions.sql` | 未执行：第 01 步失败后立即停止。 | 不适用。 |
| 06 | `06_V005__add_tenant_rbac_permissions.sql` | 未执行：第 01 步失败后立即停止。 | 不适用。 |
| reset | `99_RESET_DESTRUCTIVE__APPROVAL_REQUIRED.sql.disabled` | 未执行；不在授权范围内。 | 不适用。 |

### 最终数据库状态与恢复条件

数据库保持空库：没有业务表、基础用户、角色、权限或迁移历史。未启动后端、pytest 或 Jenkins，未提交或推送 Git。此次停止原因是本地执行器的认证参数传递缺陷，不是目标库权限或 SQL 内容错误；在获得新的明确授权后，应先修复执行器的密码继承方式、重新做空库只读门禁，再从 `01` 重新开始，绝不跳过或直接执行后续脚本。

## 12. 2026-08-10 经重新授权后的执行结果（最终）

在负责人重新授权后，已重新确认目标连接严格选择 `shipflow_http_test` 且起始基表数为 `0`，随后从 `01_schema_empty_target.sql` 重试。此轮未连接、切换或操作 `shipflow`、`shipflow_test`、`shipflow_qa`，未使用 `--force`，也未执行 reset、后端、pytest 或 Jenkins。

| 顺序 | 文件 | 执行结果 | 执行后立即只读核验 |
|---|---|---|---|
| 01 | `01_schema_empty_target.sql` | 失败，停止。MySQL 子进程的标准输入在脚本传输中提前关闭，无法取得可归因的服务器错误文本；不得假定脚本已原子回滚。 | 目标库为 `shipflow_http_test`；基表数为 `2`：`tenant`、`sys_permission`；两表记录数均为 `0`；`sys_user`、`sys_role`、关联表和 API 支撑表均不存在；`flyway_schema_history` 不存在。 |
| 02 | `02_init_data.sql` | 未执行：第 01 步失败后立即停止。 | 未创建基础用户、角色或额外权限数据。 |
| 03 | `03_V002__add_api_support_tables.sql` | 未执行：第 01 步失败后立即停止。 | API 幂等与刷新会话表不存在。 |
| 04 | `04_V003__repair_v002_comments.sql` | 未执行：第 01 步失败后立即停止。 | 无可核验的 V003 注释调整。 |
| 05 | `05_V004__add_tenant_management_permissions.sql` | 未执行：第 01 步失败后立即停止。 | 不存在平台租户管理权限或角色绑定。 |
| 06 | `06_V005__add_tenant_rbac_permissions.sql` | 未执行：第 01 步失败后立即停止。 | 不存在 V005 权限字典或角色绑定。 |
| reset | `99_RESET_DESTRUCTIVE__APPROVAL_REQUIRED.sql.disabled` | 未执行；明确禁止。 | 不适用。 |

### 最终阻塞结论

目标库已不再为空，因而不满足 `01_schema_empty_target.sql` 的空库准入条件，不能安全重试 `01`，也不能跳过前序步骤执行 `02` 至 `06`。当前没有用户、角色、权限种子或 Flyway 历史，但存在部分结构，不能将其视为可用初始化结果。恢复需要负责人对隔离库的处置方案作出新的明确授权：要么提供经审查的、仅针对 `shipflow_http_test` 的恢复/清理方案，要么另行提供新的空隔离库；在此之前禁止任何补救 DDL/DML。

## 13. 第二模块真实接口自动化实施（2026-08-10）

本阶段已获准开始真实接口自动化实施。新的运行前提是：`shipflow_http_test` 已由环境负责人完成初始化并具备完整表结构和基础数据；后端业务库只能为该库，`shipflow_qa` 只用于读取测试用例定义。以下环境门禁、框架实现、冒烟和 66 条场景的结果均以本节后续实际检查为准；在后端连接目标无法证明前，不发送任何真实 HTTP 写请求。

### 13.1 实测环境变量与数据库门禁

下表基于当前代码和当前 Codex 终端的变量名检查；只记录“已配置/未配置”，不读取或展示秘密值。`API_BASE_URL` 不是当前仓库读取的变量名：既有通用框架读取 `SHIPFLOW_API_BASE_URL`，第二模块客户端读取 `SHIPFLOW_MODULE2_BASE_URL`，两者不能互相替代。

| 变量 | 用途与示例格式 | 配置位置 | 当前状态 |
|---|---|---|---|
| `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` | 后端数据源；`jdbc:mysql://<主机>:<端口>/shipflow_http_test?<参数>`、账号、受控密码 | IDEA 后端 Run Configuration | 未配置；后端不得启动到其他库。 |
| `SHIPFLOW_JWT_*`、`SHIPFLOW_REFRESH_TOKEN_*` | 仅当后端显式启用 JWT/刷新令牌时所需的密钥位置、HMAC 与开关 | IDEA 后端 Run Configuration 或受控密钥存储 | 不在本轮读取；禁止写入仓库或报告。 |
| `SHIPFLOW_QA_DB_HOST`、`SHIPFLOW_QA_DB_PORT`、`SHIPFLOW_QA_DB_NAME`、`SHIPFLOW_QA_DB_USERNAME`、`SHIPFLOW_QA_DB_PASSWORD` | 仅读取 `shipflow_qa` 用例定义；示例库名 `shipflow_qa` | Codex 测试终端 | 主机、端口、库名未配置；用户名和密码变量已存在。 |
| `SHIPFLOW_HTTP_TEST_DB_URL`、`SHIPFLOW_HTTP_TEST_DB_USERNAME`、`SHIPFLOW_HTTP_TEST_DB_PASSWORD` | 测试后的只读数据库断言；URL 必须指向 `shipflow_http_test` | Codex 测试终端 | 已配置；只读实测目标库为 `shipflow_http_test`，有 30 张基表，基础数据为租户 2、用户 10、角色 10、权限 19；无 `flyway_schema_history`。 |
| `SHIPFLOW_MODULE2_RUN`、`SHIPFLOW_MODULE2_ISOLATED_ENV`、`SHIPFLOW_MODULE2_BUSINESS_DB_NAME` | 第二模块显式运行与隔离库门禁；示例 `1`、`1`、`shipflow_http_test` | Codex 测试终端 | 未配置。 |
| `SHIPFLOW_MODULE2_BASE_URL` | 第二模块实际读取的 HTTP 根地址；示例 `http://127.0.0.1:8080` | Codex 测试终端 | 未配置。 |
| `SHIPFLOW_API_BASE_URL` | 既有通用 API 测试配置的根地址；示例 `http://127.0.0.1:8080` | Codex 测试终端 | 未配置。 |
| `API_BASE_URL` | 用户约定名称；当前代码不读取，不能作为运行门禁替代项 | 如需保留，仅作人工说明 | 不适用。 |
| 四个 `SHIPFLOW_MODULE2_*_ACCESS_TOKEN` | 平台管理员、租户 A 管理员、租户 B 管理员、低权限身份的现有模块二运行时令牌 | Codex 测试终端或受控密钥存储 | 均未配置。 |

当前 8080 没有监听进程，且后端 `DB_*` 未注入；因此无法证明后端连接 `shipflow_http_test`，真实 HTTP、冒烟、66 条场景、JUnit 和 Allure 全部保持停止。

### 13.2 可复制的负责人操作与启动顺序

1. 在 IDEA 的后端 Run Configuration 注入 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，并确认 URL 的数据库段严格为 `shipflow_http_test`；如启用 JWT/刷新令牌，同时从受控密钥存储注入相应安全变量。
2. 启动后端后，以非敏感的配置证明或受控启动日志确认其数据源仅为 `shipflow_http_test`；再验证 `http://127.0.0.1:8080/actuator/health` 返回 HTTP 200 和 `UP`。端口已有实例时，先确认其数据源归属，不得复用来源不明的实例。
3. 在 Codex 测试终端注入完整 `SHIPFLOW_QA_DB_*`（仅 `shipflow_qa`）、完整 `SHIPFLOW_HTTP_TEST_DB_*`（仅 `shipflow_http_test`）、`SHIPFLOW_MODULE2_RUN=1`、`SHIPFLOW_MODULE2_ISOLATED_ENV=1`、`SHIPFLOW_MODULE2_BUSINESS_DB_NAME=shipflow_http_test`、`SHIPFLOW_MODULE2_BASE_URL=<已验证后端地址>`，以及四个受控身份令牌。不要在命令行、YAML、报告或聊天中粘贴秘密值。
4. 先复核环境门禁与健康检查，再执行最小冒烟链路；只有冒烟的 HTTP 和数据库断言均通过，才可解除对应 66 条用例的 `BLOCKED` 状态并运行完整套件。

## 14. 2026-08-10 本次获授权后的实际预检记录

本次只在当前 Codex 执行会话中读取环境变量名、是否存在与 JDBC URL 中的数据库名；未输出密码、Token、Cookie、私钥或完整连接串，未连接、读取或写入 `shipflow`、`shipflow_test`、`shipflow_qa`。

| 预检项 | 实际结果 | 结论 |
|---|---|---|
| 必要环境变量存在性 | QA 库三项、HTTP 测试库三项、模块二运行开关、隔离开关、BASE_URL 与业务库名均存在；但四个身份 Token 变量均缺失 | 失败，不满足可执行身份前置 |
| `SHIPFLOW_QA_DB_URL` 目标 | 只解析数据库名：`shipflow_qa` | 通过 |
| `SHIPFLOW_HTTP_TEST_DB_URL` 目标 | 只解析数据库名：`shipflow_http_test` | 通过 |
| `SHIPFLOW_MODULE2_BUSINESS_DB_NAME` | `shipflow_http_test` | 通过 |
| 后端实际目标、`/actuator/health`、业务库只读检查 | 未执行 | 因首项失败而立即停止，不得跳过门禁 |

缺失的变量：`SHIPFLOW_MODULE2_PLATFORM_ACCESS_TOKEN`、`SHIPFLOW_MODULE2_TENANT_A_ACCESS_TOKEN`、`SHIPFLOW_MODULE2_TENANT_B_ACCESS_TOKEN`、`SHIPFLOW_MODULE2_NO_PERMISSION_ACCESS_TOKEN`。请仅将它们注入执行 pytest 的同一个 Codex 终端会话，不要在聊天或仓库中提供其值。本次没有发送任何 HTTP 请求，没有产生测试数据，也没有修改第二模块 66 条用例的源 SQL 或执行状态。

## 15. 运行时动态身份认证（2026-08-10）

第二模块已取消对以下四个人工 Token 环境变量的依赖：`SHIPFLOW_MODULE2_PLATFORM_ACCESS_TOKEN`、`SHIPFLOW_MODULE2_TENANT_A_ACCESS_TOKEN`、`SHIPFLOW_MODULE2_TENANT_B_ACCESS_TOKEN`、`SHIPFLOW_MODULE2_NO_PERMISSION_ACCESS_TOKEN`。不得配置、长期保留或写入这些 Token。

### 15.1 用户仅需配置的长期输入

| 分类 | 变量 | 用途 |
|---|---|---|
| 平台管理员凭据 | `SHIPFLOW_PLATFORM_TEST_PASSWORD` | 唯一的登录密码；必须从受控密钥注入 |
| 平台管理员账号 | `SHIPFLOW_MODULE2_PLATFORM_USERNAME` | 可选；默认 `platform_admin` |
| 环境门禁 | `SHIPFLOW_MODULE2_RUN=1`、`SHIPFLOW_MODULE2_ISOLATED_ENV=1`、`SHIPFLOW_MODULE2_BASE_URL`、`SHIPFLOW_MODULE2_BUSINESS_DB_NAME=shipflow_http_test` | 启用隔离真实运行并指定 API 地址 |
| 数据库门禁与断言 | `SHIPFLOW_QA_DB_*`、`SHIPFLOW_HTTP_TEST_DB_*` | QA URL 只能指向 `shipflow_qa`；业务断言 URL 只能指向 `shipflow_http_test` |

请在同一个 Codex 运行会话中通过受控密钥注入 `SHIPFLOW_PLATFORM_TEST_PASSWORD`，不要把其值写入 PowerShell 历史、Markdown、日志、SQL、Git 或环境文件。注入后可复制执行下列无敏感检查：

```powershell
Test-Path Env:SHIPFLOW_PLATFORM_TEST_PASSWORD
$env:SHIPFLOW_MODULE2_BUSINESS_DB_NAME
$env:SHIPFLOW_MODULE2_BASE_URL
```

### 15.2 四类身份的运行时流程

1. 以受控平台管理员密码获取 CSRF，登录后将 Access Token、Refresh Cookie 与 CSRF 只保存在当前 Python 进程的 `IdentityContext`。
2. 使用该会话以唯一运行前缀创建租户 A、B，创建请求中生成两个初始管理员账号和临时密码。
3. A、B 管理员使用各自的 CSRF 与独立认证客户端登录，不共享 Refresh Cookie。
4. 框架从租户 A 的角色列表选择一个已启用且 `permissionIds=[]` 的角色，创建并登录低权限用户。后端不存在此类角色时必须立即停止，不得用管理员会话伪造 403 用例。
5. 刷新令牌时只使用该身份自身的 CSRF 与 Refresh Cookie，在原内存上下文中更换两者，不写入任何外部介质。

### 15.3 本次单元测试与重新预检

已使用 `api-tests/.venv/Scripts/python.exe` 执行模块二环境、运行时会话、客户端与数据库断言单元测试：`31 passed`。pytest 仅报出 `.pytest_cache` 目录权限警告，未触发 HTTP。
随后重新进行环境预检：QA URL 解析为 `shipflow_qa`，HTTP 测试 URL 与模块二业务库声明均为 `shipflow_http_test`；但 `SHIPFLOW_PLATFORM_TEST_PASSWORD` 缺失。按照失败即停止规则，未调用 `/actuator/health`、未连接数据库、未发送 HTTP 冒烟请求。

## 16. 2026-08-10 授权后真实 HTTP 执行门禁复核

本次受控预检确认：必需终端变量均存在；用例定义 URL 为 `shipflow_qa`；HTTP 断言 URL 与模块二业务库声明均为 `shipflow_http_test`；`/actuator/health` 返回 HTTP 200 / `UP`；仅对断言库执行的只读 `SELECT DATABASE()` 返回 `shipflow_http_test`。未输出任何秘密，未连接、读取或写入 `shipflow`、`shipflow_test`。

执行器阻断：`api-tests/tests/module2/test_module2_api.py` 只有 20 个静态基线入口，不是冻结的 66 条数据驱动场景。所有写场景会被 `skip_if_static_case_needs_unavailable_teardown` 跳过；仓库没有租户、店铺或用户的公开删除 API，现有 runtime 也明确不会推断 SQL 清理。若发送动态建立冒烟写请求，将产生无法按“仅清理本次数据”契约清理的残留。因此本次在发送任何业务 HTTP 写请求前立即停止，未更改 `shipflow_qa` 中的 `BLOCKED` 状态。

## 17. 隔离测试库恢复基线专用账号与环境配置（2026-08-10）

恢复只能在**每次完整测试运行前**执行；任何测试失败后绝不自动恢复，必须保留失败现场。不得对 `shipflow`、`shipflow_test` 或 `shipflow_qa` 执行恢复、清理或测试数据写入。

### 17.1 专用账号最小权限

由 MySQL 管理员在仓库外部、只在需要的实例上执行；`<reset-user>`、`<allowed-host>` 和密码不能写入仓库、聊天、报告或命令行历史。

```sql
CREATE USER '<reset-user>'@'<allowed-host>' IDENTIFIED BY '<secret-in-vault>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX
  ON shipflow_http_test.* TO '<reset-user>'@'<allowed-host>';
```

不授予 `GRANT OPTION`、全局权限、其他 schema 权限或对 `mysql.*`的权限。重置器执行前必须以 `CURRENT_USER()` 精确匹配下文的专用账号标识；不接受通配、前缀、包含关系或默认账号。

### 17.2 仅用于当前 PowerShell 会话的变量名

| 变量 | 用途 |
|---|---|
| `SHIPFLOW_MODULE2_ALLOW_RESET` | 必须为精确值 `1`；未设置或其他值一律拒绝。 |
| `SHIPFLOW_MODULE2_RESET_DB_URL` | reset 专用 JDBC URL，路径段必须精确为 `shipflow_http_test`。 |
| `SHIPFLOW_MODULE2_RESET_DB_USERNAME` | 用于登录的 reset 用户名。 |
| `SHIPFLOW_MODULE2_RESET_DB_PASSWORD` | 仅在当前进程内读取。 |
| `SHIPFLOW_MODULE2_RESET_DB_ACCOUNT` | `CURRENT_USER()` 必须精确等于此密钥管理的账号标识（如 `name@host-pattern`）。 |
| `SHIPFLOW_MODULE2_ISOLATED_BASE_URL` | 环境负责人显式批准的隔离后端绝对 URL；必须与 `SHIPFLOW_MODULE2_BASE_URL` 全值相等。 |

### 17.3 可复制的受控配置步骤（今日不执行 reset）

1. 在受控密钥存储中创建上述专用账号，记录 `CURRENT_USER()` 的精确标识。
2. 只向即将运行的 PowerShell 会话注入 17.2 中的六个变量；不输出值。
3. 以非敏感方式检查变量存在性，并确认 `SHIPFLOW_MODULE2_BASE_URL` 与 `SHIPFLOW_MODULE2_ISOLATED_BASE_URL` 精确一致。
4. 仅在项目负责人再次确认后，设置 `SHIPFLOW_MODULE2_ALLOW_RESET=1`，再由重置器依次验证开关、JDBC 目标库、`SELECT DATABASE()`、`CURRENT_USER()` 和两个 API URL。
5. 只有全部通过才能恢复 `database/http-test-control/01` 至 `06` 的已审查基线。任一检查失败立即停止，不执行任何 DDL/DML。

### 17.4 当前 Codex 终端只读环境预检（2026-08-10）

本节仅检查变量是否存在，未读取或输出其值，未连接数据库、未执行 reset、未运行 pytest。

| 分类 | 真实缺失的变量 | 用途 |
|---|---|---|
| 后端运行变量 | `DB_URL` | 后端数据源地址；启动时必须显式指向隔离库。 |
| 后端运行变量 | `DB_USERNAME` | 后端数据源账号。 |
| 后端运行变量 | `DB_PASSWORD` | 后端数据源密码，仅由受控密钥注入。 |
| 普通 HTTP 测试变量 | 无 | 已齐全；不要重新配置已存在的变量。 |
| reset 专用账号变量 | `SHIPFLOW_MODULE2_RESET_DB_URL` | reset 专用连接地址，路径必须精确为 `shipflow_http_test`。 |
| reset 专用账号变量 | `SHIPFLOW_MODULE2_RESET_DB_USERNAME` | reset 专用登录账号。 |
| reset 专用账号变量 | `SHIPFLOW_MODULE2_RESET_DB_PASSWORD` | reset 专用账号密码，仅在当前进程内使用。 |
| reset 专用账号变量 | `SHIPFLOW_MODULE2_RESET_DB_ACCOUNT` | 用于与 `CURRENT_USER()` 精确比对的 reset 专用账号标识。 |
| reset 专用账号变量 | `SHIPFLOW_MODULE2_ISOLATED_BASE_URL` | 显式批准的隔离后端 URL，必须与模块二 API 地址全值相等。 |
| `SHIPFLOW_MODULE2_ALLOW_RESET` 显式开关 | `SHIPFLOW_MODULE2_ALLOW_RESET` | 只有精确设为 `1` 才允许 reset 门禁通过；本次保持缺失。 |

### 13.3 框架检查与本地单元测试

现有模块二框架已有四身份令牌门禁、唯一运行前缀、幂等键、版本号、资源上下文、API 恢复登记、轮询工具和纯只读数据库断言函数。但 `test_module2_api.py` 目前仍是 20 个静态基线场景，写操作在缺少 API 清理契约时会跳过；它尚未构成可安全执行的 66 条动态真实场景实现。

首次通过全局 Python 执行单元测试失败，原因是该解释器未安装 `pytest`；随后使用 `api-tests/.venv/Scripts/python.exe` 真实执行模块二新增单元测试，结果为 **32 passed，1 条 pytest 缓存目录权限警告**。本轮修正了身份上下文测试：断言展示文本不包含访问令牌，而不是错误地要求身份名称不包含 `platform`。
