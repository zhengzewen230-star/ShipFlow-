# 商家业务员工作台第三阶段 P3-05 真实浏览器验收

更新时间：2026-08-17（Asia/Shanghai）
阶段状态：阻塞，未通过；真实浏览器登录态和商家业务数据证据均未建立，不能进入 P3-06。

## 1. 验收流程与安全边界

商家业务员登录后，后端从认证上下文取得 `tenant_id` 和用户身份，再计算可见的 ACTIVE `store_id`；控制台读取同一租户/店铺范围的运营概览，指标跳转到订单、仓库、轨迹、异常或账单列表。筛选、刷新和网络重试都只能发起只读查询；401、403、空数据和 404 必须按实际响应分别记录，不能把匿名 200、通用 401/403/404 或单元测试结果当成浏览器通过。

本阶段范围为运营概览、店铺管理、物流基础资料、报价、订单、轨迹、异常与索赔、账单与对账 8 个页面及控制台。

本轮没有业务写请求、数据库 SQL、Flyway/V018/V019、顺丰生产调用、Git 提交或推送。密码、Token、Cookie、Authorization 值和请求体没有写入输出、报告或文档。

## 2. 环境检查与阻塞

| 检查项 | 实际结果 |
|---|---|
| `SHIPFLOW_TEST_PASSWORD`（当前 shell 进程） | 变量存在，但未读取值；没有注入到可用的受控浏览器会话，因此浏览器登录凭证仍不可用 |
| `SHIPFLOW_PLATFORM_TEST_PASSWORD`（Codex shell 进程） | 不存在 |
| `DB_URL`、`DB_USERNAME`（Codex shell 进程） | 不存在；`.env` 仅发现变量名称，未读取值 |
| 前端 | `localhost:5173`，PID 9196，Node/Vite |
| Nginx | `localhost:80`，PID 13400 |
| 后端 | `localhost:8080`，PID 24860，Java |
| Playwright/Puppeteer 项目依赖 | 未发现项目配置或直接依赖；未安装、未临时下载 |
| 浏览器插件运行时 | 文件存在，但可用浏览器列表仍为空 `[]` |

原因判断：已运行的 Codex 服务/子进程不会回读用户之后在另一个父 PowerShell 中设置的环境变量。安全的同进程方式是在启动 Codex 的同一个受控 PowerShell 中注入后再启动 Codex；只传递变量名和值到进程内存，不使用 `setx` 持久化密码：

```powershell
$secure = Read-Host 'SHIPFLOW_TEST_PASSWORD' -AsSecureString
$env:SHIPFLOW_TEST_PASSWORD = [Net.NetworkCredential]::new('', $secure).Password
codex
```

本轮没有执行上述注入，也没有索要或输出密码值。

### P3-05-ENV-002：真实浏览器不可用

浏览器插件初始化目标为 `http://localhost:5173/login`；重试可用浏览器列表仍为 `[]`。没有真实浏览器 tab，因此没有页面渲染、控制台、浏览器网络面板、登录点击或页面交互证据。该项是环境阻塞，不以 HTTP 入口 200 代替。

### P3-05-ENV-003：商家登录凭证不可见

当前进程未发现 `SHIPFLOW_TEST_PASSWORD`，所以没有执行 `POST /api/v1/auth/login`，登录状态码为“未执行”，不是失败登录，也没有伪造登录通过。

## 3. 实际执行的页面和接口

### 3.1 页面入口辅助检查（不是浏览器通过）

通过 `Invoke-WebRequest` 只读请求确认 Vite fallback 可取：`/login`、`/app`、`/app/stores`、`/app/logistics`、`/app/quotes`、`/app/orders`、`/app/tracking`、`/app/exceptions`、`/app/billing` 均为 HTTP 200。此结果只计为 9 个静态入口辅助检查通过，不能证明 8 个页面渲染、登录态、控制台无错误或业务 API 正常。

### 3.2 修正后的只读 HTTP 验收脚本

执行文件：[run-http-acceptance.ps1](D:\Projects\shipflow\backend\scripts\run-http-acceptance.ps1)。脚本现在从 `openapi/shipflow-api.yaml` 读取每个 operation 的方法、路径、认证声明和已声明响应码；匿名只执行 GET，POST/PUT/PATCH/DELETE 共 50 个写操作全部标记 `SKIPPED_READ_ONLY`，没有发送空请求体写操作。

| 范围 | 实际数量 | 通过 | 失败 | 未执行/阻塞 |
|---|---:|---:|---:|---:|
| OpenAPI operation | 106 | 106 个被解析且 operationId 唯一 | 0 | 0 |
| 匿名 GET | 56 | 56 | 0 | 0 |
| 写 operation | 50 | — | — | 50，因只读边界跳过 |
| 商家登录 | 1 | 0 | 0 | 1，凭证变量不可见 |
| 登录后 8 页面 | 8 | 0 | 0 | 8，浏览器和登录均阻塞 |

匿名 GET 的真实状态分布：HTTP 204 为 1 个（`getCsrfToken`），HTTP 401 为 55 个（所有受保护 GET）；脚本只按 operation 的精确预期判断，不把 200、401、403、404 混合视为通过。`GET /api/v1/operations/workbench` 在前端代理 `5173`、Nginx `80`、后端 `8080` 三个入口均为 401；CSRF 三个入口均为 204；健康检查和运行时 OpenAPI 分别为 200。

逐 operation 的方法、路径、认证要求、文档响应码、实际状态和原因已写入：[http-acceptance-summary.md](D:\Projects\shipflow\backend\reports\http-acceptance-summary.md) 及同名 JSON 报告。匿名 401 只证明认证过滤器拒绝匿名请求，不推断受保护路由已经实现；受保护路由的真实存在性仍需登录后验证。

## 4. 8 个页面的实际验收状态

| 页面 | 浏览器登录后流程 | 实际状态 | 根因/接口证据 |
|---|---|---|---|
| 运营概览 | 未执行 | 阻塞 | 未登录；匿名 `GET /api/v1/operations/workbench` 为 401 |
| 店铺管理 | 未执行 | 阻塞 | 未登录；匿名 `GET /api/v1/stores` 为 401 |
| 物流基础资料 | 未执行 | 阻塞 | 未登录；匿名 `GET /api/v1/logistics/channels` 为 401 |
| 报价 | 未执行 | 阻塞 | 未登录；匿名 `GET /api/v1/quotes` 为 401 |
| 订单 | 未执行 | 阻塞 | 未登录；匿名 `GET /api/v1/orders` 为 401 |
| 轨迹 | 未执行 | 阻塞 | 未登录；匿名轨迹查询为 401 |
| 异常与索赔 | 未执行 | 阻塞 | 未登录；匿名 `GET /api/v1/exceptions` 为 401 |
| 账单与对账 | 未执行 | 阻塞 | 未登录；匿名账单/对账查询为 401 |

因此浏览器页面通过数为 0/8，失败数为 0/8，阻塞数为 8/8；“失败数 0”不代表通过。

尚未执行：真实 `tenant_id/store_id` 隔离、概览与列表数据一致、指标跳转、筛选、刷新、空数据、登录后 401、缺权限 403、网络失败重试、浏览器控制台无 error/warning，以及 Asia/Shanghai、金额和重量展示检查。

## 5. OpenAPI、自动化测试和脚本修正

