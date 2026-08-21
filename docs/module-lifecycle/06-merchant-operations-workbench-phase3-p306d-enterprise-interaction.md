# 商家业务员工作台第三阶段 P3-06d 统一企业级交互

更新时间：2026-08-18（Asia/Shanghai）

## 1. 阶段边界与业务流程

本阶段只统一商家业务员工作台已有页面的读取、筛选、错误反馈和操作防重复体验，不新增业务状态、不修改数据库结构、不放宽租户或店铺权限。读取流程为：页面读取 URL 白名单参数 → service 调用真实 API → 页面按后端返回状态渲染加载/空数据/错误/详情；写入流程为：页面校验 → 高风险操作二次确认 → `useSubmit` 加锁并携带幂等配置 → 保留后端错误码和 Trace ID。

参与方和边界保持不变：商家业务员只能提交费用确认申请；财务人员最终确认费用，租户管理员作为备用审批人；所有订单、仓库、轨迹、异常和账单接口继续由后端校验 authentication、role、tenant_id、user/store scope 和资源归属。前端不根据颜色或本地数量推断后端业务状态。

## 2. 本阶段统一能力

| 能力 | 实现 | 适用页面 |
|---|---|---|
| 加载状态 | `DataState` 显示“正在加载”，刷新按钮在请求期间禁用 | 订单、仓库、轨迹、异常、账单、报价、店铺、物流 |
| 空数据 | 真实 `items=[]` 使用中文空状态；403 不再显示成空数据 | 上述列表 |
| API 错误 | `toApiError` 保留 HTTP 状态、后端 code、message、details 和 traceId；不静默吞掉非 2xx | 所有 service/page |
| 401 | 统一显示会话失效提示；已有 refresh 失败清理逻辑继续生效 | 读取和写入页面 |
| 403/404 | 显示中文权限/资源提示；路由权限不足进入 `/403` | 订单、仓库、轨迹、异常、账单及全局路由 |
| 409/422/5xx/网络 | 保留后端业务原因或统一中文重试提示；读取错误支持重试 | 读取和写入页面 |
| Trace ID | 读取错误和提交错误均展示追踪编号；`DataState` 支持复制失败提示 | 订单、仓库、轨迹、异常、账单、店铺等 |
| 防重复 | `DataState` 的重试锁、刷新期间禁用、`useSubmit` 提交锁 | 列表刷新和所有写操作 |
| 高风险确认 | 取消订单、费用最终确认、出库、顺丰取消、对账差异确认前二次确认 | 订单、仓库、账单 |
| URL 恢复 | 订单、仓库、轨迹、异常、账单使用白名单 query；非法值安全回退并显示中文说明 | 工作台入口和目标列表 |
| 时间 | 后端保持 UTC 存储/返回；店铺更新时间和页面业务时间以 `Asia/Shanghai` 展示 | 店铺、概览、订单、轨迹、账单 |

## 3. 修改文件

- `frontend/src/components/DataState.vue`：重试处理中、防重复点击、Trace ID 复制反馈。
- `frontend/src/composables/useSubmit.ts`：提交失败保留错误码和 Trace ID，重复提交直接阻止。
- `frontend/src/services/http.ts`：统一 401/403/404/409/422/5xx/网络错误中文映射，保留真实响应字段。
- `frontend/src/views/WorkflowView.vue`：订单/异常/账单等列表读取锁；订单轨迹子请求的错误码、Trace ID 和重试；高风险操作确认。
- `frontend/src/views/WarehouseWorkView.vue`：仓库列表读取锁；详情轨迹子请求的错误码、Trace ID 和重试；出库/顺丰取消确认。
- `frontend/src/views/FinanceView.vue`：财务列表刷新防重复；对账差异确认保持提交锁和二次确认。
- `frontend/src/views/DirectoryView.vue`：店铺/用户等目录刷新防重复；更新时间按 Asia/Shanghai 展示。
- `frontend/src/views/LogisticsView.vue`、`frontend/src/views/GuestEstimateLeadsView.vue`：沿用统一读取错误、Trace ID 和刷新状态。
- `frontend/src/components/ActionError.vue`：提供可复用的中文操作错误与 Trace ID 展示组件。
- `backend/src/main/java/com/shipflow/common/exception/GlobalExceptionHandler.java`：修正订单 `COMMON-1006` 的中文错误映射。
- `backend/src/test/java/com/shipflow/order/ShipmentOrderManagementControllerWebMvcTest.java`：补充订单 404 错误码和中文文案契约测试。

未修改 `StoreMapper.xml`、租户/店铺权限数据、数据库迁移、Flyway 配置或顺丰生产调用。

## 4. 错误与状态契约

订单、仓库、物流、异常和财务状态仍由后端字段直接返回。前端仅做白名单筛选和展示映射：

