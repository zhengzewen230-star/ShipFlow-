# ShipFlow 前端 OpenAPI 覆盖清单

契约基线：`openapi/shipflow-api.yaml`，共 109 个 `operationId`。状态含义：

- **页面/动作**：控制台或登录流程已经直接调用。
- **已封装**：typed service 已实现，但当前页面还没有完整业务表单。
- **不由浏览器调用**：服务端到服务端回调，前端只展示其处理结果。

| operationId | 前端页面/动作 | service | 状态 |
|---|---|---|---|
| createGuestEstimateRequest | 访客预估页提交运输需求 | `onboarding.ts` | 页面/动作 |
| listGuestEstimateLeads | 平台访客预估线索列表、筛选和分页 | `onboarding.ts` | 页面/动作 |
| getGuestEstimateLead | 平台访客预估线索详情 | `onboarding.ts` | 页面/动作 |
| updateGuestEstimateLeadStatus | 平台访客预估线索状态处理 | `onboarding.ts` | 页面/动作 |
| createOnboardingApplication | 商户入驻申请页提交 | `onboarding.ts` | 页面/动作 |
| activateOnboardingAdministrator | 邀请激活流程 | `onboarding.ts` | 页面/动作 |
| listOnboardingApplications | 平台商户入驻申请列表 | `onboarding.ts` | 页面/动作 |
| getOnboardingApplication | 平台商户入驻申请详情 | `onboarding.ts` | 页面/动作 |
| approveOnboardingApplication | 平台审核通过并生成一次性邀请 | `onboarding.ts` | 页面/动作 |
| rejectOnboardingApplication | 平台驳回入驻申请 | `onboarding.ts` | 页面/动作 |
| getCsrfToken | 所有写请求自动获取或复用 | `csrf.ts` | 页面/动作 |
| login | 登录页 | `auth.ts` | 页面/动作 |
| refreshToken | 会话恢复、401 单次刷新 | `auth.ts` | 页面/动作 |
| logout | 控制台安全退出 | `auth.ts` | 页面/动作 |
| getCurrentUser | 登录后权限初始化 | `auth.ts` | 页面/动作 |
| createTenant | 租户管理新建 | `tenants.ts` | 页面/动作 |
| listTenants | 租户管理列表 | `tenants.ts` | 页面/动作 |
| getTenant | 租户详情 | `tenants.ts` | 已封装 |
| updateTenant | 编辑租户 | `tenants.ts` | 已封装 |
| changeTenantStatus | 启停租户 | `tenants.ts` | 已封装 |
| createStore | 店铺管理新建 | `stores.ts` | 页面/动作 |
| listStores | 店铺管理列表 | `stores.ts` | 页面/动作 |
| getStore | 店铺详情 | `stores.ts` | 已封装 |
| updateStore | 编辑店铺 | `stores.ts` | 已封装 |
| changeStoreStatus | 启停店铺 | `stores.ts` | 已封装 |
| getStoreDefaultAddress | 店铺默认发货地址 | `stores.ts` | 已封装 |
| updateStoreDefaultAddress | 配置店铺默认发货地址 | `stores.ts` | 已封装 |
| listStoreLogisticsChannels | 店铺可用物流渠道 | `stores.ts` | 已封装 |
| setStoreDefaultLogisticsChannel | 配置店铺默认物流渠道 | `stores.ts` | 已封装 |
| createUser | 用户管理新建 | `users.ts` | 页面/动作 |
| listUsers | 用户管理列表 | `users.ts` | 页面/动作 |
| getUser | 用户详情 | `users.ts` | 已封装 |
| updateUser | 编辑用户 | `users.ts` | 已封装 |
| changeUserStatus | 启停用户 | `users.ts` | 已封装 |
| replaceUserRoles | 绑定用户角色 | `users.ts` | 已封装 |
| listRoles | 角色与权限列表 | `rbac.ts` | 页面/动作 |
| getRole | 角色详情 | `rbac.ts` | 已封装 |
| replaceRolePermissions | 绑定角色权限 | `rbac.ts` | 已封装 |
| listPermissions | 权限选择数据 | `rbac.ts` | 已封装 |
| listLogisticsProviders | 平台物流资料列表 | `logistics.ts` | 页面/动作 |
| createLogisticsProvider | 新建物流商 | `logistics.ts` | 已封装 |
| getLogisticsProvider | 物流商详情 | `logistics.ts` | 已封装 |
| updateLogisticsProvider | 编辑物流商 | `logistics.ts` | 已封装 |
| listLogisticsChannels | 平台渠道列表 | `logistics.ts` | 页面/动作 |
| createLogisticsChannel | 新建渠道 | `logistics.ts` | 已封装 |
| getLogisticsChannel | 渠道详情 | `logistics.ts` | 已封装 |
| updateLogisticsChannel | 编辑渠道 | `logistics.ts` | 已封装 |
| replaceLogisticsChannelServiceCountries | 服务国家维护 | `logistics.ts` | 已封装 |
| listPublishedPriceRules | 已发布价格规则列表 | `logistics.ts` | 已封装 |
| publishPriceRuleVersion | 发布价格规则版本 | `logistics.ts` | 已封装 |
| getPublishedPriceRule | 价格规则详情 | `logistics.ts` | 已封装 |
| listAvailableLogisticsChannels | 租户可用渠道列表 | `logistics.ts` | 页面/动作 |
| getAvailableLogisticsChannel | 可用渠道详情 | `logistics.ts` | 已封装 |
| getAvailableChannelServiceCountries | 可服务国家 | `logistics.ts` | 已封装 |
| listQuotes | 报价管理列表 | `quotes.ts` | 页面/动作 |
| createQuote | 创建正式报价 | `quotes.ts` | 已封装 |
| getQuote | 报价管理详情查询 | `quotes.ts` | 页面/动作 |
| validateQuote | 报价有效性校验 | `quotes.ts` | 已封装 |
| createShipmentOrderFromQuote | 从报价创建订单 | `orders.ts` | 已封装 |
| listShipmentOrders | 订单管理列表 | `orders.ts` | 页面/动作 |
| getOrder | 订单/仓库详情查询 | `orders.ts` | 页面/动作 |
| updateDraftShipmentOrder | 更新草稿订单 | `orders.ts` | 已封装 |
| submitOrder | 订单提交 | `orders.ts` | 页面/动作 |
| cancelOrder | 订单取消 | `orders.ts` | 页面/动作 |
| confirmInbound | 仓库确认入库 | `warehouse.ts` | 页面/动作 |
| submitMeasurement | 仓库复称 | `warehouse.ts` | 页面/动作 |
| confirmPrice | 商户确认新费用 | `warehouse.ts` | 已封装 |
| getPriceConfirmation | 订单费用确认状态查询 | `orders.ts` | 页面/动作 |
| submitPriceConfirmationRequest | 商户提交费用确认申请 | `orders.ts` | 页面/动作 |
| confirmOutbound | 仓库确认出库 | `warehouse.ts` | 页面/动作 |
| getWarehouseOverview | 控制台仓库概览 | `warehouse.ts` | 页面/动作 |
| listWarehouseWork | 仓库作业分页、状态筛选和订单号查询 | `warehouse.ts` | `WarehouseWorkView.vue` |
| getWarehouseWork | 仓库作业详情 | `warehouse.ts` | `WarehouseWorkView.vue` |
| executeSfInternationalOperation | 顺丰五类受控操作 | `warehouse.ts` | `WarehouseWorkView.vue`；官方契约/配置缺失时后端拒绝 |
| receiveTrackingEvents | 物流商 HMAC 回调 | 无浏览器 client | 不由浏览器调用 |
| listTrackingEvents | 订单轨迹分页 | `tracking.ts` | 已封装 |
| listShipmentOrderTracking | 订单轨迹查询 | `tracking.ts` | 页面/动作 |
| getShipmentOrderTrackingStatus | 当前物流状态 | `tracking.ts` | 页面/动作 |
| getShipmentTrackingTimeline | 订单号/运单号全链路轨迹 | `tracking.ts` | 页面/动作 |
| importBillingCsv | CSV 账单导入 | `billing.ts` | 已封装 |
| listBillImportBatches | 账单批次列表 | `billing.ts` | 页面/动作 |
| getBillImportBatch | 账单批次详情 | `billing.ts` | 页面/动作 |
| listBillImportErrors | 批次错误明细 | `billing.ts` | 已封装 |
| listBillDetails | 账单明细 | `billing.ts` | 已封装 |
| listReconciliations | 对账列表 | `billing.ts` | 已封装 |
| getReconciliation | 对账详情 | `billing.ts` | 已封装 |
| confirmReconciliationDifference | 确认对账差异 | `billing.ts` | 已封装 |
| listTenantAuditLogs | 租户审计列表 | `audit.ts` | 页面/动作 |
| getTenantAuditLog | 租户审计详情 | `audit.ts` | 页面/动作 |
| listPlatformTenantAuditLogs | 平台按租户查询审计 | `audit.ts` | 页面/动作 |
| getPlatformTenantAuditLog | 平台审计详情 | `audit.ts` | 页面/动作 |
| listExceptionCases | 异常单列表 | `exceptions.ts` | 页面/动作 |
| getExceptionCase | 异常单详情 | `exceptions.ts` | 页面/动作 |
| listExceptionHandlingRecords | 异常处理记录 | `exceptions.ts` | 已封装 |
| createExceptionHandlingRecord | 新增异常处理记录 | `exceptions.ts` | 已封装 |
| listExceptionEvidence | 异常证据元数据 | `exceptions.ts` | 已封装 |
| uploadExceptionEvidence | 上传异常证据 | `exceptions.ts` | 已封装 |
| downloadExceptionEvidence | 下载异常证据 | `exceptions.ts` | 已封装 |
| createExceptionCase | 创建异常单 | `exceptions.ts` | 已封装 |
| assignExceptionCase | 分派异常 | `exceptions.ts` | 已封装 |
| transitionExceptionCase | 解决/关闭异常 | `exceptions.ts` | 已封装 |
| createClaim | 创建索赔 | `exceptions.ts` | 已封装 |
| getClaim | 索赔详情 | `exceptions.ts` | 已封装 |
| submitClaim | 提交索赔 | `exceptions.ts` | 已封装 |
| resolveClaim | 审核索赔 | `exceptions.ts` | 已封装 |
| closeClaim | 关闭索赔 | `exceptions.ts` | 已封装 |
| getOperationsSummary | 控制台概览 | `operations.ts` | 页面/动作 |
| getOperationsTodos | 控制台待办 | `operations.ts` | 页面/动作 |
| getOperationsWorkbench | P3-04 前端运营概览工作台 | `operations.ts` | 页面/动作 |

