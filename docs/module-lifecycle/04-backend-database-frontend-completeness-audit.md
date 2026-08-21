# ShipFlow 后端、数据库与前端完整对接审计

## 1. 审计范围

- 审计日期：2026-08-15（Asia/Shanghai）。
- 后端：`backend/src/main/java`、`backend/src/main/resources`，包括 Controller、Application Service、Domain、Mapper 和 Mapper XML。
- 契约：`openapi/shipflow-api.yaml`。
- 数据库：`database/schema.sql`、`database/init_data.sql`、`database/migrations/`，只读扫描 SQL，不连接数据库、不执行迁移。
- 前端：`frontend/src/router`、`views`、`components`、`layouts`、`stores`、`services`、`navigation`、测试与权限守卫。

本次审计在既有入驻/轨迹接入基础上补齐了财务工作台的真实账单与对账查询入口，并核对了财务角色权限边界；没有新增权限码、接口或数据库表。

## 2. 数量盘点

| 项目 | 数量 | 说明 |
|---|---:|---|
| 后端 Controller | 19 | `backend/src/main/java` 中的 `*Controller.java` |
| 后端映射操作 | 92 | 以实际 `@GetMapping`/`@PostMapping`/`@PutMapping`/`@PatchMapping`/`@DeleteMapping` 计数 |
| OpenAPI operationId | 93 | 契约当前声明数量 |
| 后端 Service | 27 | `*Service.java` 与 `*ApplicationService.java` |
| 后端 Mapper | 33 | Java Mapper 与 XML 对应业务持久化边界 |
| 数据库业务表（去重） | 33 | 基础 schema 30 张，加 V007 的 3 张；V002 重复声明已去重 |
| 前端页面视图 | 14 | 原有 13 个，加本次 `/activate` |
| 前端路由记录 | 25 | `path` 属性数量，包含布局与兜底路由 |
| 前端 typed service 文件 | 18 | `frontend/src/services` |
| 控制台菜单项 | 15 | `consoleNavigationItems` |
| 前端 service 导出函数 | 102 | 含请求基础设施辅助函数 |
| 在 Vue 页面中实际调用的 service 导出 | 49 | 未使用导出见第 7 节 |

后端与 OpenAPI 的数量差异已缩小：此前后端额外存在但 OpenAPI 未声明的 `GET /api/v1/platform/onboarding-applications/{applicationId}` 已在本轮补入，同时新增了轨迹分页 Controller；物流商 HMAC 回调仍是非浏览器入口。

## 3. 后端接口按模块

| 模块 | Controller 操作 | OpenAPI 操作 | 主要前端入口 |
|---|---:|---:|---|
| 认证与当前用户 | 5 | 5 | `/login`、全局会话恢复 |
| 入驻与访客预估 | 10 | 9 | `/quote`、`/apply`、`/activate`、平台入驻/线索 |
| 平台租户 | 5 | 5 | `/app/tenants` |
| 店铺 | 5 | 5 | `/app/stores` |
| 用户 | 6 | 6 | `/app/users` |
| RBAC | 4 | 4 | `/app/rbac` |
| 物流基础资料 | 16 | 16 | `/app/logistics` |
| 报价 | 4 | 4 | `/app/quotes`、`/app/quotes/create` |
| 订单 | 6 | 6 | `/app/orders`、报价转订单 |
| 仓库 | 3 | 4 | `/app/warehouse` |
| 轨迹 | 4 | 4 | `/app/tracking` |
| 账单与对账 | 8 | 8 | `/app/billing` |
| 审计 | 4 | 4 | `/app/audit` |
| 异常与索赔 | 10 | 10 | `/app/exceptions` |
| 运营概览 | 2 | 2 | `/app` |

仓库和轨迹的 OpenAPI 分组包含路径归属差异：仓库 `confirmPrice` 属于订单费用确认；本轮已补齐轨迹分页查询 Controller，物流商回调仍保持 server-to-server。

