# P3-06b 后端筛选契约最小补齐

更新时间：2026-08-18（Asia/Shanghai）

## 已补齐

| 工作台项 | 现有字段和数据来源 | 后端参数 | 前端 query | 权限与隔离 |
|---|---|---|---|---|
| 待确认费用 | `fee_adjustment.confirmation_status` | `GET /api/v1/orders?workbenchFilter=PENDING_FEE_CONFIRMATION` | 工作台 `status=PENDING_FEE_CONFIRMATION`，订单页转换为后端参数 | `scope:TENANT + order:read`；SQL 同时校验 `tenant_id + user_id + active sys_user_store_scope` |
| 待补地址 | `shipment_address` 缺少 SENDER 或 RECEIVER | `GET /api/v1/orders?workbenchFilter=MISSING_ADDRESS` | 工作台 `status=MISSING_ADDRESS`，订单页转换为后端参数 | 同上；空结果仍为正常分页响应 |
| 待异常跟进 | `exception_case.status` 为 OPEN、PROCESSING、WAITING_PROVIDER_FEEDBACK | `GET /api/v1/exceptions?workbenchFilter=PENDING_FOLLOW_UP` | 工作台 `status=PENDING_EXCEPTION_FOLLOW_UP`，异常页转换为后端参数 | `scope:TENANT + exception:read`；异常可见店铺谓词校验租户、角色和 active scope |

参数均为后端白名单；非法值返回统一 `400 COMMON-1001`。所有时间仍由后端 UTC 存储和 API UTC ISO-8601 返回，前端按 Asia/Shanghai 展示。

## 仍缺失的安全契约

| 工作台项 | 根因 | 处理结论 |
|---|---|---|
| 待贴标/待出库 | 来源是 `provider_order.lifecycle_status` 与订单状态组合；现有仓库列表仅 tenant 过滤，未承接调用方 `user_id/store_id` 范围 | 不新增 tenant-only 筛选；前端保留中文提示 |
| 轨迹异常/在途 | `tracking_event.process_status` 和订单状态可统计，但轨迹页面只有按订单号或运单号时间线，没有 scoped 聚合列表 | 不伪造轨迹状态列表；前端保留中文提示 |
| 待补清关资料/待仓库处理 | `customs_document` 与订单状态可以统计，但现有仓库工作列表同样未实现 user/store scope | 不暴露超出授权店铺的仓库列表 |
| 待财务处理/待对账确认 | 对账记录可按订单归属；账单错误行可能没有 `shipment_order_id`，不能可靠映射到商家店铺 | 现有对账 `PENDING_CONFIRMATION` 可复用；商家账单错误聚合保持后端契约缺失，不能按 tenant 全量放行 |

## 修改与验证

- 后端：订单 Controller、查询服务、Mapper/XML；异常 Controller、服务、Mapper/XML。
- 前端：订单/异常 service 类型、工作台筛选解析与列表请求。
- OpenAPI：`/orders` 与 `/exceptions` 新增 `workbenchFilter` 白名单。
- 测试：JDK 21.0.11 下定向 Controller、服务和 Mapper/XML 测试 `47/47` 通过；订单筛选服务验证白名单、分页偏移和调用者范围参数，异常筛选服务验证跟进状态，Mapper/XML 验证租户和 active store scope 谓词。前端单元测试 `57/57`、生产构建通过；OpenAPI 静态检查 `106/106` operationId 唯一；`git diff --check` 通过。

未执行数据库写入、Flyway、顺丰生产调用、Git commit 或 push。

结论：本轮只能关闭已具备精确店铺归属的数据筛选。其余项必须先补齐 scoped 仓库、轨迹和账单查询契约，P3-06c 暂不进入。