- OpenAPI 静态检查：106 个 operationId，唯一值 106；P3-05 所需 operation、路径、方法、认证声明和 200/201/202/204/400/401/403/404/409/422 等已声明响应码均被脚本读取；运行时 `GET /v3/api-docs` 为 200。
- 修正 `backend/scripts/run-http-acceptance.ps1`：删除旧的 82 个 operationId 计数基线；动态读取当前契约，校验 operationId 唯一及 P3-05 operation 存在；明确区分读请求状态，跳过所有写请求，禁止匿名 200/通用 401/403/404 伪通过。
- 修正 `frontend/docs/openapi-coverage.md` 和 `frontend/README.md` 的 92/82 旧引用为 106，并补齐/更正当前 operationId。覆盖表与 OpenAPI 对照为 106/106，无缺失、无未知 ID。
- 前端 `npm run test:unit`：14 个测试文件、52 个测试全部通过。
- 前端 `npm run build`：通过。
- 后端相关测试：JDK 21.0.11，21 个测试类、80 个测试，失败 0、错误 0；未运行 `*IT`，未连接数据库。
- `git diff --check`：通过；仅有工作区既有 LF/CRLF 转换 warning，没有空白错误。

## 6. 未执行内容、失败原因和后续问题

未执行登录后的 8 页面、控制台错误检查、业务数据读取、跨租户/跨店铺访问、概览/列表一致性和异常交互。共同根因为浏览器连接为空且 `SHIPFLOW_TEST_PASSWORD` 不在当前 Codex 进程。没有真实登录 HTTP 状态码，因此不能报告登录成功或失败。

需要项目负责人确认：

1. 将受控密码变量注入启动 Codex 的同一进程，并确认业务用户名/租户编码来源；不在聊天或文档提供密码值。
2. 恢复一个受控真实浏览器连接；项目没有 Playwright 配置，本轮不能自行安装或改用非浏览器来源伪造页面证据。
3. 是否允许继续使用当前本地 `5173 → Nginx 80 → Spring Boot 8080` 只读链路完成登录后的 HTTP 和浏览器验收。

只有浏览器登录态、8 个页面交互和对应 HTTP 状态证据全部齐全后，才可判定 P3-05 通过；当前仍阻塞，不能进入 P3-06。

## 7. 2026-08-17 真实商家登录重试与 P3-05 复验

### 7.1 登录与会话证据

- 当前 Codex 进程中的 `SHIPFLOW_TEST_PASSWORD`：存在；没有读取、输出或写入密码值。
- 使用测试商家业务员 `merchant_operator_001`、租户 `TENANT_DEMO_001` 登录；请求使用无 BOM UTF-8 JSON。
- `GET /api/v1/auth/csrf`：204；`POST /api/v1/auth/login`：200；`GET /api/v1/users/me`：200；`POST /api/v1/auth/refresh`：200。
- Cookie/refresh 会话仅保存到系统临时目录；Cookie 文件和访问令牌文件未进入仓库、日志或本记录。后端响应的 `REFRESH_TOKEN` 为 `Secure`，本地 HTTP 下 curl 不会自动将它写入 cookie jar；本轮仅在临时文件中保存响应 Cookie，并用显式 Cookie 完成 refresh 验证，没有输出 Cookie 或 Token。
- 会话身份为 `TENANT`，权限共 14 个；包含 `operations:read`、`store:read`、`logistics:read`、`quote:read`、`order:read`、`tracking:read`、`exception:read`、`billing:read`，不包含 `warehouse:manage`。

### 7.2 八个页面的真实 API 复验

| 页面 | 实际只读 API 状态 | 结果 | 失败/通过原因 |
|---|---|---|---|
| 概览 | `GET /operations/workbench` = 500 `COMMON-1007`；条件请求 `GET /warehouse/overview` = 403 | FAIL | 运营权限存在但后端返回内部错误；仓库请求因账号没有 `warehouse:manage` 被权限层拒绝，前端按权限不会发出该条件请求。 |
| 店铺 | `GET /stores` = 200 | PASS | 认证读取成功。 |
| 物流资料 | `GET /logistics/channels` = 200 | PASS | 认证读取成功。 |
| 报价 | `GET /quotes` = 200 | PASS | 认证读取成功。 |
| 订单 | `GET /orders` = 200 | PASS | 认证读取成功。 |
| 轨迹 | `GET /orders/SF-001/tracking` = 404 | FAIL | 使用只读测试引用请求，未取得可用轨迹资源；404 没有被当作路由成功。 |
| 异常索赔 | `GET /exceptions` = 200 | PASS | 认证读取成功。 |
| 账单对账 | `GET /billing/import-batches` = 200；`GET /reconciliations` = 200 | PASS | 账单批次和对账列表均认证读取成功。 |

按页面主请求计为 6/8 通过、2/8 阻塞；按本轮 10 个 API 检查计为 7/10 通过、3/10 未通过（其中仓库概览是权限条件请求）。页面壳层的 Vite 200 没有计入业务 API 通过。

### 7.3 401、代理和端口排查

- 登录后没有出现 401；`/users/me` 和 refresh 均为 200，说明本轮登录态、CSRF 和 refresh 会话可用。
- 同一 Bearer 会话下，`/operations/workbench` 在 `5173`、Nginx `80`、后端 `8080` 均为 500；`/warehouse/overview` 三个入口均为 403；三个入口的 CSRF 请求均为 204。因此当前失败不是 Vite 页面 200、代理丢 Authorization/Cookie 或后端端口不可达造成的。
- Nginx `-T` 配置测试成功；活动配置将 `/api/` 代理到 `127.0.0.1:8080`，并转发 Authorization、Cookie、X-XSRF-TOKEN 和 Set-Cookie。当前进程可见 10 个 nginx 实例，未执行 reload/终止操作。
- 原验收脚本未修改，原样执行结果为：106 个 operation、匿名 GET 56/56、写操作 50 个全部跳过、登录 PASS、脚本覆盖的认证读取 7 PASS/2 FAIL（运营工作台 500、仓库概览 403）；脚本未覆盖轨迹页面的输入查询，因此轨迹结果由本节单独记录。详见 [http-acceptance-summary.md](../../backend/reports/http-acceptance-summary.md)。

### 7.4 本轮验证与阻塞结论

- 前端 `npm run test:unit`：14 个测试文件、52/52 通过。
- 前端 `npm run build`：通过。
- 后端 `mvn -q -Dshipflow.build.directory=.codex-build/p305-backend-test test`，JDK 21.0.11：92 个测试类、380/380 通过，失败 0、错误 0、跳过 0；未运行 `verify`、`*IT` 或 Flyway。
- OpenAPI 静态对照：`openapi/shipflow-api.yaml` 与 `frontend/docs/openapi-coverage.md` 均为 106 个 operationId，缺失 0、未知 0，106/106 通过。
- 写接口保持跳过；没有连接生产数据库写入，没有执行 Flyway，没有调用顺丰生产接口。
- 浏览器运行时发现结果仍为 `[]`，没有可控制的真实浏览器 tab。因此没有浏览器页面渲染、登录交互、控制台或浏览器网络面板证据，不能把 HTTP 复验升级为“浏览器验收通过”。

P3-05 当前仍不能报告通过，阻塞项为：受控浏览器连接缺失、运营工作台真实 API 返回 `500 COMMON-1007`、轨迹测试引用返回 404。需要项目负责人确认：

1. 提供受控浏览器连接；
2. 提供可确认的只读轨迹测试引用，或确认 404 对应的非生产数据/路由修复方案；
3. 提供 `COMMON-1007` 请求的后端控制台堆栈，或授权进行只读诊断；
4. 确认商家业务员是否应获得 `warehouse:manage`，以及本地 HTTP 是否应继续使用 `Secure` refresh Cookie。

### 7.5 P3-05 学习笔记

真实登录验收必须同时记录登录、当前用户、refresh、权限和业务 API；`5173` 返回页面 200 只能证明静态入口可取。对多层网关应使用同一认证会话分别请求 5173、80、8080，才能区分会话问题、代理问题和后端业务错误。条件权限 API 要按当前账号的权限模型解释，不能把未发出的仓库请求伪造成页面成功；轨迹页面必须使用已确认存在的只读引用，404 不能替代真实数据证据。

## 8. 2026-08-17 P3-05 前置条件核对清单

### 8.1 浏览器连接方式与证据边界