## 4. OpenAPI 到前端映射

下表按 operationId 汇总；同一模块内列出的 operationId 均使用当前 `/api/v1` Axios client、`withCredentials`、内存 Bearer Token、写请求 CSRF 和幂等/请求 ID 处理。

| 模块 | operationId | service | 页面/动作 | 状态 |
|---|---|---|---|---|
| 认证 | `getCsrfToken`, `login`, `refreshToken`, `logout`, `getCurrentUser` | `auth.ts`、`csrf.ts` | 登录、会话恢复、退出 | 已接入 |
| 入驻 | `createGuestEstimateRequest` | `onboarding.ts` | `/quote` 访客预估 | 已接入 |
| 入驻 | `createOnboardingApplication` | `onboarding.ts` | `/apply` | 已接入 |
| 入驻 | `activateOnboardingAdministrator` | `onboarding.ts` | `/activate` | 本次补齐 |
| 入驻 | `listOnboardingApplications` | `onboarding.ts` | 平台入驻列表 | 已接入 |
| 入驻 | `getOnboardingApplication` | `onboarding.ts` | 平台入驻列表详情 | 本次补齐 |
| 入驻 | `approveOnboardingApplication`、`rejectOnboardingApplication` | `onboarding.ts` | 平台审核操作 | 本次补齐 |
| 线索 | `listGuestEstimateLeads`、`getGuestEstimateLead`、`updateGuestEstimateLeadStatus` | `onboarding.ts` | 平台线索列表、详情、状态 | 已接入 |
| 租户 | `createTenant`、`listTenants`、`getTenant`、`updateTenant`、`changeTenantStatus` | `tenants.ts` | `/app/tenants` | service 已接入；编辑/停用按钮缺失 |
| 店铺 | `createStore`、`listStores`、`getStore`、`updateStore`、`changeStoreStatus` | `stores.ts` | `/app/stores` | service 已接入；编辑/停用按钮缺失 |
| 用户 | `createUser`、`listUsers`、`getUser`、`updateUser`、`changeUserStatus`、`replaceUserRoles` | `users.ts` | `/app/users` | service 已接入；编辑/停用/角色重绑按钮缺失 |
| RBAC | `listRoles`、`getRole`、`replaceRolePermissions`、`listPermissions` | `rbac.ts` | `/app/rbac` | 查询已接入；权限绑定按钮缺失 |
| 物流 | `listLogisticsProviders`、`createLogisticsProvider`、`getLogisticsProvider`、`updateLogisticsProvider` | `logistics.ts` | `/app/logistics` | 列表已接入；平台维护表单缺失 |
| 物流 | `listLogisticsChannels`、`createLogisticsChannel`、`getLogisticsChannel`、`updateLogisticsChannel`、`replaceLogisticsChannelServiceCountries` | `logistics.ts` | `/app/logistics` | service 已接入；维护动作缺失 |
| 价格规则 | `listPublishedPriceRules`、`getPublishedPriceRule`、`publishPriceRuleVersion` | `logistics.ts` | `/app/logistics` | service 已接入；规则详情/发布动作缺失 |
| 租户物流 | `listAvailableLogisticsChannels`、`getAvailableLogisticsChannel`、`getAvailableChannelServiceCountries`、`getEffectivePublishedPriceRule` | `logistics.ts` | 报价准备数据 | 列表/报价流程已接入；详情方法暂无独立入口 |
| 报价 | `listQuotes`、`createQuote`、`getQuote`、`validateQuote` | `quotes.ts` | 报价列表、创建、详情、校验 | 已接入 |
| 订单 | `createShipmentOrderFromQuote`、`listShipmentOrders`、`getOrder` | `orders.ts` | 报价转订单、订单列表/详情 | 已接入 |
| 订单 | `updateDraftShipmentOrder`、`submitOrder`、`cancelOrder` | `orders.ts` | 订单工作台 | service 已接入；草稿编辑表单缺失，提交/取消已接入 |
| 仓库 | `confirmInbound`、`submitMeasurement`、`confirmOutbound` | `warehouse.ts` | 仓库工作台 | 已接入 |
| 仓库 | `confirmPrice` | `warehouse.ts` | 仓库费用确认 | service 已接入；按钮/表单缺失 |
| 轨迹 | `listShipmentOrderTracking`、`getShipmentOrderTrackingStatus` | `tracking.ts` | 轨迹工作台 | 已接入 |
| 轨迹 | `listTrackingEvents` | `tracking.ts` | 订单详情轨迹面板 | 本次补齐 |
| 物流回调 | `receiveTrackingEvents` | 无浏览器 service | 物流商 HMAC server-to-server | 明确不进菜单 |
| 账单 | `importBillingCsv`、`listBillImportBatches`、`getBillImportBatch`、`listBillImportErrors`、`listBillDetails` | `billing.ts` | `/app/billing` 财务工作台 | 批次列表/详情、导入错误明细、账单明细查询已接入；CSV 导入按钮待后续补齐 |
| 对账 | `listReconciliations`、`getReconciliation`、`confirmReconciliationDifference` | `billing.ts` | `/app/billing` 财务工作台 | 列表、详情和差异确认已接入，写操作使用 CSRF、幂等键和版本号 |
| 费用调整 | （暂无查询 operation） | 无 | `/app/billing` | `fee_adjustment` 只有仓库复称产生/费用确认写入链路，后端未提供按租户查询接口；前端只展示契约缺口提示 |
| 审计 | `listTenantAuditLogs`、`getTenantAuditLog`、`listPlatformTenantAuditLogs`、`getPlatformTenantAuditLog` | `audit.ts` | 审计工作台 | 已接入 |
| 异常 | `listExceptionCases`、`getExceptionCase`、`createExceptionCase`、`assignExceptionCase`、`transitionExceptionCase` | `exceptions.ts` | 异常工作台 | 查询已接入；创建/分派/状态按钮缺失 |
| 索赔 | `createClaim`、`getClaim`、`submitClaim`、`resolveClaim`、`closeClaim` | `exceptions.ts` | 异常详情 | service 已接入；索赔动作缺失 |
| 运营 | `getOperationsSummary`、`getOperationsTodos` | `operations.ts` | 控制台概览 | 已接入 |