## 契约收口说明

1. 已按实际 `ShipmentOrderManagementController` 增加 `listShipmentOrders` 与 `updateDraftShipmentOrder`，订单列表现已接入页面，草稿更新已完成 typed service。
2. 原 OpenAPI 的 `createOrder`（`POST /orders`）没有对应后端 Controller，已从契约和前端 client 移除；创建订单统一使用实际存在的 `createShipmentOrderFromQuote`。
3. 两个 shipment-order 轨迹查询已复用后端 `TrackingEventResponse` 与 `TrackingStatusResponse` 建立正式 schema，前端不再使用无类型 JSON。
4. 服务国家查询按实际 `ApiResponse<List<String>>` 建立 ISO 国家代码数组 schema。
5. `ShipmentOrderResponse` 已正式返回 `version`，创建、列表、详情及订单写操作都会刷新前端内存中的版本；草稿更新、提交和取消均携带最近一次响应版本。
6. `receiveTrackingEvents` 使用物流商 HMAC 签名，是 server-to-server 入口；浏览器不持有共享密钥，也不提供该 client。
7. `GET /api/v1/platform/onboarding-applications/{applicationId}` 已补入 OpenAPI，并由平台入驻列表详情入口调用。
8. `GET /api/v1/orders/{orderId}/tracking-events` 已由订单详情轨迹面板调用，返回分页 `TrackingEventView`，物流商 HMAC 回调仍不由浏览器调用。
## P4-05 契约补充（2026-08-19）

店铺资源契约新增 4 个 operation：`getStoreDefaultAddress`、`updateStoreDefaultAddress`、`listStoreLogisticsChannels`、`setStoreDefaultLogisticsChannel`。当前静态 OpenAPI operationId 总数为 109，唯一性检查通过。

## 第五阶段 OpenAPI 收口（2026-08-19）

此前按“历史 106 + 店铺资源 4”推导为 110 的清单包含已移除的 `getEffectivePublishedPriceRule`：`GET /api/v1/logistics/channels/{channelId}/price-rule`。该租户接口会返回完整 `PublishedPriceRule` 及费率阶梯，不符合第五阶段“商家只能读取公开渠道字段”的边界，已由公开渠道详情的规则版本、生效时间和材积重除数投影替代。该路径没有前端调用；完整价格规则仍仅在平台 `/api/v1/platform/logistics-channels/{channelId}/price-rules*` 和报价内部计算中使用。当前 109 是有意收口后的实际契约数，不以修改统计掩盖差异。
