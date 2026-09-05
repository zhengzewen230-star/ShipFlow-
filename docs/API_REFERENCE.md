# ShipFlow 接口文档

> 文档版本：1.0.0
> 契约版本：以 [`openapi/shipflow-api.yaml`](../openapi/shipflow-api.yaml) 为准
> API 前缀：`/api/v1`（OpenAPI `servers.url`）
> 更新时间：2026-09-05（Asia/Shanghai）

## 1. 交付边界与获取方式

本文是 ShipFlow 跨境物流与电商履约系统的中文联调索引，覆盖 OpenAPI 中全部 **115 个 HTTP 操作**。每一个请求/响应字段、必填项、枚举、格式和状态码的可执行定义都在唯一机器可读契约 [`openapi/shipflow-api.yaml`](../openapi/shipflow-api.yaml)；不得以本文的示例取代该契约。

后端启动后可访问：

- OpenAPI JSON：`GET /v3/api-docs`
- Swagger UI：`/swagger-ui/index.html`
- 业务 API：`/api/v1/*`

建议客户端在构建时从 OpenAPI 生成类型，发布前对比运行时 `/v3/api-docs` 与本文件所列操作数。操作数不一致、DTO 字段不一致或安全要求不一致均应阻断发布。

## 2. 全局约定

### 2.1 认证、租户和授权

除 OpenAPI 明确声明 `security: []` 的公共接口、`/auth/*` 以及物流商回调外，请使用 Bearer Access Token：

```http
Authorization: Bearer <access-token>
```

租户、用户、角色和店铺数据范围由 JWT 和服务端授权决定。客户端不得传入 `tenantId` 以扩大范围。资源不属于当前调用方时，服务端可返回统一的 `404`；无认证为 `401`，认证后但无权限为 `403`。

登录、刷新与退出使用 HttpOnly Cookie；浏览器端的状态变更请求先取得 CSRF Cookie，并回传 `X-XSRF-TOKEN`（准确请求头名称以 OpenAPI `XsrfHeader` 参数为准）。不得把密码、Token、Cookie 或 HMAC 签名写入日志、文档或版本库。

### 2.2 请求关联、幂等与并发

| 目的 | 请求头 | 规则 |
| --- | --- | --- |
| 调用链关联 | `X-Request-Id` | 客户端可传入可追踪 ID；服务端响应应返回 `X-Trace-Id`，故障工单需记录它。 |
| 写操作去重 | `Idempotency-Key` | 对 OpenAPI 声明该参数的创建、导入、状态变更和仓库写接口必传或按契约传入；同一租户、同一业务语义、同一键重复提交应返回既有结果或明确冲突，不能重复落账。 |
| 乐观锁 | 请求体 `version` | 状态变更、审批和可编辑资源按 DTO 要求提交当前版本；版本不一致返回 `409`，客户端刷新详情后由人工重新确认。 |

外部物流回调还须传 `X-Provider-Timestamp` 与 `X-Provider-Signature`（具体签名算法/头名以 `providerHmac` 安全方案为准）。回调必须允许安全重试，乱序事件由服务端按事件时间和状态机处理。

### 2.3 通用响应、金额、重量和时间

成功响应统一为：

```json
{
  "success": true,
  "traceId": "服务器生成的追踪标识",
  "message": "OK",
  "data": {}
}
```

错误响应的精确字段见 `ApiError`。客户端应显示可读 `message`，保留 `traceId` 供支持人员定位，不得把后端错误替换为“成功”。

- 金额：十进制 `BigDecimal`，不可用 IEEE-754 浮点累计；货币为 ISO 4217 三字码。
- 重量：kg；体积字段、尺寸字段及最小精度以对应 Schema 为准。
- 时间：带时区偏移的 UTC ISO-8601 `date-time`；前端可按用户时区展示，但传输与审计不可丢失偏移。
- 分页：使用 `page`（从 1 开始）与 `pageSize`；响应的 `items` 与 `total`/页元数据为唯一统计口径。分页、筛选和排序的可用参数以每个 operation 的 parameters 为准。

