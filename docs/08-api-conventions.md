# ShipFlow API 通用规范

## 1. 基础约定

- API 风格：RESTful JSON。
- 基础路径：`/api/v1`。
- 当前版本：`v1`。不兼容变更使用新主版本路径，例如 `/api/v2`。
- 请求和响应默认使用 `Content-Type: application/json;charset=UTF-8`。
- CSV 上传使用 `multipart/form-data`，文件字段名为 `file`。
- 所有 API 输入的重量统一转换为 kg、尺寸统一转换为 cm 后进入数据库。

## 2. 鉴权和租户隔离

使用 JWT Bearer Token 访问受保护资源；Refresh Token 不通过 JSON 传递，而通过 Cookie 管理：

```http
Authorization: Bearer <JWT>
```

- `tenant_id` 只能从 JWT 身份上下文读取。
- 租户业务接口不接受调用方任意传入 `tenant_id`。
- 平台管理接口使用显式 `/platform/.../{tenantId}` 路径和 `tenantId` 路径参数；普通 `X-Tenant-Id` Header 不能决定授权范围。
- 平台管理员可以执行平台级租户和公共配置管理，无权限时返回 403 并写平台级审计。
- 租户用户只能访问当前租户数据。
- 普通租户用户跨租户资源访问统一返回 404，避免泄露资源存在性。
- 平台公共物流商、渠道和价格规则可以被租户使用，但租户不能修改平台基础配置。
- 普通租户用户跨租户资源访问统一返回 404，不泄露资源存在性；平台管理接口无权限返回 403，并写入平台级安全审计日志。

登录请求可选传入 `tenantCode`：

- `tenantCode` 为空时，只允许匹配平台用户；不允许在所有租户范围内模糊查找用户名。
- `tenantCode` 非空时，只在指定租户内匹配租户用户。
- 同名用户跨租户登录必须通过明确的 `tenantCode` 区分。
- 用户不存在、密码错误、tenantCode 错误、用户禁用、租户禁用和 Mock 系统账号尝试登录统一返回 `AUTH-1001`。

Refresh Cookie 约定：

- Cookie 名称为 `SHIPFLOW_REFRESH_TOKEN`，只用于 `/api/v1/auth` 路径，`HttpOnly`、生产 `Secure`、`SameSite=Strict`，并明确 `Max-Age=2592000`（30天）。
- Refresh Cookie 与 XSRF Cookie 均不设置 `Domain`；Refresh Cookie 使用明确 Path、HttpOnly、生产 Secure、SameSite=Strict、Max-Age=2592000；XSRF Cookie 不设置 HttpOnly，使用明确 Path、生产 Secure、SameSite=Strict 和 Max-Age=2592000。local/test 可通过配置关闭 Secure，生产必须开启。
- 登录成功下发 Refresh Cookie；刷新成功轮换并覆盖 Cookie；退出登录撤销当前 family 并下发过期 Cookie 清除它。
- Access Token 通过 JSON 返回，前端只保存在内存；Refresh Token 不进入 JSON 或 LocalStorage。
- `GET /api/v1/auth/csrf` 匿名获取并设置 XSRF Cookie，不返回 Refresh Token，响应必须 `Cache-Control: no-store`。
- 获取 CSRF 后，登录、刷新和退出都必须携带 `XSRF-TOKEN` Cookie 与标准 `X-XSRF-TOKEN` 请求头；使用 Spring Security `CookieCsrfTokenRepository` 校验，不自定义 CSRF 算法或过滤器。CSRF 失败统一返回 `403 AUTH-1005`。
- 登录、刷新、退出响应必须包含 `Cache-Control: no-store`。
- 认证接口不使用 `api_idempotency_record` 通用响应缓存。

## 3. 成功和错误响应

成功响应：

```json
{
  "success": true,
  "traceId": "01J...",
  "data": {},
  "message": "OK"
}
```

错误响应：

```json
{
  "success": false,
  "traceId": "01J...",
  "error": {
    "code": "ORDER-1001",
    "message": "订单状态不允许执行该操作",
    "details": {}
  }
}
```

HTTP 状态码表达协议层结果，业务错误码表达领域原因。常用映射：

- `200`：查询或幂等重试成功。
- `201`：资源创建成功。
- `202`：异步任务已接收。
- `400`：请求格式或参数错误。
- `401`：未登录或 Token 无效/过期。
- `403`：权限不足或跨租户访问。
- `404`：资源不存在或当前租户不可见。
- `409`：幂等冲突、唯一键冲突、乐观锁冲突或状态冲突。
- `422`：业务校验失败。
- `500`：内部系统错误。

## 4. 请求关联和时间

