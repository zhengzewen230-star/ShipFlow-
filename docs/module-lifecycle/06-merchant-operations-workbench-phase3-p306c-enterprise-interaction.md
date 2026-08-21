# P3-06c 企业级真实数据展示与交互

更新时间：2026-08-18（Asia/Shanghai）

## 本轮范围

本轮仅完善已有真实读取契约的页面交互，不新增数据库结构、迁移或 tenant-only 查询。所有时间仍由后端按 UTC 返回，前端使用 `Asia/Shanghai` 展示。

| 页面 | 已完成的真实交互 | 权限与数据边界 |
|---|---|---|
| 订单 | `workbenchFilter`、店铺和订单详情筛选保留在 URL；读取失败可重试、显示 Trace ID 并复制 | `GET /orders` 保留 `tenant_id + user_id + active sys_user_store_scope`；待确认费用、待补地址使用 P3-06b 白名单 |
| 异常与索赔 | 待异常跟进使用 `PENDING_FOLLOW_UP`；空状态、失败重试和 Trace ID 统一呈现 | 异常列表继续复用调用方可见店铺谓词 |
| 轨迹 | 手工输入订单号/运单号会回写 `reference` query，刷新与返回可恢复；真实时间线、空状态、失败重试和 Trace ID | 仅查询已有的订单号/运单号时间线，不将聚合指标伪装成列表 |
| 仓库 | 状态、订单号和页码回写 URL；概览 403 不再遮蔽列表；无面单筛选契约的“待打单/贴标”保持禁用提示 | 商家操作员访问仓库路由仍由前端权限矩阵和后端校验拒绝；未放宽仓库 API 的 tenant/store 边界 |
| 账单与对账 | tab 和状态筛选回写 URL；真实空状态、失败重试和 Trace ID 统一呈现；写操作继续通过 `useSubmit` 防重复 | 页面不为商家伪造账单聚合；现有账单/对账接口的 scope 缺口仍保留 |

## 统一交互

`DataState` 现支持读取失败后的中文重试和后端 Trace ID 复制。错误文案继续由统一 HTTP 映射处理 401、403、404、409、422 与网络/5xx；提交类操作继续复用 `useSubmit`，避免重复提交。

## 浏览器只读验收

使用当前受控浏览器和现有商家业务员会话：

| 项目 | 结果 | 证据 |
|---|---|---|
| 工作台 -> 待确认费用 | BLOCKED | URL 与前端 query 正确为 `status=PENDING_FEE_CONFIRMATION`，但运行中后端返回了未筛选订单；源码和 JDK 21 定向测试已包含 `workbenchFilter`，当前运行实例未加载该契约，不能将其写成通过 |
| 轨迹时间线 | PASS | 订单参考号查询返回 3 个真实仓内节点，页面按 Asia/Shanghai 显示，无控制台 error/warning |
| 异常跟进 | PASS | `PENDING_EXCEPTION_FOLLOW_UP` 回显为真实空状态，无控制台 error/warning |
| 对账筛选 | PASS | `tab=reconciliations&status=PENDING_CONFIRMATION` 保持并回显为真实空状态，无控制台 error/warning |
| 仓库页面 | PASS（预期拒绝） | 商家业务员访问 `/app/warehouse` 被路由导向中文 403 页面；未绕过权限 |

## 仍未支持的项目

- 待贴标、待出库、待补清关资料、待仓库处理：仓库列表尚无安全的调用方店铺范围筛选。
- 在途订单、轨迹异常：仅有时间线查询，尚无调用方范围内的轨迹聚合列表。
- 账单导入错误和财务聚合：错误账单行不一定关联订单，现有列表未能按商家店铺可靠隔离。

以上项目继续显示中文不可用说明，不使用前端本地筛选、静态数量或伪造响应。

## 修改文件与验证

- 前端：`DataState.vue`、`WorkflowView.vue`、`TrackingView.vue`、`WarehouseWorkView.vue`、`FinanceView.vue`。
- 未修改后端业务逻辑、数据库、Flyway、顺丰生产配置或店铺权限 SQL。
- 前端单元测试 `57/57` 通过，生产构建通过。
- JDK 21.0.11 后端定向 Controller、Service、Mapper/XML 测试 `47/47` 通过，使用独立 `.codex-build/p306c-contract-20260818` 输出目录。
- OpenAPI 静态检查：106 个 operationId，106 个唯一，订单、异常、仓库、轨迹、对账路径及 P3-06b 筛选参数均存在。
- `git diff --check` 通过；输出仅为既有工作区的 LF/CRLF 转换提示。