### 2.4 共同状态码

| 状态 | 含义 | 客户端处理 |
| --- | --- | --- |
| 200/201 | 成功/已创建 | 保存 `traceId`，刷新对应列表或详情。 |
| 400 | 参数、格式或业务前置条件错误 | 显示服务端字段/业务提示，不重试同一无效内容。 |
| 401 | 未认证或会话失效 | 仅按认证流程刷新一次；失败则回登录页。 |
| 403 | 已认证但无权限或 CSRF 校验失败 | 不伪装为空数据；提示无权限/重新获取 CSRF。 |
| 404 | 资源不存在或不在数据范围 | 返回列表或提示资源不可用。 |
| 409 | 幂等冲突、重复记录或版本冲突 | 读取最新状态，避免盲目重复提交。 |
| 422 | 可解析但不满足领域规则 | 显示规则原因，待人工修正后使用新幂等键提交。 |
| 500/503 | 未预期错误/依赖不可用 | 显示 `traceId`；只对具备幂等保证的调用做退避重试。 |

## 3. 接口目录

路径均相对 `/api/v1`。`公开`表示无需 Bearer Token，但仍可能需要 CSRF 或幂等键；`平台`表示平台侧权限；`租户`表示由 JWT 租户与店铺范围约束；`回调`表示物流商 HMAC 校验入口。

### 3.1 公开询盘与商户入驻（10）

| 方法 | 路径 | 范围 | 用途 |
| --- | --- | --- | --- |
| POST | `/public/estimate-requests` | 公开 | 提交访客预估询盘，不生成正式报价。 |
| POST | `/public/onboarding-applications` | 公开 | 提交商户入驻申请。 |
| POST | `/public/onboarding-activations` | 公开 | 以一次性邀请激活初始管理员。 |
| GET | `/platform/onboarding-applications` | 平台 | 分页查询入驻申请。 |
| GET | `/platform/onboarding-applications/{applicationId}` | 平台 | 查看入驻申请。 |
| POST | `/platform/onboarding-applications/{applicationId}/approve` | 平台 | 审批通过并创建租户/邀请。 |
| POST | `/platform/onboarding-applications/{applicationId}/reject` | 平台 | 驳回入驻申请。 |
| GET | `/platform/guest-estimate-leads` | 平台 | 分页查询访客询盘线索。 |
| GET | `/platform/guest-estimate-leads/{leadId}` | 平台 | 查看询盘线索。 |
| PATCH | `/platform/guest-estimate-leads/{leadId}/status` | 平台 | 更新线索状态与处理备注（带 `version`）。 |

### 3.2 认证与当前身份（5）

| 方法 | 路径 | 范围 | 用途 |
| --- | --- | --- | --- |
| GET | `/auth/csrf` | 公开 | 获取 CSRF Cookie/令牌。 |
| POST | `/auth/login` | 公开 | 使用登录 DTO 建立会话并取得 Access Token。 |
| POST | `/auth/refresh` | 刷新 Cookie | 刷新 Access Token。 |
| POST | `/auth/logout` | 会话 | 注销并清理认证 Cookie。 |
| GET | `/users/me` | 租户 | 获取当前用户、角色、权限和数据范围。 |

### 3.3 平台租户、店铺、用户与 RBAC（24）