## 5. 数据库表、后端能力与前端入口

| 表 | 用途与关键关系 | 后端能力 | 前端入口 |
|---|---|---|---|
| `tenant` | 平台租户；被用户、店铺、订单等引用 | 租户 CRUD/状态 | `/app/tenants`，编辑/停用动作待补 |
| `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission` | 身份、角色、权限及绑定；平台/租户 scope | 用户与 RBAC Controller | `/app/users`、`/app/rbac`，绑定动作待补 |
| `merchant_store` | 租户店铺 | 店铺 CRUD/状态 | `/app/stores`，编辑/停用动作待补 |
| `logistics_provider`、`logistics_channel`、`logistics_channel_service_country` | 平台公共物流主数据 | 平台维护、租户只读 | `/app/logistics`，平台维护动作待补 |
| `price_rule`、`price_rule_tier` | 渠道发布价格规则及阶梯 | 查询/发布 | `/app/logistics`，发布动作待补 |
| `quote` | 租户正式报价；关联店铺、渠道、规则 | 创建/列表/详情/校验 | `/app/quotes`、`/app/quotes/create` |
| `shipment_order`、`shipment_quote_snapshot`、`shipment_address`、`shipment_package`、`shipment_item` | 订单及不可变报价快照、地址、包裹、商品 | 报价转订单、草稿、提交/取消 | `/app/orders`，草稿编辑待补 |
| `warehouse_measurement`、`fee_adjustment`、`warehouse_outbound_record` | 入库复称、费用调整、出库 | 仓库作业、费用确认 | `/app/warehouse`，费用确认待补 |
| `tracking_event` | 物流商事件与订单轨迹 | HMAC 回调、租户查询 | `/app/tracking`、订单详情轨迹面板 |
| `exception_case`、`claim_record` | 异常与索赔状态机 | 查询、创建、分派、处理、索赔 | `/app/exceptions`，操作按钮待补 |
| `bill_import_batch`、`bill_detail`、`reconciliation_record` | 账单导入、明细和对账 | 导入、查询、确认 | `/app/billing`，批次/错误/明细/对账详情和确认已接入 |
| `fee_adjustment` | 仓库复称产生的订单费用调整 | 仅由仓库服务写入并确认；无租户查询接口 | `/app/billing` 显示未提供查询契约，不展示伪造数据 |
| `audit_log` | 租户/平台操作审计，`tenant_id` 可为空 | 租户/平台查询 | `/app/audit` |
| `api_idempotency_record`、`auth_refresh_session` | 幂等和刷新会话基础设施 | service/拦截器内部使用 | 不进入业务菜单 |
| `guest_estimate_lead` | 未登录访客预估线索，不创建 quote/order/tenant | 公开提交、平台查询和状态 | `/quote`、平台线索 |
| `merchant_onboarding_application`、`onboarding_invitation` | 入驻申请、一次性激活邀请 | 公开申请、平台审核、激活 | `/apply`、平台入驻、`/activate` |

