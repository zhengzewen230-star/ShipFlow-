# ShipFlow API 调用示例

> 这些示例使用占位符，不能填入真实密码、Token、Cookie、HMAC 密钥或客户数据。完整字段约束见 [`openapi/shipflow-api.yaml`](../openapi/shipflow-api.yaml)。

## 1. 浏览器认证顺序

```http
GET /api/v1/auth/csrf
```

浏览器保存服务端设置的 CSRF Cookie，再提交登录：

```http
POST /api/v1/auth/login
Content-Type: application/json
X-XSRF-TOKEN: <csrf-token>

{
  "username": "<username>",
  "password": "<password>"
}
```

后续受保护请求带 `Authorization: Bearer <access-token>`。当 Access Token 失效时，前端最多使用 HttpOnly 刷新 Cookie 调用一次 `/auth/refresh`；刷新失败即清理本地会话并返回登录页，禁止无限重试。

## 2. 幂等写入与 Trace ID

以下示例展示由报价创建订单。`Idempotency-Key` 应为客户端为一次业务意图生成的唯一值；网络超时后可用**同一键和相同请求体**重试。响应中的 `traceId` 和 HTTP `X-Trace-Id` 必须写入业务操作记录。

```http
POST /api/v1/quotes/123/shipment-orders
Authorization: Bearer <access-token>
Idempotency-Key: <uuid-for-this-business-intent>
X-Request-Id: <client-correlation-id>
Content-Type: application/json

{
  "storeId": 12,
  "receiver": { "countryCode": "US" },
  "items": [{ "sku": "<merchant-sku>", "quantity": 1 }]
}
```

```json
{
  "success": true,
  "traceId": "<trace-id>",
  "message": "OK",
  "data": { "id": 456, "status": "DRAFT" }
}
```

字段 `receiver`、`items` 只是说明性片段；实际必填字段以 `CreateShipmentOrderFromQuoteRequest` 为准。若返回 `409`，先 `GET` 读取最新状态；不得换键后重复创建。

## 3. 列表与指标下钻

```http
GET /api/v1/orders?status=SUBMITTED&page=1&pageSize=20&sortBy=createdAt&sortDirection=DESC
Authorization: Bearer <access-token>
```

首页某个指标的明细应改用：

```http
GET /api/v1/operations/workbench/metrics/<metricKey>/items?page=1&pageSize=20
Authorization: Bearer <access-token>
```

下钻页的 `total`、筛选范围和数据权限来自服务端，不能由首页数字或前端数组自行计算。

## 4. 物流商轨迹回调

```http
POST /api/v1/integrations/logistics/<providerCode>/tracking-events
Content-Type: application/json
X-Provider-Timestamp: <UTC-ISO-8601-or-contract-format>
X-Provider-Signature: <provider-hmac-signature>
X-Request-Id: <provider-delivery-id>

{
  "events": ["<follow-TrackingCallbackRequest-schema>"]
}
```

回调方应保留原始投递 ID；服务端按签名、租户/承运商映射、幂等标识与事件时间处理。收到 `5xx` 或依赖超时可按供应商约定退避重试；`4xx` 应由人工修正签名或数据后再投递。不要因事件乱序而倒退订单的物流状态。

## 5. 账单导入、对账与人工处理

账单导入使用 `POST /billing/import-batches` 的 multipart/请求体定义；导入后按顺序查询：

```text
GET /billing/import-batches/{batchId}
GET /billing/import-batches/{batchId}/errors
GET /billing/details?batchId={batchId}&page=1&pageSize=20
GET /reconciliations?batchId={batchId}&page=1&pageSize=20
GET /reconciliations/{reconciliationId}
```

零差异由后端完成。非零差异经人工复核后调用确认、驳回或补充说明接口；请求体提交当前 `version` 和真实处理说明。每次动作都会形成可查询历史，操作人应记录 `traceId`。

## 6. 错误与重试决策

| 结果 | 处理 |
| --- | --- |
| 401 | 尝试一次受控刷新；仍失败则登录。 |
| 403/404 | 停止重试，检查角色、店铺范围或资源归属。 |
| 409 | 读取最新资源；版本冲突由用户确认后生成新的业务意图。 |
| 422 | 修正字段或业务前置条件后重试。 |
| 500/503/超时 | 仅当接口支持幂等且使用原键时退避重试；否则提交人工重放任务并携带 `traceId`。 |

详见 [接口文档](API_REFERENCE.md) 的全局约定和发布验收清单。