| 方法 | 路径 | 范围 | 用途 |
| --- | --- | --- | --- |
| POST/GET | `/platform/tenants` | 平台 | 创建 / 分页查询租户。 |
| GET/PUT | `/platform/tenants/{tenantId}` | 平台 | 查看 / 更新租户。 |
| POST | `/platform/tenants/{tenantId}/status` | 平台 | 变更租户状态。 |
| POST/GET | `/stores` | 租户 | 创建 / 分页查询店铺。 |
| GET/PUT | `/stores/{storeId}` | 租户 | 查看 / 更新店铺。 |
| GET/PUT | `/stores/{storeId}/default-address` | 租户 | 查询 / 更新默认发货地址。 |
| GET | `/stores/{storeId}/logistics-channels` | 租户 | 查询店铺可用物流渠道。 |
| PUT | `/stores/{storeId}/default-logistics-channel` | 租户 | 设置默认物流渠道。 |
| POST | `/stores/{storeId}/status` | 租户 | 变更店铺状态。 |
| POST/GET | `/users` | 租户 | 创建 / 分页查询租户用户。 |
| GET/PUT | `/users/{userId}` | 租户 | 查看 / 更新用户。 |
| POST | `/users/{userId}/status` | 租户 | 变更用户状态。 |
| PUT | `/users/{userId}/roles` | 租户 | 替换用户角色绑定。 |
| GET | `/roles` | 租户 | 查询角色。 |
| GET | `/roles/{roleId}` | 租户 | 查询角色详情。 |
| PUT | `/roles/{roleId}/permissions` | 租户 | 替换角色权限。 |
| GET | `/permissions` | 已认证 | 查询权限目录。 |

### 3.4 物流主数据与报价（20）

| 方法 | 路径 | 范围 | 用途 |
| --- | --- | --- | --- |
| GET/POST | `/platform/logistics-providers` | 平台 | 查询 / 创建物流商。 |
| GET/PUT | `/platform/logistics-providers/{providerId}` | 平台 | 查看 / 更新物流商。 |
| GET/POST | `/platform/logistics-channels` | 平台 | 查询 / 创建物流渠道。 |
| GET/PUT | `/platform/logistics-channels/{channelId}` | 平台 | 查看 / 更新物流渠道。 |
| PUT | `/platform/logistics-channels/{channelId}/service-countries` | 平台 | 替换服务国家。 |
| GET/POST | `/platform/logistics-channels/{channelId}/price-rules` | 平台 | 查询 / 发布价格规则。 |
| GET | `/platform/logistics-channels/{channelId}/price-rules/{priceRuleId}` | 平台 | 查询已发布规则。 |
| GET | `/logistics/channels` | 租户 | 查询调用方可用渠道。 |
| GET | `/logistics/channels/{channelId}` | 租户 | 查询可用渠道详情。 |
| GET | `/logistics/channels/{channelId}/service-countries` | 租户 | 查询可用渠道服务国家。 |
| GET/POST | `/quotes` | 租户 | 分页查询 / 创建运费报价。 |
| GET | `/quotes/{quoteId}` | 租户 | 获取报价详情。 |
| POST | `/quotes/{quoteId}/validate` | 租户 | 重新校验报价规则和有效期。 |
| POST | `/quotes/{quoteId}/shipment-orders` | 租户 | 由报价创建物流订单（幂等）。 |

### 3.5 订单、仓库作业与价格确认（15）

| 方法 | 路径 | 范围 | 用途 |
| --- | --- | --- | --- |
| GET | `/orders` | 租户 | 按订单号、店铺、状态、国家、渠道、轨迹、时间分页筛选订单。 |
| GET | `/orders/export` | 租户 | 按同一筛选范围导出订单 CSV。 |
| GET/PUT | `/orders/{orderId}` | 租户 | 获取订单详情 / 更新草稿。 |
| POST | `/orders/{orderId}/submit` | 租户 | 提交订单状态流转（幂等、版本控制）。 |
| POST | `/orders/{orderId}/cancel` | 租户 | 取消订单（幂等、版本控制）。 |
| POST | `/orders/{orderId}/inbound` | 仓库 | 入库确认（幂等）。 |
| POST | `/orders/{orderId}/measurements` | 仓库 | 提交量方称重（幂等）。 |
| GET/POST | `/orders/{orderId}/price-confirmation` | 租户 | 查询价格确认 / 确认报价。 |
| POST | `/orders/{orderId}/price-confirmation-requests` | 租户 | 发起价格确认申请。 |
| POST | `/orders/{orderId}/outbound` | 仓库 | 出库确认（幂等）。 |
| GET | `/warehouse/overview` | 仓库 | 查询仓库概览指标。 |
| GET | `/warehouse/orders` | 仓库 | 分页查询仓库待办订单。 |
| GET | `/warehouse/orders/{orderId}` | 仓库 | 查询仓库作业详情。 |