- 当前浏览器运行时的可用连接列表仍为 `[]`，没有可控制的真实浏览器 tab；因此本轮没有页面渲染、登录点击、页面交互、控制台或浏览器网络面板证据，不伪造浏览器验收结果。
- 可接受的连接方式有两种：
  1. 由 Codex 可见并连接的受控 In-App Browser，打开本地 `http://localhost:5173/login`；
  2. 通过 ChatGPT 浏览器扩展连接的外部 Chrome/Edge，在 **Settings → Computer use** 中完成连接后，再提供可控制的本地页面。
- 不能用独立 Playwright、普通 curl、Vite 页面 200 或 HTTP 响应替代真实浏览器页面证据。浏览器连接恢复后，仍需重新建立登录态并逐页检查页面、控制台和网络请求。

### 8.2 只读 HTTP 脚本的临时依据

- `backend/scripts/run-http-acceptance.ps1` 可以作为浏览器恢复前的临时辅助依据：它动态读取 106 个 OpenAPI operation，匿名 GET 逐项断言，写操作全部跳过，并可在提供测试凭证时验证登录后的只读状态码。
- 本轮脚本未修改，最近一次真实结果为：匿名 GET 56/56、写操作 50 个跳过、商家登录 PASS、脚本覆盖的认证读取 7 PASS/2 FAIL。
- 它只能证明 HTTP 路由、认证会话和 API 状态，不能证明页面渲染、浏览器 Cookie 行为、路由跳转、表单交互、控制台无错误、网络面板、刷新重试或页面数据展示。因此不能替代真实浏览器验收，也不能单独判定 P3-05 通过。

### 8.3 COMMON-1007 后端桩定位

| 项目 | 只读结论 |
|---|---|
| 对应接口 | `GET /api/v1/operations/workbench`；入口为 `OperationsController.workbench`。 |
| 历史返回与当前复核 | 历史记录中的认证请求曾在 5173、Nginx 80、后端 8080 返回 HTTP 500 `COMMON-1007`；本轮使用同一只读登录流程复核三入口，workbench 均返回 HTTP 200，匿名请求均按认证边界返回 401。 |
| 当前返回原因 | `GlobalExceptionHandler.handleUnexpected` 捕获未预期异常并统一返回 `COMMON-1007 / 内部系统错误`。`OperationsController`、`OperationsQueryApplicationService` 和 `OperationsMapper` 均没有主动抛出 `COMMON-1007`；Controller/Service 中可预期的租户、用户、参数和权限异常分别走 `COMMON-1001` 或 `COMMON-1004`。 |
| 调用链 | `OperationsController.workbench` 解析查询参数并调用 `OperationsQueryApplicationService.workbench` → Service 校验租户/用户、规范化查询、按 Asia/Shanghai 计算窗口 → `OperationsMapper.findVisibleStoreIds`、`findWorkbenchCounts`、`findRecentOrders`、`findRisks` 查询可见店铺、工作台计数、最近订单和风险 → Service 组装真实响应。上述任一未预期异常都会被 `handleUnexpected` 统一映射为 500。 |
| 是否预期未实现 | 不是。Controller、Service、Mapper/XML、OpenAPI 200 响应契约和相关单元/静态测试均已存在；当前不是允许用空数据或占位响应通过的“未实现”状态。 |
| 根因与处理决定 | 当前没有可复现的 workbench 500。保留日志中唯一匹配的完整 `BindingException(status)` 属于 `WarehouseWorkMapper.findById(orderId, tenantId)` 的仓库详情请求，不是 workbench，不能误归因。当前运行库字段、Mapper 查询和真实有数据的商家管理员请求均通过；因此没有证据支持修改生产 Mapper/Service 或扩大权限。 |

### 8.4 `warehouse:manage` 角色核对

- `SecurityConfig` 对 `GET /api/v1/warehouse/overview`、`GET /api/v1/warehouse/orders` 以及仓库入库、复称、出库和顺丰履约写接口要求 `scope:TENANT + warehouse:manage`。
- OpenAPI 的 `getWarehouseOverview` 描述为当前租户仓库操作员授权范围概览，并声明 401/403；这不是商家业务员概览的必需权限。
- 代码/矩阵只读结果：
  - `MERCHANT_OPERATOR`：报价、订单、轨迹、异常、账单和运营概览；不应默认拥有仓库作业权限。
  - `WAREHOUSE_OPERATOR`：仓库入库、复称、出库及必要订单/轨迹读取；应拥有 `warehouse:manage`。
  - V011 的目标矩阵明确把 `warehouse:manage` 绑定到 `WAREHOUSE_OPERATOR`，并给该角色 `order:read`、`tracking:read`；没有把它授予 `MERCHANT_OPERATOR`。
- 最小授权方案：只向当前租户的有效 `WAREHOUSE_OPERATOR` 角色绑定 `warehouse:manage`，同时按已确认矩阵保留 `order:read`、`tracking:read`；不向 `MERCHANT_OPERATOR`、财务或客服角色扩散。影响范围是仓库概览、仓库作业列表/详情、仓库状态写操作和顺丰履约操作；不改变报价、订单普通读取、轨迹、异常或账单权限。
- `database/init_data.sql` 仍保留旧的仓库权限字典/绑定，V011 才定义了 `warehouse:manage` 及新的标准角色矩阵；由于本轮禁止执行 Flyway 且没有数据库凭证，不能把 V011 的目标矩阵表述为当前已持久化状态。当前只确认代码和迁移文件中的目标定义，未确认运行库实际绑定。
- 本轮没有执行数据库查询、角色绑定、权限写入或 Flyway。若业务要求商家业务员也看到仓库指标，需要负责人先确认这是角色边界变更，而不是为通过验收临时放权。

### 8.5 验证基线与状态分类

已完成：

- 前端测试 52/52；
- 后端测试 380/380，JDK 21.0.11；
- OpenAPI 106/106；
- `git diff --check` 通过；
- 已更新本文件并保留 P3-05 阻塞状态；
- 未修改业务代码、数据库、权限数据或验收脚本。

未完成：

- 真实浏览器连接、商家浏览器登录态、8 个页面交互、控制台/网络面板和页面展示证据；
- 历史 workbench 500 的可复现根因和对应生产修复；当前已无法复现，不能伪造根因；
- 已确认存在的只读轨迹测试引用；
- 负责人确认后的仓库角色权限方案。

环境阻塞：

- 当前没有可用浏览器连接；
- 后端运行进程没有可供本轮读取的对应异常堆栈；
- 当前本地 HTTP 使用 `Secure` refresh Cookie，浏览器连接恢复后仍需验证其在实际本地链路中的会话恢复行为。

需要负责人确认：

1. 提供受控 In-App Browser 或浏览器扩展连接，并确认可访问本地页面；
2. 确认只读 HTTP 结果仅作临时辅助依据，不替代浏览器验收；
3. 提供 `COMMON-1007` 对应请求的后端堆栈，或授权只读运行库诊断；
4. 确认 `WAREHOUSE_OPERATOR` 的最小授权方案，以及商家业务员是否明确不拥有 `warehouse:manage`；
5. 提供或确认非生产只读轨迹测试引用。

在浏览器连接可用、商家登录态可验证、`COMMON-1007` 处理方案确认、仓库权限方案确认前，P3-05 继续保持阻塞，不进入下一阶段。

## 9. 2026-08-17 只读诊断复核与恢复 P3-05 的最小条件

### 9.1 本轮环境阻塞

- 浏览器控制运行时的可用列表为 `[]`。已检查目标 `http://localhost:5173/app`，HTTP 入口返回 200，但这不是浏览器连接，也没有页面、控制台或浏览器网络面板证据；因此不报告真实浏览器验收结果。
- 当前 shell 进程能检测到 `SHIPFLOW_TEST_PASSWORD` 变量存在，但只读取存在性，未读取值；变量没有注入到可用的受控浏览器会话，浏览器登录未执行。`SHIPFLOW_PLATFORM_TEST_PASSWORD`、`DB_URL`、`DB_USERNAME` 在当前进程不可见；不以此推测密码或数据库内容。
- 后端默认配置 `SHIPFLOW_AUTH_COOKIE_SECURE=true`，refresh Cookie 带 `Secure`。当前没有可用浏览器连接，无法验证本地 HTTP `http://localhost:5173/app` 中 Cookie 的保存、回送和刷新行为；静态入口 200 或显式 HTTP 请求头不能替代该验证。