所有租户业务表均通过 `tenant_id` 做数据范围约束；公共物流主数据不带租户字段；用户、角色和审计支持平台 scope。版本字段用于乐观锁，删除字段/时间用于软删除或生命周期过滤。

## 6. 角色与权限矩阵

菜单隐藏不是授权边界，后端 `SecurityConfig` 和实时 `permissions` 仍是最终判定。

| 身份 | scope | 前端可见菜单 | 主要权限/接口 | 禁止范围 |
|---|---|---|---|---|
| 平台超级管理员 | `PLATFORM` | 概览、租户管理、商户入驻申请、访客预估线索、物流基础资料、平台审计 | `tenant:*`、`logistics:*`、`price-rule:manage`、`audit:read`；平台路径 | 不进入租户报价/订单/仓库/账单业务 |
| 租户管理员 | `TENANT` | 概览、用户、店铺、角色与权限、物流渠道、报价、订单、仓库、轨迹、异常、账单、审计 | 由实时 `permissions` 决定；常见为 `user:*`、`store:*`、`quote:*`、`order:*` 等 | 平台租户、平台线索、平台物流维护 |
| 商户业务人员 | `TENANT` | 报价、订单、物流渠道、轨迹、异常；具体按权限 | `quote:read/create/validate`、`order:read/create/manage`、`tracking:read`、`exception:*` | 用户/RBAC、平台数据、仓库和财务写操作 |
| 财务人员 | `TENANT` | 账单与对账（批次、错误明细、账单明细、对账列表/详情/确认） | `billing:read`、`finance:bill-import`、`finance:reconcile`；只有额外授予 `audit:read` 时才显示财务审计记录 | 订单履约状态、平台主数据、无授权的审计记录 |
| 仓库操作员 | `TENANT` | 订单只读、仓库、轨迹 | `order:read`、`warehouse:manage`、`tracking:read` | 报价创建、财务对账、异常审批 |
| 客服/异常专员 | `TENANT` | 轨迹、异常与索赔 | `tracking:read`、`exception:read/manage` | 报价定价、仓库、账单 |
| 物流服务商/承运商 | 无浏览器租户 scope | 不显示控制台菜单 | 仅通过 provider HMAC `receiveTrackingEvents` server-to-server | 不调用 JWT 租户页面接口 |
| 访客/潜在商户 | 未认证 | 首页、在线预估、入驻申请、激活 | 仅公开 POST 预估/入驻/激活 | 正式 quote、order、tenant、store、channel |