### 3.6 物流轨迹、供应商回调与 SF 国际件（7）

| 方法 | 路径 | 范围 | 用途 |
| --- | --- | --- | --- |
| POST | `/integrations/logistics/{providerCode}/tracking-events` | 回调 | 接收物流商批量轨迹回调；校验签名、时间戳、去重和乱序。 |
| GET | `/orders/{orderId}/tracking-events` | 租户 | 查询订单轨迹事件。 |
| GET | `/shipment-orders/{orderId}/tracking` | 租户 | 查询运单轨迹时间线。 |
| GET | `/shipment-orders/{orderId}/tracking/status` | 租户 | 查询当前物流状态、ETA/SLA 相关信息。 |
| GET | `/orders/{orderNo}/tracking` | 租户 | 以订单号查询对外轨迹。 |
| GET | `/tracking-events` | 租户 | 按条件检索轨迹事件。 |
| POST | `/orders/{orderId}/sf-international/{operation}` | 租户 | 执行顺丰国际下单、面单等已定义 operation。 |

回调成功不代表每一事件一定同步完成：响应/结果中的成功、失败、重复、乱序和待重放信息应由调用方记录。供应商超时可重试同一回调；业务方人工重放前必须保留原始事件和 `traceId`。

### 3.7 账单、对账与审计（14）

| 方法 | 路径 | 范围 | 用途 |
| --- | --- | --- | --- |
| POST/GET | `/billing/import-batches` | 财务 | 导入账单批次 / 分页查询批次。 |
| GET | `/billing/import-batches/{batchId}` | 财务 | 查看批次汇总与处理状态。 |
| GET | `/billing/import-batches/{batchId}/errors` | 财务 | 查询原始错误行、重复行与错误原因。 |
| GET | `/billing/details` | 财务 | 分页查询账单逐行明细。 |
| GET | `/reconciliations` | 财务 | 分页查询系统费用与供应商费用对账。 |
| GET | `/reconciliations/{reconciliationId}` | 财务 | 查询对账详情、差异及处理历史。 |
| POST | `/reconciliations/{reconciliationId}/confirm` | 财务 | 确认非零差异（带说明/版本）。 |
| POST | `/reconciliations/{reconciliationId}/reject` | 财务 | 驳回对账处理。 |
| POST | `/reconciliations/{reconciliationId}/comments` | 财务 | 追加对账说明及审计记录。 |
| GET | `/audit-logs`、`/audit-logs/{auditId}` | 租户 | 查询租户审计列表 / 详情。 |
| GET | `/platform/audit-logs`、`/platform/audit-logs/{auditId}` | 平台 | 查询平台审计列表 / 详情。 |

零差异由后端规则自动完成；非零差异必须进入人工确认或驳回。汇率快照、系统费用、供应商费用和差异原因以 `Reconciliation` 与关联明细 Schema 为准。

### 3.8 异常、证据与索赔（16）

| 方法 | 路径 | 范围 | 用途 |
| --- | --- | --- | --- |
| GET | `/exceptions` | 租户 | 分页查询异常单。 |
| GET | `/exceptions/{exceptionId}` | 租户 | 查询异常详情、状态和时间线。 |
| POST | `/orders/{orderId}/exceptions` | 租户 | 为订单创建异常单。 |
| POST | `/exceptions/{exceptionId}/assign` | 客服/运营 | 指派异常处理人。 |
| POST | `/exceptions/{exceptionId}/status` | 客服/运营 | 执行异常状态机流转。 |
| GET/POST | `/exceptions/{exceptionId}/handling-records` | 客服/运营 | 查询 / 新增处理记录。 |
| GET/POST | `/exceptions/{exceptionId}/evidence` | 客服/运营 | 查询 / 上传证据附件。 |
| GET | `/exceptions/{exceptionId}/evidence/{attachmentId}/content` | 客服/运营 | 下载授权范围内的证据内容。 |
| POST | `/exceptions/{exceptionId}/claim` | 客服/运营 | 创建关联索赔。 |
| GET | `/claims/{claimId}` | 租户 | 查询索赔详情、金额和时间线。 |
| POST | `/claims/{claimId}/submit` | 客服/运营 | 提交索赔。 |
| POST | `/claims/{claimId}/result` | 客服/运营 | 登记承运商处理结果。 |
| POST | `/claims/{claimId}/finance-confirmation` | 财务 | 财务确认索赔金额。 |
| POST | `/claims/{claimId}/close` | 客服/运营 | 关闭完成的索赔。 |