### 9.2 恢复 P3-05 的最小条件

1. **可用浏览器连接**：提供 Codex 可控制的 In-App Browser 或已连接的浏览器扩展，并打开 `http://localhost:5173/app`；只有该连接能提供页面渲染、交互、控制台和网络面板证据。
2. **同一进程可读取的登录凭证变量**：在启动受控验收进程的同一环境注入商家测试密码变量，并明确用户名和租户编码来源；只检查变量可读性，不在聊天、日志或文档输出密码值。
3. **HTTP 验收凭证方案**：先获取 CSRF，再通过登录建立访问令牌和 refresh Cookie；访问令牌只保存在验收进程内存，业务验收只执行 GET 读请求，写操作继续跳过。必须在真实浏览器中验证 `Secure` refresh Cookie 的保存/回送；若本地 HTTP 不满足该浏览器策略，负责人需选择 HTTPS 本地入口或批准明确的非生产 Cookie 配置方案，不能用手工复制敏感值伪造浏览器会话。
4. **COMMON-1007 处理决定**：提供该请求对应的后端控制台堆栈，或明确授权只读运行库诊断；据此决定修复 Mapper/Service、修复数据/结构，还是记录已确认的环境原因。不得用空数据、通用 200 或单元测试替代真实接口结果。
5. **WAREHOUSE_OPERATOR 权限决定**：确认是否采用最小矩阵：当前租户有效 `WAREHOUSE_OPERATOR` 获得 `warehouse:manage`、`order:read`、`tracking:read`，商家业务员、财务和客服不获得该权限。若决定变更，必须另行审批并通过受控迁移/权限流程处理；本轮不修改数据库权限、不新增迁移、不执行 Flyway。

### 9.3 保留的已通过结果与边界

- 前端 52/52；
- 后端 380/380；
- OpenAPI 106/106；
- `git diff --check` 通过；
- 未修改业务代码、数据库权限、迁移或验收脚本；未提交、未推送 Git。

P3-05 继续保持阻塞；上述五项条件未满足前，不进入 P3-06。

## 10. 2026-08-17 workbench 500 复核、回归测试与浏览器准备

### 10.1 完整异常证据与根因边界

- 历史持久化日志中唯一匹配的完整异常为 `org.apache.ibatis.binding.BindingException: Parameter 'status' not found. Available parameters are [orderId, tenantId, param1, param2]`，调用链是 `WarehouseWorkController.get` → `WarehouseWorkApplicationService.get` → `WarehouseMapper.findById`；这是仓库订单详情问题，不是 `GET /api/v1/operations/workbench`，未将其误记为 workbench 根因。
- 当前 IntelliJ 后端进程没有持久化到项目目录的控制台日志；本轮通过 8080、80、5173 三入口触发了真实只读认证请求，workbench 均为 200。使用商家管理员真实租户数据时返回 2 个可见店铺、7 个最近订单和 1 个风险项，说明当前 SQL、DTO 字段类型、空值处理和 MyBatis 结果映射在有数据路径上可工作。
- 当前运行库只读检查确认工作台涉及表均存在，关键字段包括 `shipment_order.current_status/chargeable_weight/current_fee/updated_at`、`provider_order.lifecycle_status/tracking_no`、`warehouse_outbound_record.tracking_no`、`tracking_event.process_status/received_time`、`bill_detail.detail_status`、`reconciliation_record.difference_amount` 和 `customs_document.document_status`。未执行任何写 SQL。
- 结论：历史 workbench 500 的具体根因在当前证据中不可复现，不能把仓库详情的 `status` 参数异常冒充 workbench 根因；本轮没有修改生产 Controller、Service、Mapper/XML 或数据库。

### 10.2 权限与错误分类复核

| 场景 | 实际结果 | 结论 |
|---|---:|---|
| 匿名 `GET /api/v1/operations/workbench` | 401 | 认证边界正确 |
| 租户作用域但无 `operations:read` 的 `warehouse_operator_001` | 403 | workbench 最小权限为 `scope:TENANT + operations:read`；不扩大 `WAREHOUSE_OPERATOR` 权限 |
| 商家管理员有真实店铺/订单数据 | 200 | DTO/Mapper 有数据路径可用 |
| 商家业务员无可见店铺数据 | 200，空店铺/订单/风险集合 | 空数据不转为 500 |
| 注入 Mapper 异常的后端测试 | 500 `COMMON-1007` | SQL/Mapper 异常保持明确内部业务错误，不伪造 200 |
| 未监听本地端口的只读 GET | 无 HTTP 响应 | 网络失败按失败记录，不计为通过 |

### 10.3 本轮修改文件与测试

- `backend/src/test/java/com/shipflow/operations/OperationsQueryApplicationServiceTest.java`：补充所有读取结果为空时返回零指标和空集合的测试。
- `backend/src/test/java/com/shipflow/operations/OperationsMapperXmlTest.java`：补充 workbench 四个查询、命名参数、结果字段和只读契约检查。
- `backend/src/test/java/com/shipflow/operations/OperationsWorkbenchControllerWebMvcTest.java`：补充缺少 `operations:read` 的 403，以及 Mapper 异常返回 `COMMON-1007` 的测试。
- 本文档：记录当前复核、根因边界、权限和浏览器阻塞。
- 未修改生产业务代码、数据库权限、数据库结构或迁移；未执行 Flyway，未调用顺丰生产接口。

### 10.4 当前测试和验收状态

- 定向 workbench 回归：10/10 通过；
- 后端原有基线：92 个测试类、380/380 通过；加入本轮 4 条回归后全量为 92 个测试类、384/384 通过，JDK 21.0.11；
- 前端：52/52 通过，构建通过；
- OpenAPI：106/106；
- `git diff --check`：通过，仅有既有 LF/CRLF warning；
- 浏览器连接：仍不可用，浏览器列表为 `[]`；
- 当前 shell `SHIPFLOW_TEST_PASSWORD`：存在，仅检查存在性，未输出值；
- 三入口登录：HTTP 200；三入口 refresh Cookie 未被 PowerShell 会话保存，refresh 实际返回 401；该结果不能替代真实浏览器 Cookie 验收。

真实浏览器未连接，因此 8 个商家页面的渲染、交互、控制台、网络面板、页面跳转、刷新重试和空状态仍未完成。P3-05 继续阻塞，不进入 P3-06；本轮未提交、未推送 Git。

## 11. 2026-08-17 当前 Chrome 会话只读复核

### 11.1 连接、凭证、端口和安全边界

第 10 节中的“浏览器不可用”是此前复核时的历史状态。本节使用当前已连接的 Google Chrome 扩展会话，认领已有 ShipFlow 标签页，并先打开 `http://localhost:5173/`：页面标题为 `ShipFlow`，导航、商户工作台和已登录用户信息均可见。

- 使用的是浏览器现有登录态，没有填写密码、读取 Token、Cookie、Authorization、浏览器存储或请求/响应敏感内容。
- 当前 Codex shell 进程中 `SHIPFLOW_TEST_PASSWORD`：存在；仅检查存在性，未读取或输出值，也未用它绕过浏览器认证。
- 前端监听 `localhost:5173`；`frontend/vite.config.ts` 的 `/api` 代理目标为 `http://127.0.0.1:80`；Nginx 监听 80，后端监听 8080；18080 未监听。
- 浏览器网络资源显示为 `http://localhost:5173/api/...`，这是 Vite 代理入口，不代表绕过网关直连后端。
- 每个页面均观察到 refresh 请求 HTTP 200，但本轮不读取 Cookie 值或属性；因此只能证明当前会话刷新请求成功，仍不能独立证明 `Secure` refresh Cookie 在本地 HTTP 链路中的保存、回送和属性行为。