## 下一步

在部署/重启已包含 P3-06b 的后端实例后，重新进行工作台到订单筛选的真实浏览器验收。仓库、轨迹和账单聚合筛选需先补足 caller/store scoped 后端契约，才可进入后续导航闭环。

## P3-06c 运行时复验（2026-08-18，Asia/Shanghai）

### 重启结论

| 项目 | 结果 | 证据与结论 |
|---|---|---|
| 运行中旧实例 | 已定位 | Nginx `80` 的上游为本机 `8080`；监听 `8080` 的 JDK 21.0.11 Java 进程被确认是本次待重启的 ShipFlow 后端。未停止前端、Nginx 或来源无法确认的其他 Java 进程。 |
| 受控重启 | PASS | 已提供 `local-secrets` 下的 JWT 公私钥文件；使用项目既有 `mvnw.cmd spring-boot:run`、JDK 21.0.11 启动后端，当前 PID `22512` 监听 `8080`，健康检查 HTTP 200。Flyway 保持关闭，未停止前端、Nginx 或其他非本任务进程。 |
| JWT 配置 | PASS | 两份密钥文件仅做存在性/文件元数据检查，未读取或输出内容；认证登录态可由受控浏览器恢复。 |
| 运行时筛选复验 | PASS | 工作台“待确认费用”真实返回数量 `3`；页面跳转 URL 为 `/app/orders?resourceType=PENDING_FEE_CONFIRMATION&status=PENDING_FEE_CONFIRMATION`；真实请求为 `GET /api/v1/orders?workbenchFilter=PENDING_FEE_CONFIRMATION`，HTTP 200，响应 `total=3/items=3`。返回订单主状态可包含 `DELIVERED`，因为该筛选依据为 `fee_adjustment.confirmation_status IN ('PENDING_CONFIRMATION','REQUESTED')`，不是订单主状态；因此与工作台指标一致。 |

### 可重复的静态契约验证

使用 JDK 21.0.11，并以独立 `shipflow.build.directory` 输出目录避开被占用的 `backend/target/classes`：

| 测试范围 | 结果 |
|---|---|
| 订单 Controller、查询 Service、Mapper/XML；异常 Controller、Service、Mapper/XML | PASS，`37/37`，失败 `0`、错误 `0`、跳过 `0` |

该测试覆盖 `GET /api/v1/orders?workbenchFilter=PENDING_FEE_CONFIRMATION` 的 Controller、白名单解析、调用者范围参数和 SQL 映射，但不替代需 JWT 配置和真实登录态的浏览器验收。

### 浏览器复验补充

| 检查项 | 结果 | 证据 |
|---|---|---|
| 页面刷新和筛选回显 | PASS | 刷新后 URL 仍保留 `resourceType=PENDING_FEE_CONFIRMATION&status=PENDING_FEE_CONFIRMATION`，再次“查看列表”仍发出同一真实筛选请求。 |
| 非法 query | PASS | `status=INVALID_FILTER` 未被发送为后端业务筛选，页面显示中文安全提示并仅展示授权范围内真实订单；无前端假过滤。 |
| 页面错误 | PASS | 订单页刷新后控制台 error/warning 数量为 0；请求返回 HTTP 200。 |
| Trace ID/失败重试 | BLOCKED | 本次真实数据路径未产生失败响应，无法在不破坏当前会话和业务数据的前提下安全制造网络失败/500；代码和单测已覆盖统一错误显示与重试。 |

### 后续最小环境动作

由持有本地认证密钥配置的受控启动流程提供 JWT 私钥/公钥位置（及启用 refresh 时的 HMAC），再以同一 JDK 21.0.11 启动后端。启动健康检查为 200 后，重新登录并只读验证：工作台“待确认费用”跳转、`workbenchFilter=PENDING_FEE_CONFIRMATION` 的真实请求与结果、刷新后的 URL 回显，以及轨迹、异常、对账和仓库 403 不回归。
