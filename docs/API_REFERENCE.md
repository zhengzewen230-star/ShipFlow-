# ShipFlow 接口文档

## 唯一契约来源

所有 HTTP 契约以 [`openapi/shipflow-api.yaml`](../openapi/shipflow-api.yaml) 为准。后端 Controller、DTO、Mapper XML、前端 typed service 和 WebMvc/契约测试应同步更新；不得只修改其中一层。

运行时文档（后端已启动时）：

- OpenAPI JSON：`GET /v3/api-docs`
- Swagger UI：`/swagger-ui/index.html`
- 业务 API 前缀：`/api/v1`

## 通用约定

- 成功响应为 `ApiResponse<T>`；失败响应包含稳定错误码、可读信息和 Trace ID。
- 使用 `X-Request-Id` 关联请求与审计；客户端应记录响应中的 `X-Trace-Id`。
- 写接口按契约要求提交 `Idempotency-Key`，并遵守资源 `version` 的乐观锁规则。
- 认证通过 Access Token 和 HttpOnly 刷新 Cookie 完成；浏览器写请求必须携带 CSRF Header。
- 业务数据必须由 JWT 租户、角色和店铺授权范围约束，客户端传入的 tenant/store 参数不能扩大范围。
- 金额为 `BigDecimal` 十进制，货币为 ISO 代码；重量单位为 kg；接口时间为含 offset 的 UTC ISO-8601。

## 领域入口

| 领域 | 典型资源 |
| --- | --- |
| 认证与授权 | `/auth/*`、`/users/me`、租户/用户/角色/店铺资源 |
| 报价和订单 | `/quotes`、`/orders`、订单状态与费用确认 |
| 仓库和物流 | `/warehouse/*`、`/orders/{id}/inbound`、`/tracking-events`、物流商回调 |
| 异常与索赔 | `/exceptions`、`/claims`、证据与处理记录 |
| 账单与对账 | `/billing/import-batches`、`/billing/details`、`/reconciliations` |
| 运营与审计 | `/operations/workbench`、指标下钻、`/audit` |

## 验证接口契约

1. 修改 OpenAPI 后，运行后端 WebMvc、OpenAPI/Mapper XML 测试。
2. 启动隔离环境后，比对 `/v3/api-docs` 与静态 OpenAPI 的 operationId、请求参数、响应 schema 和安全要求。
3. 对每个写接口测试 401、403、400/422、409、重复请求和 Trace ID；不得用前端模拟成功替代服务端结果。