### 11.2 八个页面实际只读请求和页面结果

以下状态来自 Chrome 页面实际资源计时中的响应状态；业务查询均为 GET。每个页面还共同观察到 refresh 请求 HTTP 200 和 `GET /api/v1/users/me` HTTP 200。没有发起写接口、没有创建/修改/删除业务数据。

| 页面 | 实际业务请求 | 状态 | 页面实际结果 |
|---|---|---:|---|
| 概览 `/app` | `GET /api/v1/operations/workbench` | 200 | 运营概览正常渲染；今日指标、待办、风险和最近订单均为空/为 0，显示空统计提示。 |
| 店铺管理 `/app/stores` | `GET /api/v1/stores` | 200 | 店铺表正常渲染，显示 2 个当前租户店铺。 |
| 物流基础资料 `/app/logistics` | `GET /api/v1/logistics/channels` | 200 | 物流渠道表正常渲染，显示 4 个可用渠道。 |
| 报价 `/app/quotes` | `GET /api/v1/quotes` | 200 | 报价列表正常渲染，显示当前租户只读报价数据。 |
| 订单 `/app/orders` | `GET /api/v1/orders` | 200 | 页面正常渲染“暂无数据”空状态。 |
| 轨迹 `/app/tracking` | 未发起业务查询（输入为空） | — | 页面正常渲染，查询按钮保持禁用并提示输入订单号或运单号。 |
| 异常与索赔 `/app/exceptions` | `GET /api/v1/exceptions` | 200 | 页面正常渲染“暂无数据”空状态。 |
| 账单与对账 `/app/billing` | `GET /api/v1/billing/import-batches` | 200 | 页面正常渲染“当前暂无财务记录”空状态；费用调整接口未提供，页面未展示伪造数据。 |

浏览器控制台仅观察到 Vite 连接调试信息，未观察到 error/warning。当前 8 个页面的真实只读加载结果已建立，但未将“未触发”的 401、403 和网络失败场景伪造成通过：本轮未注销登录态、未改写 Cookie、未关闭服务，也未人为制造失败请求。

### 11.3 当前结论和剩余阻塞

- workbench 当前真实浏览器请求为 200，历史 500 `COMMON-1007` 在本轮无法复现；完整后端堆栈仍未提供可归因于 workbench 的异常。已确认的 `BindingException(status)` 属于仓库详情链路，不能冒充 workbench 根因。
- workbench 最小读取权限仍为 `scope:TENANT + operations:read`。`WAREHOUSE_OPERATOR` 当前只读检查结果为 `warehouse:manage`、`order:read`、`tracking:read`；不增加 `operations:read`，不修改数据库权限。
- 保留已通过结果：前端 52/52、后端原有基线 380/380（本轮增加 4 条回归后全量 384/384）、OpenAPI 106/106、`git diff --check` 通过。
- 本轮修改文件为三个 operations 后端测试文件和本文档；未修改生产 Controller/Service/Mapper/XML、数据库、权限数据或迁移，未执行 Flyway，未调用顺丰生产接口。
- P3-05 总体仍保持阻塞，不进入 P3-06。恢复通过仍需项目负责人确认：可复现的 COMMON-1007 处理决定、WAREHOUSE_OPERATOR 权限决定，以及对 Secure refresh Cookie 属性和 401/403/网络失败场景的明确验收方案。

本轮未提交、未推送 Git。

## 12. 2026-08-17 当前会话身份、响应结构与刷新策略复核

### 12.1 当前 Chrome 会话身份（仅脱敏）

- 当前账号：`scope=TENANT`，用户名显示为 `m***1`，显示名显示为 `商***一`；角色为 `MERCHANT_OPERATOR`。
- 当前内存访问令牌仅确认存在，未读取或输出值；浏览器业务请求使用 Bearer Authorization，未输出请求头。
- 当前权限包含 `operations:read`、`store:read`、`logistics:read`、`quote:read`、`order:read`、`tracking:read`、`exception:read`、`billing:read` 等；不包含 `warehouse:manage`。
- 当前 `tenant_id` 已在前端用户状态和 workbench `scope` 中确认一致，但文档不记录其明文；当前 workbench `scope.storeIds` 条数为 0。

### 12.2 八个页面响应结构和条数

公共响应契约为 `{ success, traceId, data }`。列表接口的 `data` 均使用分页结构 `{ page, pageSize, total, totalPages, items }`，前端服务统一读取 `data.items`；workbench 的 `data` 结构为 `businessTimeZone`、`scope`、`timeRange`、`refreshedAt`、`metrics`、`todos`、`recentOrders`、`risks`。当前 Vue 组件状态和页面 DOM 的条数如下：

| 页面/接口 | 后端响应后的前端条数 | 结论 |
|---|---:|---|
| `GET /stores` | 2 | 真实非空数据；`items` 映射正确。该接口当前 SQL 仅按 `tenant_id`/分页过滤，不按业务员店铺范围过滤。 |
| `GET /logistics/channels` | 4 | 真实非空数据；`items` 映射正确。 |
| `GET /quotes` | 14 | 真实非空数据；`items` 映射正确。 |
| `GET /orders` | 0 | 真实空数据响应，页面显示空状态；不是字段映射错误。订单查询按调用者角色/店铺范围过滤。 |
| `GET /exceptions` | 0 | 真实空数据响应，页面显示空状态；不是字段映射错误。 |
| `GET /billing/import-batches` | 0 | 真实空数据响应，页面显示空状态；未展示伪造数据。 |
| 轨迹页 | 未发起业务查询 | 输入为空，按钮禁用；不能把未查询误记为 API 返回空数据。 |
| `GET /operations/workbench` | `metrics=8`、`todos=6`，全部 count=0；`recentOrders=0`、`risks=0` | 响应结构完整，前端字段读取正确；是后端按当前时间窗口和调用者可见范围计算出的空快照。 |

当前 workbench 返回 `scopeType=ALL_TENANT_STORES` 但 `storeIds` 条数为 0。代码中 `scopeType` 仅由是否传入 `storeId` 决定，而可见店铺 ID 则由 `MERCHANT_OPERATOR` 的 active `sys_user_store_scope` 决定；这属于后端权限范围语义需要确认的问题，不是前端映射错误。店铺页能看到 2 个租户店铺，也不能证明当前业务员拥有这 2 个店铺的 workbench/订单可见范围。

### 12.3 Network、refresh Cookie 和失败清理

- 当前会话的 `accessToken` 仅检查存在性，前端 HTTP 拦截器在存在时设置 `Authorization: Bearer <已脱敏>`；没有读取或输出令牌值。
- 当前浏览器 refresh 请求为 HTTP 200，说明本次现有会话可完成刷新；CDP 当前能力只返回 Runtime 事件，未提供可安全读取的 Network 请求头/响应头事件，因此没有伪造“已验证 Authorization 原文或 Set-Cookie 原文”的结果。
- 后端代码确认登录和 refresh 成功路径通过 `Set-Cookie` 设置 refresh Cookie；配置元数据为 `HttpOnly=true`、`Secure=true`、`SameSite=Strict`、`Path=/api/v1/auth`、未指定 `Domain`（host-only）。这些是源码配置结论，不是读取浏览器 Cookie 值或属性的结果。
- `Path` 覆盖 `/api/v1/auth/refresh`，未指定 `Domain` 对 localhost 属于 host-only；但 `Secure=true` 在当前 HTTP localhost 链路中的实际保存/回送仍未由浏览器 Cookie 面板独立验证，P3-05 不能据此宣称完成。
- refresh 失败时，前端 `auth.refresh()` 会清空内存中的 access token、currentUser 和认证状态；但失败分支不会调用 `/auth/logout`，因此不会主动收到后端清除 HttpOnly refresh Cookie 的响应。显式 logout 才会清除两类 Cookie。本轮没有人为失效 Cookie 或注销会话，未改变当前登录态。