无权限时前端路由进入 `/403`，API 仍必须返回 `403 / COMMON-1004`；未登录返回 `401 / COMMON-1002`，不能通过隐藏菜单绕过后端校验。

## 7. 差异清单与优先级

### P0：契约一致性

本轮已收口两个 P0 缺口：

1. `GET /api/v1/orders/{orderId}/tracking-events` 已由分页 Controller、`scope:TENANT` + `tracking:read` 权限、租户订单归属校验、显式列 Mapper SQL 和订单详情轨迹面板接通。
2. `GET /api/v1/platform/onboarding-applications/{applicationId}` 已补入 OpenAPI，并由平台入驻列表详情按钮调用。

### P1：已有接口但缺少用户操作入口

1. 平台物流商、渠道、服务国家和价格规则的创建/编辑/发布动作。
2. 租户店铺、用户、角色权限绑定和租户状态维护动作。
3. 订单草稿编辑和仓库费用确认。
4. 账单导入、账单明细、对账差异确认。
5. 异常创建/分派/状态处理和索赔提交/审核/关闭。

这些接口已经有 typed service，但当前工作台只提供查询或列表；下一轮应按模块逐一增加表单、版本号、幂等键、防重复提交和错误状态。

### P2：体验与数据展示

1. 各列表补齐服务端分页、筛选、搜索和详情入口，而不是仅使用默认第一页。
2. 统一状态枚举、国家、运输方式和错误码的中文展示。
3. 平台审计在未选择 `tenantId` 时应明确提示选择租户，而不是静默空列表。

### 财务角色专项核对

| 业务要求 | 后端能力与权限 | 前端实现 | 状态 |
|---|---|---|---|
| 账单批次列表 | `GET /api/v1/billing/import-batches`；租户范围；`billing:read`/财务读权限 | `FinanceView`“账单批次”页签 | 已接入 |
| 账单批次详情 | `GET /api/v1/billing/import-batches/{batchId}` | 批次详情面板 | 已接入 |
| 账单导入错误明细 | `GET /api/v1/billing/import-batches/{batchId}/errors` | 批次详情“导入错误明细”表格 | 已接入 |
| 账单明细查询 | `GET /api/v1/billing/details` | “账单明细”页签及状态筛选 | 已接入 |
| 费用对账列表 | `GET /api/v1/reconciliations` | “费用对账”页签及状态筛选 | 已接入 |
| 对账详情 | `GET /api/v1/reconciliations/{reconciliationId}` | 对账详情面板 | 已接入 |
| 确认对账差异 | `POST /api/v1/reconciliations/{reconciliationId}/confirm`；`finance:reconcile`；版本号/幂等/CSRF | 对账详情确认表单，重复提交受 `useSubmit` 保护 | 已接入 |
| 查询订单费用调整 | 当前无查询 operation；`fee_adjustment` 只有仓库写入链路 | 页面明确显示“后端尚未提供正式接口”，不调用伪接口 | 后端缺口，P1 |
| 查看授权范围内财务审计记录 | `GET /api/v1/audit-logs`；`scope:TENANT` + `audit:read` | 有 `audit:read` 时显示“财务审计记录”页签；当前 V011 的 `FINANCE_OPERATOR` 未绑定该权限 | 权限前置条件缺口，P1 |

财务页面统一覆盖加载中、空数据、请求失败、无权限和成功状态；列表查询默认使用服务端分页参数，详情和确认错误通过统一 API 错误转换显示中文提示。账单、对账 SQL 由后端按 `tenant_id` 过滤，跨租户 ID 不会通过前端参数绕过服务层校验。

## 8. 本次已完成

