# ShipFlow 前端 OpenAPI 覆盖清单

契约基线：`openapi/shipflow-api.yaml`，共 83 个 `operationId`。状态含义：

- **页面/动作**：控制台或登录流程已经直接调用。
- **已封装**：typed service 已实现，但当前页面还没有完整业务表单。
- **不由浏览器调用**：服务端到服务端回调，前端只展示其处理结果。

| operationId | 前端页面/动作 | service | 状态 |
|---|---|---|---|
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
| getEffectivePublishedPriceRule | 生效价格规则 | `logistics.ts` | 已封装 |
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
| confirmOutbound | 仓库确认出库 | `warehouse.ts` | 页面/动作 |
| receiveTrackingEvents | 物流商 HMAC 回调 | 无浏览器 client | 不由浏览器调用 |
| listTrackingEvents | 订单轨迹分页 | `tracking.ts` | 已封装 |
| listShipmentOrderTracking | 订单轨迹查询 | `tracking.ts` | 页面/动作 |
| getShipmentOrderTrackingStatus | 当前物流状态 | `tracking.ts` | 页面/动作 |
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

## 契约收口说明

1. 已按实际 `ShipmentOrderManagementController` 增加 `listShipmentOrders` 与 `updateDraftShipmentOrder`，订单列表现已接入页面，草稿更新已完成 typed service。
2. 原 OpenAPI 的 `createOrder`（`POST /orders`）没有对应后端 Controller，已从契约和前端 client 移除；创建订单统一使用实际存在的 `createShipmentOrderFromQuote`。
3. 两个 shipment-order 轨迹查询已复用后端 `TrackingEventResponse` 与 `TrackingStatusResponse` 建立正式 schema，前端不再使用无类型 JSON。
4. 服务国家查询按实际 `ApiResponse<List<String>>` 建立 ISO 国家代码数组 schema。
5. `ShipmentOrderResponse` 已正式返回 `version`，创建、列表、详情及订单写操作都会刷新前端内存中的版本；草稿更新、提交和取消均携带最近一次响应版本。
6. `receiveTrackingEvents` 使用物流商 HMAC 签名，是 server-to-server 入口；浏览器不持有共享密钥，也不提供该 client。