### 12.4 workbench 查询、数据库证据和 COMMON-1007

- `OperationsController` 从 JWT 读取 `tenant_id` 和用户 subject；`OperationsQueryApplicationService` 先查询可见店铺，再用 tenant、user、时间窗口和可选 store 条件调用四个只读 Mapper 查询。
- `OperationsMapper.xml` 的 workbench 查询均带 tenant 条件；`MERCHANT_OPERATOR` 需要 active `sys_user_store_scope`，而 `MERCHANT_ADMIN`、财务、仓库、客服角色走租户级可见分支。当前浏览器返回的 `storeIds=0` 与这一 SQL 条件一致。
- 当前浏览器真实响应证明该租户入口至少有 2 个店铺、14 条报价；但当前业务员没有 workbench 可见店铺 ID。数据库直接 SQL 本轮未使用凭证重读，当前 Codex 进程没有可用 DB 凭证，未输出或寻找密码；因此不把“API 返回的 2 个店铺”伪称为已完成的数据库 store-scope 行级核对。
- 当前真实 `GET /api/v1/operations/workbench` 为 200，未复现 COMMON-1007。后端定向测试中的 Mapper 异常是人为注入，仅证明 `GlobalExceptionHandler` 对未预期 MyBatis 异常返回 500 `COMMON-1007`，不能当作生产触发条件。当前没有足够堆栈证据定位真实生产根因，未猜测、未修改生产 Mapper/Service/Controller。

### 12.5 本轮验证结果

- 前端 `npm run test:unit`：52/52；`npm run build`：通过。
- 后端 JDK 21.0.11 定向测试：28/28（operations、Mapper、Controller、auth）；首次使用 JDK 8 的失败仅为环境编译失败，已切换到项目要求的 JDK 21 后通过。
- OpenAPI/只读 HTTP 检查：106 个 operation，匿名 GET 56/56，通过；写操作 50 个全部跳过；认证页面检查 8 个通过，`warehouse/overview` 因当前账号缺少 `warehouse:manage` 返回预期 403。
- `git diff --check`：通过，仅保留既有 LF/CRLF warning。
- 未新增权限、未修改数据库、未写入业务数据、未执行 Flyway、未调用顺丰生产接口；未提交、未推送 Git。

P3-05 仍阻塞，不进入 P3-06。通过前仍需确认：业务员店铺范围是否应有 active store scope、店铺列表是否应与该范围一致、Secure refresh Cookie 在本地 HTTP 的真实属性/回送结果，以及 COMMON-1007 的真实可复现堆栈或明确的“不复现”处理决定。

## 14. 2026-08-18 真实浏览器登录与店铺授权数据复核

### 14.1 已建立的浏览器证据

- 已通过受控 In-App Browser 打开本地登录页，并使用当前受控进程中已配置的商家测试凭证完成真实登录。密码值没有输出、写入文档、浏览器脚本或日志。
- 登录后 `GET /api/v1/users/me` 返回 HTTP 200。脱敏身份为租户范围的 `MERCHANT_OPERATOR`，角色数为 1、权限数为 14；不记录用户、租户、令牌、Cookie 或认证头的真实值。
- `GET /api/v1/stores` 由浏览器页面实际发起并返回 HTTP 200。店铺管理页在加载完成后显示“暂无数据”，控制台无 error/warning。这与此前已记录的脱敏响应 `total=0`、`items=0` 一致，不是前端读取 `data.items` 错误、会话失效、代理失败或 HTTP 错误。
- `GET /api/v1/operations/workbench` 由已登录概览页实际发起并返回 HTTP 200。响应包含 8 个核心指标、6 个待办类别、0 个最近订单和 0 条风险；概览展示的统计窗口为 `Asia/Shanghai`，控制台无 error/warning。响应不以 `storeIds` 作为对外字段，当前可见店铺范围只能由后端查询条件和已记录的只读数据库聚合佐证。
- 点击概览“刷新”后，浏览器实际重新请求 `GET /api/v1/operations/workbench?timeRange=TODAY&recentLimit=10&riskLimit=10`，返回 HTTP 200，刷新时间继续显示，控制台无 error/warning。

### 14.2 授权数据正式补充方式

- `sys_user_store_scope` 是唯一的商家业务员店铺授权关系，具有 `(tenant_id, user_id, store_id)` 唯一约束、ACTIVE/DISABLED 状态和租户、用户、店铺外键。该表不是由前端菜单或 token 内店铺字段替代。
- 初始化示例仅给每个演示商家操作员授权 1 个本租户 ACTIVE 店铺；当前测试账号的 ACTIVE scope 为 0，而其所属租户有 2 个 ACTIVE 店铺。这解释了店铺页和工作台的真实空快照。
- 代码库目前未发现用户店铺授权的 Controller、Service、OpenAPI operation 或前端授权管理页。因此正式补充应走受控 DBA/权限变更流程，向 `sys_user_store_scope` 为当前测试商家操作员、其当前租户和业务负责人明确选择的 1 个或 2 个现有 ACTIVE 店铺建立/启用 scope；不得涉及其他用户、租户或店铺。
- 本轮未执行 INSERT、UPDATE、Flyway 或任何权限/业务数据写入；未恢复 tenant-only 查询，未修改 `StoreMapper.xml`。HTTP 200 空列表继续保留为真实权限边界证据。

### 14.3 P3-05 状态

- P3-05 仍阻塞，不能进入 P3-06。恢复条件是项目负责人确认最小授权范围并由正式授权流程完成写入，然后在同一受控浏览器中复验 `/api/v1/stores`、`/api/v1/operations/workbench`、8 个业务页面和跨店铺未授权资源的 404。
- 本轮尚未复验全部 8 个业务页面、跨店铺 404、401/403/网络失败重试、refresh Cookie 属性及回送、以及 COMMON-1007 是否可复现；这些项目不能由本次成功登录和空数据快照替代。

## 15. 2026-08-18 授权两店铺后的复核

### 15.1 授权变更

- 已获明确授权：为当前测试商家操作员授权其所属租户的全部 2 个 ACTIVE 店铺。
- 写入前脱敏聚合：匹配测试账号 1 个、候选 ACTIVE 店铺 2 个、现有 ACTIVE scope 0 条；目标唯一范围为当前账号 + 当前租户 + 该租户全部 2 个 ACTIVE 店铺。
- 通过受控 DBA 事务补充 2 条 `sys_user_store_scope` ACTIVE 关系；使用唯一关系条件避免重复插入，并在事务提交后复核 ACTIVE scope 为 2 条、候选店铺仍为 2 个。
- 未修改 `StoreMapper.xml`，未恢复 tenant-only 查询；未涉及其他账号、租户或店铺。密码、Token、Cookie、完整认证头和完整 ID 未写入输出或文档。

### 15.2 真实浏览器结果

- 当前受控浏览器保持真实登录态。`GET /api/v1/stores` 实际返回 HTTP 200，脱敏响应为 `total=2`、`items=2`；店铺管理页不再显示空数据。
- `GET /api/v1/operations/workbench` 实际返回 HTTP 200，包含 8 个核心指标、6 类待办、0 个最近订单和 1 条真实风险提醒；概览页面正常渲染。
- 概览刷新实际重新请求工作台接口并返回 HTTP 200；页面刷新时间正常显示。
- 店铺页、概览页和工作台浏览器控制台均未发现 error/warning。

### 15.3 跨租户资源边界