- 订单费用确认筛选使用后端 `workbenchFilter=PENDING_FEE_CONFIRMATION`，不是把订单主状态误当作费用状态。
- 仓库待贴标/待出库、轨迹聚合异常、清关资料缺失和账单聚合继续在后端契约缺失时显示中文不可用提示，不做本地假筛选。
- `COMMON-1006` 的订单详情 404 统一显示“订单不存在或当前租户无权访问”；后端仍用统一 404 隐藏跨租户资源存在性。
- `COMMON-1005`、`COMMON-1010` 保留并发/处理中语义；账单和顺丰错误保留各自真实业务码。

## 5. 浏览器只读验收

使用当前受控浏览器的真实商家操作员会话，未输出密码、Token、Cookie、Authorization 或完整敏感 ID。

| 页面/操作 | 结果 | 实际证据 |
|---|---|---|
| 订单列表 | PASS | `/app/orders?resourceType=PENDING_FEE_CONFIRMATION&status=PENDING_FEE_CONFIRMATION` 正常渲染；真实筛选运行实例返回 HTTP 200、`total=3/items=3`，页面显示 3 条授权范围内订单；控制台 error/warning 为 0。 |
| 订单筛选刷新 | PASS | 直接以同一 URL 重新加载后 query 仍保留，页面仍加载真实列表；不使用前端本地数量。 |
| 轨迹 | PASS | `/app/tracking?resourceType=TRACKING&status=IN_TRANSIT` 正常渲染，显示“仅支持按订单号或顺丰单号查询”的真实契约提示，没有伪造聚合列表；控制台 error/warning 为 0。 |
| 异常与索赔 | PASS | `/app/exceptions?status=PENDING_EXCEPTION_FOLLOW_UP` 正常渲染真实空数据“暂无数据”，query 回显，控制台 error/warning 为 0。 |
| 账单与对账 | PASS | `/app/billing?tab=reconciliations&status=PENDING_CONFIRMATION` 正常选中“费用对账”，显示真实空状态和后端契约缺失说明，query 回显，控制台 error/warning 为 0。 |
| 仓库权限 | PASS | 商家操作员访问 `/app/warehouse` 按前端权限矩阵导向 `/403`；后端仓库概览仍保持 403，未放宽权限。 |
| 资源 404 | PASS | 后端重启后，浏览器访问不存在订单详情真实显示“订单不存在或当前租户无权访问”、追踪编号和“重试”；旧的“报价不存在或无权访问”文案不再出现，控制台 error/warning 为 0。 |
| 401/refresh 失败清理 | BLOCKED | 不注销或破坏当前会话，未安全制造 401/refresh 失败；源码保留 refresh 失败清理，单测覆盖统一错误映射。 |
| 网络失败/500 | BLOCKED | 不停止本地服务或伪造响应，未安全制造网络失败/500；统一重试和 Trace ID 由代码及单测覆盖。 |
| 409/422 | BLOCKED | 这些场景需要写操作或故意冲突；本阶段不写业务数据，使用前端错误映射和后端契约测试验证。 |
| 浏览器 Cookie 属性 | BLOCKED | 当前受控浏览器未开放安全的 Cookie 面板属性读取；不读取或输出 Cookie 值。 |
| 浏览器历史返回 | BLOCKED | 页面筛选 URL 可刷新恢复；订单“查看详情”在同一 URL 内展开，没有新增历史记录，无法把该操作伪造成可回退路由证据。 |

## 6. 测试与静态检查

| 检查 | 结果 |
|---|---|
| 前端单元测试 | PASS，15 个测试文件、64/64 |
| 前端生产构建 | PASS，`npm run build` |
| 后端 JDK | PASS，`java` 和 Maven 使用 JDK 21.0.11；未采纳 JDK 8 结果 |
| 后端定向测试 | PASS，39/39，失败 0、错误 0、跳过 0；独立输出目录 `.codex-build/p306d-contract-20260818` |
| OpenAPI | PASS，106 个 operationId，唯一 106，重复 0 |
| `git diff --check` | PASS，退出码 0；仅有既有 LF/CRLF 转换提示 |

本轮未连接数据库、未执行 Flyway、未调用顺丰生产接口、未修改权限数据、未提交或推送 Git。

## 7. 未解决问题与阶段结论

1. 401、refresh 失败清理、网络失败、500、409、422 和 Cookie 面板属性仍是环境/安全边界阻塞，不能写成浏览器通过。
2. 待贴标、待出库、轨迹聚合异常、清关资料缺失和账单聚合仍遵循 P3-06b/P3-06c 的“后端契约缺失”口径。

结论：P3-06d 的代码和可执行只读页面能力已完成；订单 404 已在后端重启后真实浏览器复验通过。无法安全制造的会话失效、网络失败、服务端失败、并发冲突和 Cookie 面板场景继续保留 BLOCKED，不改写为已验证通过。
