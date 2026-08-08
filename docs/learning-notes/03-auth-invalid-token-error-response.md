# 认证模块学习笔记：无效 Access Token 的统一错误响应

## 现象

数据库驱动用例 `AUTH-ME-004` 使用被篡改的 Access Token 请求
`GET /api/v1/users/me`。接口返回了预期的 HTTP 401，但响应体为空，导致执行器无法读取
`$.error.code`。

HTTP 状态正确不代表接口契约完整。客户端还依赖统一 JSON 错误结构、业务错误码和 Trace ID。

## 根因

Spring Security 的授权规则使用了统一的 `AuthenticationEntryPoint`，它负责输出
`COMMON-1002` JSON 响应。但是 Bearer Token 的解析或验签失败发生在 OAuth2 Resource
Server 过滤器中。Resource Server 默认使用自己的认证失败入口，因此没有调用项目配置的
JSON 入口。

## 业务流程

1. 客户端携带 Bearer Token 请求当前用户接口。
2. Spring Security 在进入 Controller 前解析并验证 JWT。
3. Token 被篡改或格式非法时，认证失败，不查询用户或租户数据。
4. 系统返回 HTTP 401、`COMMON-1002`、`WWW-Authenticate: Bearer` 和 Trace ID。
5. `X-Trace-Id` 响应头必须非空，并与响应体 `traceId` 相同。

这是只读失败路径，没有金额、事务、幂等或重复写入问题，也不会发生跨租户数据访问。

## 修复原则

同时为 `oauth2ResourceServer` 显式配置项目统一的 `AuthenticationEntryPoint`。不能通过删除
自动化用例中的错误码断言来掩盖响应契约缺失。

接口测试需要同时验证：

- HTTP 401；
- `COMMON-1002`；
- `WWW-Authenticate: Bearer`；
- JSON 错误结构；
- 响应头和响应体 Trace ID 一致。

## 验证边界

本次 Maven 测试启动前失败，因为运行中的 Java/IDEA 进程占用了 `backend/target` 下的资源
和编译状态文件。该失败不能记为测试通过。停止并重新启动后端前，应先运行后端测试，再复测
`AUTH-ME-004`。