- 客户端可传 `X-Request-Id`；未传时服务端生成。
- 服务端响应必须返回 `X-Trace-Id`，同时在响应体返回 `traceId`。
- 所有成功和错误响应都必须返回 `X-Trace-Id`，且响应头值必须与响应体 `traceId` 完全一致。
- `requestId` 标识一次请求；`traceId` 标识跨服务调用链。
- 所有时间使用 UTC ISO-8601，例如 `2026-08-02T10:30:00.000Z`。
- 物流业务同时保存业务事件时间和系统接收时间。

## 5. 数据格式

- BIGINT ID 在 JSON 中统一使用字符串，例如 `"10001"`。
- 金额使用字符串，例如 `"58.00"`，禁止使用 JSON 浮点数。
- 重量和尺寸使用字符串，例如 `"1.500"`。
- 金额精度为 2 位小数。
- 重量和尺寸精度为 3 位小数。
- 日期时间使用 `date-time` 格式并输出 UTC。

## 6. 分页、排序和筛选

分页请求：

```text
page=1&pageSize=20&sortBy=createdAt&sortDirection=DESC
```

- `page` 从 1 开始，默认 1。
- `pageSize` 默认 20，最大 100。
- `sortDirection` 只允许 `ASC` 或 `DESC`。
- 排序字段必须使用接口白名单，禁止将原始参数拼接为 SQL。
- 筛选参数使用明确字段，例如 `status`、`createdFrom`、`createdTo`、`orderNo`。

分页响应：

```json
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "total": 0,
  "totalPages": 0
}
```

## 7. 幂等和乐观锁

创建订单必须使用请求头：

```http
Idempotency-Key: <client-generated-key>
```

- `Idempotency-Key` 必须存在且长度在 1 至 128 个字符之间。
- 数据库唯一边界为 `UNIQUE(tenant_id, idempotency_key)`。
- 相同租户、相同幂等键和相同请求参数重复提交，返回原订单结果。
- 相同租户、相同幂等键但请求参数不同，返回幂等冲突。
- 不同租户可以使用相同幂等键。
- 并发请求由数据库唯一索引兜底，应用层必须捕获冲突并返回原订单或冲突错误。

需要乐观锁的写接口在请求体中传 `version`；更新时必须执行版本匹配，冲突返回 `COMMON-1005`。报价生命周期状态更新使用 `quote.version`，订单状态和费用确认使用 `shipment_order.version`。

## 8. CSV 文件上传

- 使用 `multipart/form-data`，字段名 `file`。
- 第一版只接受统一 CSV 模板。
- 服务端校验文件扩展名、大小、编码、表头和必填列。
- 必填业务列包含 `provider_bill_detail_no`。
- 文件 Hash 用于批次防重，物流商明细号用于明细防重。
- 文件内容、原始客户地址和商品隐私字段不得写入普通应用日志。

## 9. 物流轨迹回调

- 回调路径：`/api/v1/integrations/logistics/{providerCode}/tracking-events`。
- 使用物流商配置的 HMAC 签名请求头，例如 `X-Provider-Signature` 和 `X-Provider-Timestamp`。
- 签名校验失败返回 `401` 和 `TRACK-1004`，不得处理业务数据。
- 签名算法为 `HMAC-SHA256(secret, timestamp + "." + rawRequestBody)`。
- `X-Provider-Timestamp` 使用 Unix 秒级时间戳，必须是10位数字，例如 `1785643200`；服务端允许的时间偏差不超过 300 秒。
- 签名编码为 Base64；服务端使用恒定时间比较签名摘要。
- Mock 物流系统账号通过平台系统账号映射到 `logistics_provider`，每个物流商的签名密钥只保存在受保护的凭证配置中。
- 幂等业务键为 `provider_id + tracking_no + event_id`。
- 重复事件返回幂等成功，但不得重复保存业务影响、推进订单状态或创建审计记录。
- 所有合法轨迹保存，展示按 `event_time` 排序，迟到事件不能使订单状态回退。

## 10. 敏感信息和日志

- 创建租户的 `initialAdmin`、创建初始管理员和创建租户用户请求必须包含 `temporaryPassword`。
- `temporaryPassword` 长度至少12位，必须同时包含大小写字母、数字和特殊字符；字段仅允许写入，后端使用 BCrypt 保存。
- 明文密码不得出现在响应示例、应用日志或审计详情中，也不得被数据库以明文保存。

- 禁止在日志中记录密码、JWT、Token、完整 Authorization 头、数据库连接密码。
- 手机号、邮箱、地址、报关商品信息按字段脱敏。
- 日志只保留必要的 `traceId`、资源 ID、租户 ID、错误码和摘要。
- 响应中不返回密码摘要、内部 Token、数据库字段原始报文或完整签名。
- 商家轨迹查询只返回脱敏后的 `TrackingEventView`，不返回 `rawPayload`、物流商签名或内部处理堆栈。
- 平台异常查询如确需返回原始报文，必须使用独立授权并先脱敏。