异常与索赔的可迁移状态、前置条件、`version` 和审计字段必须按对应 Request/Response Schema 提交；不允许客户端跳过服务端状态机。

### 3.9 运营工作台（4）

| 方法 | 路径 | 范围 | 用途 |
| --- | --- | --- | --- |
| GET | `/operations/summary` | 租户 | 获取运营汇总。 |
| GET | `/operations/todos` | 租户 | 获取按角色授权的待办。 |
| GET | `/operations/workbench` | 租户 | 获取首页指标、风险、待办和最近订单。 |
| GET | `/operations/workbench/metrics/{metricKey}/items` | 租户 | 使用同一后端口径分页下钻工作台指标。 |

工作台指标与其下钻列表必须共享服务端筛选、时间范围和数据范围；客户端不得自行汇总或伪造数量。

## 4. 关键 Schema 索引

以下为联调最常用的 Schema 名称。完整字段可在 OpenAPI `components.schemas` 定位。

| 领域 | 请求 Schema | 响应/分页 Schema |
| --- | --- | --- |
| 认证 | `LoginRequest` | `TokenResponse`、`CurrentUserView`、`ApiSuccessLogin` |
| 报价 | `CreateQuoteRequest` | `Quote`、`QuotePage`、`QuoteValidation` |
| 订单 | `CreateShipmentOrderFromQuoteRequest`、`UpdateShipmentOrderRequest`、`OrderActionRequest` | `ShipmentOrder`、`ShipmentOrderPage`、`Order` |
| 仓库 | `InboundRequest`、`MeasurementRequest`、`OutboundRequest` | `WarehouseResult`、`WarehouseWorkPage`、`WarehouseOverview` |
| 轨迹 | `TrackingCallbackRequest`、`TrackingEventInput` | `TrackingEventResult`、`TrackingPage`、`ShipmentTrackingStatus` |
| 账单/对账 | `BillBatch` 导入请求、`ReconciliationActionRequest` | `BillDetailPage`、`Reconciliation`、`ReconciliationPage`、`ReconciliationAction` |
| 异常/索赔 | `CreateExceptionRequest`、`AssignExceptionRequest`、`ExceptionStatusRequest`、`CreateClaimRequest`、`ClaimActionRequest` | `ExceptionCaseView`、`ExceptionCasePage`、`ClaimView` |
| 公共入驻 | `CreateGuestEstimateRequest`、`CreateOnboardingApplicationRequest`、`ActivationRequest` | `GuestEstimateResponse`、`OnboardingApplication`、`OnboardingPage` |

## 5. 发布与验收清单

1. 修改 Controller、DTO、Mapper XML、Service、数据库字段或前端 typed service 时，同步修改 OpenAPI 和本文目录。
2. 执行 OpenAPI 契约测试、Mapper XML 测试、WebMvc 测试和集成测试；写接口同时做 HTTP 与数据库断言。
3. 覆盖 401、403、404、409、422、500、超时、重复请求、回调乱序和供应商失败；所有失败响应记录 `traceId`。
4. 对新增数据库字段先完成 Flyway 影响、索引、约束、外键、备份与回滚评审；不修改历史迁移，不删除历史数据。
5. 真实浏览器验收登录、订单、仓库、轨迹、异常、账单、对账和首页下钻；将运行证据与阻塞项记录在审计文档，不能用静态页面或 mock 成功替代。

联调请求模板与安全重试示例见 [API 调用示例](API_EXAMPLES.md)。