- 只读数据库核对确认存在其他租户 ACTIVE 店铺，且选定的负向测试资源不属于当前账号租户。
- 尝试在受控浏览器直接打开该详情 API 时，被浏览器客户端以 `ERR_BLOCKED_BY_CLIENT` 拦截；应用内店铺列表当前没有详情操作入口。因此本轮没有获得该请求的 HTTP 404 浏览器证据，不能记录为通过。
- 后端实现和既有 Mapper/Controller 测试仍要求按 `tenant_id`、用户角色和 ACTIVE `sys_user_store_scope` 校验资源归属；跨租户详情 404 仍是待补充的浏览器验证项，不通过放宽查询解决。

### 15.4 验证边界与下一步

- 已完成：最小授权写入、授权后店铺列表真实读取、工作台真实读取、概览刷新、控制台错误检查、数据库目标范围复核。
- 未完成：通过受控浏览器触发跨租户详情并取得 404；8 个页面完整复验；401/403/网络失败重试和 refresh Cookie 属性检查。
- P3-05 不能因店铺数据恢复而直接关闭，也不能进入 P3-06；应先补齐上述浏览器负向证据及剩余页面验收。

### 15.5 本轮验证命令

- 前端 `npm run test:unit`：通过，14 个测试文件、52 个测试通过。
- 前端 `npm run build`：通过，`vue-tsc` 与 Vite 生产构建均通过。
- OpenAPI 静态检查：通过，检测到 89 个路径、106 个 operation。
- `git diff --check`：通过；仅有既有 LF/CRLF 转换提示，没有空白错误。
- 后端首先输出并确认 `java -version` 为 JDK 21.0.11，`mvn -version` 为 Maven 3.9.16 且使用同一 JDK。随后后端编译和定向测试未进入测试阶段：默认 `backend/target/classes` 被现有进程占用，资源过滤报“拒绝访问”；尝试指定独立构建目录后项目仍解析到该被占用目录。该结果属于环境阻塞，不计为代码测试通过，也不采用此前 JDK 8 的失败结果。
- 本轮未执行 Flyway、顺丰生产调用、Git commit 或 push。

## 16. 2026-08-18 P3-05 收尾复核

### 16.1 浏览器登录态与店铺授权

- PASS：受控浏览器会话恢复到已登录的商家操作员工作台；当前用户界面显示商户业务人员身份，页面控制台未发现 error/warning。
- PASS：只读数据库 scope 聚合为 `2 / 2 / 0`，依次表示当前账号 ACTIVE scope 数、同时满足当前租户和 ACTIVE 店铺条件的 scope 数、跨租户或失效 scope 数。当前 2 个店铺仅来自测试账号的 ACTIVE `sys_user_store_scope`。
- PASS：代码保持 `StoreMapper.xml` 的 tenant、用户、角色和 ACTIVE scope 查询条件；本轮没有修改该文件、没有恢复 tenant-only 查询，也没有追加任何数据库权限写入。

### 16.2 八个页面真实浏览器复核

| 页面 | 页面渲染 | 真实 GET | 数据状态 | 刷新/跳转 | 控制台 | 结论 |
|---|---|---|---|---|---|---|
| 运营概览 | 正常 | `/auth/refresh`、`/users/me`、`/operations/workbench` 均 200 | 非空指标、待办和风险；最近订单为空 | 刷新后工作台 200；订单指标保留订单筛选参数 | 无 error/warning | PASS |
| 店铺管理 | 正常 | `/auth/refresh`、`/users/me`、`/stores` 均 200 | 非空，`total=2`、`items=2` | 刷新后 `/stores` 200 | 无 error/warning | PASS |
| 物流基础资料 | 正常 | `/auth/refresh`、`/users/me`、`/logistics/channels` 均 200 | 非空 | 刷新后渠道接口 200 | 无 error/warning | PASS |
| 报价管理 | 正常 | `/auth/refresh`、`/users/me`、`/quotes` 均 200 | 非空 | 当前页面无单独刷新控件 | 无 error/warning | PASS（无刷新控件） |
| 订单管理 | 正常 | `/auth/refresh`、`/users/me`、`/orders` 均 200 | 非空 | 概览订单指标跳转到带 `resourceType=SHIPMENT_ORDER` 的订单列表 | 无 error/warning | PASS |
| 轨迹全链路 | 正常 | `/auth/refresh`、`/users/me` 均 200 | 空查询提示，未输入订单号/运单号时查询按钮禁用 | 不应在空查询状态伪造轨迹 GET；无刷新控件 | 无 error/warning | PASS（空查询状态） |
| 异常与索赔 | 正常 | `/auth/refresh`、`/users/me`、`/exceptions` 均 200 | 空数据 | 当前页面无单独刷新控件 | 无 error/warning | PASS（真实空数据） |
| 账单与对账 | 正常 | `/auth/refresh`、`/users/me`、`/billing/import-batches` 均 200 | 非空 | 刷新后账单批次接口 200 | 无 error/warning | PASS |

- PASS：概览“待贴标/打单”指标为受权限控制的仓库入口。商家操作员点击后路由进入 `/403`，符合缺少 `warehouse:manage` 的预期权限拒绝，不是页面或后端异常。

### 16.3 401、403、404、网络失败与 refresh

| 场景 | 结果 | 证据/限制 |
|---|---|---|
| 401 会话过期与前端清理 | BLOCKED | 安全制造该场景需清除或篡改当前真实会话；本轮仅做只读验收，不修改浏览器认证状态。 |
| 403 无仓库权限 | PASS | 概览仓库指标跳转实际进入 `/403`；商家操作员不具备 `warehouse:manage`。 |
| 跨租户资源 404 | BLOCKED | 已确认存在跨租户负向资源，但受控浏览器直接 API 导航被 `ERR_BLOCKED_BY_CLIENT` 拦截，应用内当前没有店铺详情入口可触发该请求。不能将拦截伪造为 404。 |
| 网络失败重试 | BLOCKED | 当前受控浏览器未提供安全的网络故障注入或代理断连能力；未通过关闭服务或篡改请求制造失败。 |
| refresh 成功 | PASS | 每个已登录页面恢复均可观察到 `/auth/refresh` HTTP 200，随后 `/users/me` 和该页面业务 GET 正常返回。 |
| refresh 失败后的会话清理 | BLOCKED | 需要让 refresh Cookie 失效或修改认证状态，超出本轮只读边界。 |

### 16.4 Cookie 验证

- BLOCKED：当前受控浏览器能力不允许读取 Cookie 面板、Cookie 值或存储内容；为遵守敏感信息约束，没有尝试读取 Token、Cookie 或 Authorization。
- PASS（行为层）：登录态页面恢复过程实际观察到 refresh HTTP 200，随后用户与业务接口继续成功，说明当前会话链路可用。该行为不能替代 `HttpOnly`、`Secure`、`SameSite`、`Path` 的浏览器面板级属性验收。

### 16.5 后端测试环境

- PASS：`java -version` 和 `mvn -version` 均确认使用 JDK 21.0.11 与 Maven 3.9.16；此前 JDK 8 失败结果不纳入验收。
- BLOCKED：默认 `backend/target/classes` 被占用，Maven 资源复制失败。只读进程检查因系统拒绝访问无法获取 Java 命令行或进程归属。
- BLOCKED：使用项目既有 `.codex-build` 的全新目录后，Maven 仍在资源过滤复制 `application-test.yml` 时抛出 `AccessDeniedException`，未进入任何编译或测试断言。手工创建同一目录成功，说明是 Maven 执行环境限制；未停止任何 Java 进程、未删除文件、未修改 ACL。
- BLOCKED：对该 Maven 验证命令的受控权限提升未获执行环境批准，因此未采用替代绕过方式。后端定向单元测试和 Mapper/XML 测试没有可报告的通过数。

### 16.6 P3-05 收尾结论

