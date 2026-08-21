# 商家业务员工作台第三阶段 P3-06b 跳转与筛选闭环

更新时间：2026-08-18（Asia/Shanghai）

## 1. 目标与边界

本子阶段只处理运营工作台目标到现有业务页面的真实导航、URL query 恢复和目标页面筛选初始化。继续使用后端返回的工作台快照，不在前端计算指标、不创建静态数据、不改变后端认证、租户、店铺或角色权限校验。

流程为：工作台目标先通过前端路由与 query 白名单，再由目标页面解析合法单值参数，最后将可表达的状态传给现有真实 API。非法或后端尚未支持的聚合条件只显示中文说明，不进行前端伪过滤。

## 2. 已实现映射

| 工作台入口 | 目标页面 | query 恢复 | 真实 API 处理 |
|---|---|---|---|
| 待处理订单 | `/app/orders` | `resourceType`、`storeId` | 订单状态未由当前目标精确给出，保留真实授权范围列表并提示 |
| 待确认费用 | `/app/orders` | `status=PENDING_FEE_CONFIRMATION` | 映射为订单 API 支持的 `PENDING_PRICE_CONFIRMATION` |
| 风险订单 | `/app/orders` | `resourceId` | 映射为订单详情查询 ID，调用既有 `GET /orders/{id}` |
| 待入库 | `/app/warehouse` | `status=PENDING_INBOUND` | 调用既有仓库工作列表 API |
| 待复称 | `/app/warehouse` | `status=INBOUND` | 调用既有仓库工作列表 API |
| 对账确认 | `/app/billing` | `tab=reconciliations`、`status=PENDING_CONFIRMATION` | 调用既有对账列表 API |
| 账单导入错误 | `/app/billing` | `tab=details`、`status=ERROR` | 调用既有账单明细 API |
| 异常财务复核 | `/app/exceptions` | `status=PENDING_FINANCE_REVIEW` | 映射为 `PENDING_FINANCE_CONFIRMATION` |

所有目标路由继续经过现有 Router 权限守卫。当前商家业务员点击仓库目标时进入 `/403`，没有绕过 `warehouse:manage`。

## 3. 当前契约缺口

以下入口已保留真实路由和原始 query，但现有目标 API 没有等价筛选，前端明确提示并不伪造结果：

- `PENDING_LABEL`、`PENDING_OUTBOUND`：仓库列表只按订单 `current_status` 查询，无法区分 `READY_FOR_OUTBOUND` 下的面单生命周期；后端需要后续提供正式 `labelStatus` 筛选。
- `IN_TRANSIT`、`TRACKING_EXCEPTION`：轨迹页面当前只支持按订单号或顺丰单号查询，没有状态聚合列表接口。
- `PENDING_EXCEPTION_FOLLOW_UP`、`MISSING_ADDRESS`、`MISSING_CUSTOMS_DOCUMENT`：现有异常、订单和仓库 API 没有对应的筛选枚举。

这些缺口不能通过本阶段的前端本地过滤解决，否则会破坏工作台统计与列表一致性。

## 4. 修改文件

- `frontend/src/views/workbenchTargetFilters.ts`
- `frontend/src/views/workbenchTargetFilters.spec.ts`
- `frontend/src/views/operationsWorkbench.ts`
- `frontend/src/views/operationsWorkbench.spec.ts`
- `frontend/src/views/WorkflowView.vue`
- `frontend/src/views/WarehouseWorkView.vue`
- `frontend/src/views/TrackingView.vue`
- `frontend/src/views/FinanceView.vue`

## 5. 测试与浏览器证据

- 前端单元测试：PASS，57/57。
- 前端生产构建：PASS，`vue-tsc -b` 与 Vite 构建通过。
- 工作台订单指标：PASS，真实点击到 `/app/orders?resourceType=SHIPMENT_ORDER`；刷新后 URL 和提示保留；返回回到 `/app`。
- 费用待办：PASS，真实点击到 `/app/orders?resourceType=PENDING_FEE_CONFIRMATION&status=PENDING_FEE_CONFIRMATION`，页面按真实订单接口映射费用确认状态。
- 风险提醒：PASS，真实点击到 `/app/orders?resourceId=35`，触发订单详情查询，页面出现详情区域且无业务错误。
- 仓库指标：PASS（权限边界），真实点击进入 `/403`，符合当前账号缺少 `warehouse:manage`；未绕过后端权限。
- 浏览器控制台：PASS，以上应用页面未产生业务 error/warning；浏览器外部统计网络超时不属于 ShipFlow 页面业务错误。
- OpenAPI 静态检查：PASS，106 个 `operationId`、无重复，`getOperationsWorkbench` 存在。
- `git diff --check`：PASS；只有既有工作区的 LF/CRLF 提示，没有空白错误。

## 6. 未解决与未执行

- 未修改后端 API，因此上述聚合状态缺口仍需后续后端契约阶段确认，P3-06b 不能宣称所有工作台入口均已做到精确列表一致。
- 未执行数据库写入、Flyway、顺丰生产调用、Git commit 或 push。
- 401、网络失败和 refresh 失败清理沿用 P3-05 的环境 BLOCKED 结论，本阶段没有破坏或伪造这些证据。

结论：P3-06b 的前端安全导航和可表达筛选已完成；在补齐轨迹状态列表、面单生命周期和缺失筛选 API 前，不建议将“全部入口精确闭环”作为已完成，也不直接进入 P3-06c。