- `frontend/src/services/onboarding.ts`：补齐 `activateOnboardingAdministrator`、`approveOnboardingApplication`、`rejectOnboardingApplication` 及其契约类型。
- `frontend/src/views/ActivationView.vue`：新增公开激活页，只提交 `invitationToken` 和 `password`，不读取 Token/Cookie。
- `frontend/src/router/index.ts`：新增 `/activate` 路由。
- `frontend/src/views/PlatformOnboardingView.vue`：增加平台审核通过/驳回动作、版本号提交、审核信息表单和一次性邀请令牌提示。
- `frontend/src/services/onboarding.ts`：接入 `getOnboardingApplication`；平台入驻列表增加详情查询、加载、空数据、无权限和失败状态。
- `frontend/src/views/WorkflowView.vue`、`frontend/src/services/tracking.ts`：订单详情展示轨迹加载中、空数据、失败和事件列表。
- `backend/src/main/java/com/shipflow/tracking/`：新增 `TrackingEventsController`、分页只读 DTO/Domain 投影和 tenant-scoped Mapper 查询。
- `backend/src/main/java/com/shipflow/security/SecurityConfig.java`：新增 `tracking:read` + `scope:TENANT` 的分页轨迹路径规则。
- `backend/src/test/java/com/shipflow/tracking/`：新增分页轨迹 Service/MockMvc、权限拒绝和 Mapper XML 静态测试。
- `frontend/src/services/onboarding.spec.ts`：验证公开激活、平台审核/驳回、访客线索列表/详情/状态接口的真实方法、路径和写请求头。
- `frontend/src/views/FinanceView.vue`：新增财务工作台，接通账单批次/错误/明细、对账列表/详情/确认和按授权显示审计记录，并对 `fee_adjustment` 查询缺口作明确提示。
- `frontend/src/services/billing.ts`、`frontend/src/services/billing.spec.ts`：补齐账单响应类型、批次/错误/明细/对账 typed service 及方法、路径、CSRF/幂等请求头测试。
- `frontend/src/navigation/console.ts`、`frontend/src/navigation/console.spec.ts`、`frontend/src/router/index.ts`：财务权限可见 `/app/billing`，不展示平台、用户、店铺、仓库和异常菜单；路由继续由 scope/permission 守卫保护。
- `backend/src/main/java/com/shipflow/security/SecurityConfig.java`、`backend/src/test/java/com/shipflow/billing/BillingControllerWebMvcTest.java`：账单/对账查询允许财务读权限，对账确认仍要求 `finance:reconcile`；覆盖财务访问、无权限 403、跨租户统一 404 和版本冲突响应。
- `openapi/shipflow-api.yaml`：补充 `getOnboardingApplication`，并保留 `listTrackingEvents` 的分页响应契约。
- 未修改数据库、迁移、Nginx、Jenkins 或 API 自动化；未连接数据库、未执行迁移、未提交 Git。

## 9. 验证

- `npm run test:unit`：通过，7 个测试文件、19 个测试全部通过（包含财务导航和 billing service 契约测试）。
- `npm run build`：通过，`vue-tsc -b` 与 `vite build` 均成功。
- 后端：使用 `C:\Program Files\Java\jdk-21.0.11` 执行 `mvn "-Dshipflow.build.directory=D:\\Projects\\shipflow\\target-codex-finance" "-Dtest=Billing*Test" test`，19 个账单/对账测试全部通过。默认终端 `JAVA_HOME` 为 JDK 8 时会因 `record` 语法编译失败，未修改系统环境；验收命令显式使用 JDK 21。
- OpenAPI 静态检查：已核对 `listTrackingEvents`、`getOnboardingApplication` 的 path、operationId、成功 schema 和 401/403/404 响应声明；未执行真实 HTTP/OpenAPI 服务器验证。
- `git diff --check`：通过。
- 数据库：只读扫描 SQL，未连接、未修改、未迁移。
- 敏感信息：未新增密码、Token、Cookie、HMAC、私钥或真实数据库凭据；构建生成的 `dist`/临时产物不应纳入提交。