- PASS：店铺授权数据补齐后的商家业务员登录态、店铺范围、工作台、8 个页面主要只读流程、刷新、指标跳转、403 权限拒绝、前端单元测试、前端生产构建、OpenAPI 静态检查和 `git diff --check`。
- BLOCKED：跨租户资源 404 的真实浏览器状态、401 与 refresh 失败清理、网络失败重试、Cookie 面板属性、后端 JDK 21 定向测试。
- P3-05 不可关闭，不进入 P3-06。须在可访问 Cookie/网络开发者面板且可触发详情请求的受控浏览器环境中补齐负向证据，并在可写 Maven 资源输出目录的环境中运行后端定向测试。

## 17. 2026-08-18 最终收尾状态

### 17.1 PASS

- PASS：当前测试商家操作员仅有 2 条 ACTIVE `sys_user_store_scope`，2 条均属于当前租户的 ACTIVE 店铺；`GET /api/v1/stores` 为 HTTP 200、`total=2`、`items=2`。
- PASS：`GET /api/v1/operations/workbench` 为 HTTP 200；8 项指标、6 类待办和风险提醒均为真实响应。
- PASS：8 个业务页面的只读浏览器复核结果见第 16.2 节；页面渲染、已发起的业务 GET、空/非空状态和可用刷新均按实际记录。
- PASS：使用新恢复的同源浏览器会话再次点击工作台仓库指标，实际进入 `/403` 且 403 页面正常渲染；控制台无 error/warning。
- PASS：前端单元测试 52/52、前端生产构建、OpenAPI 106 operation 和 `git diff --check` 已通过，结果见第 15.5 节。

### 17.2 BLOCKED 与未执行

| 项目 | 状态 | 原因 |
|---|---|---|
| 跨租户/未授权店铺 404 浏览器证据 | BLOCKED | 同源应用没有店铺详情入口；直接 API 导航被浏览器客户端 `ERR_BLOCKED_BY_CLIENT` 拦截。未直接调用后端 API，也未将拦截写作 404。 |
| 401 会话过期和 refresh 失败清理 | BLOCKED | 制造该场景需要删除、失效或篡改真实会话；本轮不注销、不删 Cookie、不改本地存储。 |
| 网络失败重试 | BLOCKED | 当前受控浏览器未提供安全的网络故障注入能力；未关闭服务或修改代理。 |
| Cookie 面板属性 | BLOCKED | 当前浏览器能力仅暴露可见性、视口、页面资源和 CDP，不提供 Cookie 面板或 Cookie 存储读取。未读取 Cookie、Token、Authorization 或密码。源码配置仅保留为辅助证据，不代表浏览器属性验收通过。 |
| 后端 Store、Operations、Mapper/XML 定向测试 | BLOCKED | JDK 21.0.11 与 Maven 3.9.16 已确认；Maven 在资源过滤复制阶段对默认 `target/classes` 和预创建的 `.codex-build` 临时目录均出现 `AccessDeniedException`，未进入测试断言。Java 进程数量可见但系统拒绝读取命令行/归属，不能安全停止任一进程；受控权限提升未获执行环境批准。 |

### 17.3 最终结论

- P3-05 仍然阻塞，不关闭，不进入 P3-06。
- 未执行数据库写入、Flyway、顺丰生产调用、权限数据修改、`StoreMapper.xml` 修改、tenant-only 查询恢复、文件删除、Git commit 或 push。

## 13. 2026-08-17 权限边界和环境复核（最新）

### 13.1 业务结论与修复

- `GET /api/v1/warehouse/overview` 要求 `scope:TENANT + warehouse:manage`。商家业务员当前角色矩阵不包含 `warehouse:manage`，所以 403 符合权限设计；前端继续由权限矩阵隐藏仓库入口，后端保持 403。本轮没有扩大角色权限，也没有直接改数据库权限。
- 当前账号的脱敏权限检查结果仍为租户范围 `MERCHANT_OPERATOR`：JWT 中存在 `tenant_id`，并用于工作台和店铺服务；权限包含 `operations:read`、`store:read` 等读取权限，不包含 `warehouse:manage`。完整 tenant ID、令牌、Cookie、认证头均不写入本文件。
- `GET /api/v1/operations/workbench` 的可见店铺由 `tenant_id + user_id + 角色 + active sys_user_store_scope` 决定。`storeIds=0` 时，既有 Service 返回真实的零指标、空待办/订单/风险集合，不拒绝访问；这不是静态假数据，也不是把权限问题转换为 500。
- 复核发现并修复店铺列表/详情的权限缺口：原 `GET /api/v1/stores` 和详情 SQL 仅按 `tenant_id`，业务员可看到本租户未授权店铺。现改为 `findByIdForUser`、`pageForUser`、`countForUser`，统一校验用户有效性、租户、租户级角色或 `MERCHANT_OPERATOR` 的 active `sys_user_store_scope`，并保留 `deleted=0`、店铺有效状态和筛选/分页条件。更新与状态变更也先执行同一资源归属检查。
- 因此，修复前的实际店铺列表 tenant/store 隔离结果为“缺失”；修复后的代码和定向测试结果为“查询路径已按 tenant_id/store_id 授权过滤”。本轮未连接数据库，未用业务库重读授权行，故不把代码测试写成生产数据库行级验收。

### 13.2 本轮真实浏览器边界

- 已连接受控 In-App Browser 并打开 `http://localhost:5173/login`，登录页正常渲染。直接访问 `http://localhost/app` 被浏览器客户端拦截，改用 Vite 登录入口后可访问。
- 当前 Codex shell 进程对 `SHIPFLOW_TEST_PASSWORD` 的存在性检查结果为 missing；没有读取、输出、猜测或反推密码。因此本轮没有执行真实商家业务员登录，也没有把未登录的 8 个页面检查冒充为已通过。
- 未登录条件下，不能新增确认 8 个页面的真实业务响应、控制台错误、指标跳转/筛选/刷新、401/403/网络失败重试、浏览器 Cookie 属性和回送行为。既有已记录的登录态只读证据继续保留，但本节不扩大其覆盖范围。

### 13.3 本轮修改文件

- `backend/src/main/java/com/shipflow/store/api/StoreController.java`
- `backend/src/main/java/com/shipflow/store/application/StoreApplicationService.java`
- `backend/src/main/java/com/shipflow/store/mapper/StoreMapper.java`
- `backend/src/main/resources/mapper/store/StoreMapper.xml`
- `backend/src/test/java/com/shipflow/store/StoreApplicationServiceTest.java`
- `backend/src/test/java/com/shipflow/store/StoreControllerWebMvcTest.java`
- `backend/src/test/java/com/shipflow/store/StoreMapperXmlTest.java`

未修改数据库、Flyway、前端权限矩阵、顺丰接口或业务数据。

### 13.4 验证结果

- Java/Maven 环境：显式设置 `JAVA_HOME=C:\Program Files\Java\jdk-21.0.11` 后，`java -version` 和 `mvn -version` 均确认 Java `21.0.11`；Maven `3.9.16` 使用该 JDK。先前误用 JDK 8 的失败结果不计入验收。
- 店铺权限定向后端测试：10/10 通过；JDK 21 编译通过。
- 前端 `npm run test:unit`：52/52；`npm run build`：通过。
- OpenAPI/只读 HTTP 检查：106 个 operation；匿名 GET 56/56；写操作 50 个全部跳过；因当前进程没有密码，登录及认证页面检查为 BLOCKED，不能记为通过。
- `git diff --check`：通过；仅有工作区既有 LF/CRLF 转换 warning，无空白错误。

### 13.5 未执行与阻塞

- 未执行数据库查询、数据库写入、Flyway/V018/V019、业务数据创建/修改/删除、顺丰生产调用、Git commit/push。
- 未完成真实浏览器登录及登录后的 8 个页面完整复测；未完成刷新 Cookie 的浏览器面板级属性/回送验证，以及登录态下的 401/403/网络失败重试实测。
- P3-05 继续阻塞，不进入 P3-06；恢复条件是同一受控验收进程可安全读取测试密码并完成真实登录，随后补齐上述浏览器证据。
