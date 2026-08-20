# 物流轨迹全链路追踪与沙箱推演学习笔记

## 业务闭环

订单创建、仓库入库、复称、出库交接来自 ShipFlow 内部业务表；顺丰回调来自既有 `tracking_event`；沙箱推演事件持久化到 `shipment_tracking_event`。查询服务按当前租户定位业务订单号或顺丰运单号，将这些来源合并后按 `occurred_at DESC` 返回，前端用来源颜色区分仓内节点和顺丰节点。

沙箱调度器只处理 `OUTBOUND`/`IN_TRANSIT` 且订单号为 `UAT-SF-*` 或 `provider_order.test_flag=TRUE` 的订单。每次调度只推进一个阶段：`SF_PICKED_UP`、`CUSTOMS_EXPORT_CLEARED`、`AIR_IN_TRANSIT`、`DELIVERED`。阶段唯一键和订单版本乐观更新共同防止重复事件、重复状态推进和并发覆盖；`DELIVERED` 后不再处理。订单现有状态约束不包含 `COMPLETED`，因此终态使用 `DELIVERED`。

## 关键技术约束

- V017 使用 UTC `DATETIME`，加入 `tenant_id`、外键、订单/运单索引和阶段唯一约束。
- 查询接口是 `GET /api/v1/orders/{orderNo}/tracking`，路径参数同时支持业务订单号和顺丰运单号，权限沿用租户 `tracking:read` 矩阵。
- `shipflow.sf.sandbox-enabled=false` 时调度器立即返回；定时频率为 25 秒。
- 工作台在途数量继续由订单 `IN_TRANSIT` 状态聚合，前端每 25 秒轮询仓库概览；轨迹页使用 Element Plus `ElTimeline`。

## 验证证据

- Flyway 11.7.2 已在 `shipflow` 库成功应用 V017，历史版本为 `017/1`。
- 后端 `mvn test`：360 tests，0 failures/errors；新增轨迹定点测试 9/9 通过。
- 前端 `npm run test:unit`：37/37 通过；`npm run build` 通过。
- OpenAPI 使用项目 Jackson YAML 依赖解析通过：OpenAPI 3.0.3、84 个 paths，轨迹 operationId 为 `getShipmentTrackingTimeline`。
- 新后端隔离端口 8081 健康检查通过，未认证轨迹路由返回 401；验收后已停止隔离进程。
- 真实沙箱数据库验收：5 个 `provider_order.test_flag=TRUE` 订单各有且仅有四个阶段事件，均推进至 `DELIVERED`；验收前已备份受影响行到系统临时目录。

真实登录后的租户轨迹 HTTP 查询和浏览器登录验收仍需配置 `SHIPFLOW_TEST_PASSWORD`；不在聊天中传递密码。沙箱调度与数据库闭环已完成真实验收。
