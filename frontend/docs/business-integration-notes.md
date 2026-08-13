# 前后端业务接入学习笔记

## 认证与请求

- 登录前先取得 CSRF Cookie；写请求发送 `X-XSRF-TOKEN`。
- Access Token 只存在 Pinia/模块内存，刷新 Cookie 不由前端读取。
- 401 共享一个刷新请求，原请求最多重试一次，认证端点不参与自动刷新。

## 权限与导航

- 平台和租户工作台按 `scope:PLATFORM`、`scope:TENANT` 隔离。
- 菜单只是体验层隐藏，路由守卫再次校验；后端仍是最终权限边界。

## 业务写操作

- 所有写请求由请求拦截器统一携带 CSRF。
- 契约声明幂等键的创建/状态动作生成新的 `Idempotency-Key`；组件提交锁阻止双击重复发送。
- 带 `version` 的动作保留乐观锁版本，由服务端拒绝过期修改。
- 复称时间和出库时间使用 `Date.toISOString()`，明确发送 UTC 时间。

## 接入边界

- 页面不构造虚假成功数据；加载、空状态、网络错误和业务错误分别呈现。
- 服务端回调不会在浏览器中模拟；OpenAPI 未声明的响应模型不猜字段。

## API 契约收口

- 订单创建只有 `POST /quotes/{quoteId}/shipment-orders` 具备实际 Controller；移除无实现的 `POST /orders` 契约。
- `GET /orders` 与 `PUT /orders/{orderId}` 已按实际 Controller、请求 DTO 和响应 DTO写入 OpenAPI。
- 轨迹查询复用 `TrackingEventResponse` 与 `TrackingStatusResponse`；服务国家复用现有国家代码数组，不增加重复 DTO。
- `ShipmentOrderResponse` 已在创建、列表、详情和订单写操作响应中统一返回 `version`；前端保存最新响应版本，并用于后续草稿更新、提交和取消。
- 草稿更新在修改地址与商品前原子校验并递增订单版本；旧版本请求返回 409，事务失败时版本递增随业务修改一并回滚。
- 物流商 HMAC 回调是 server-to-server 边界，Vue 前端不持有或发送共享密钥。
