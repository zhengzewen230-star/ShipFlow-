# 订单异常处理与索赔学习笔记

## 业务流程

异常单独立于订单履约状态，不使用异常处理伪造或回退订单状态。异常严格沿 `OPEN → PROCESSING → RESOLVED → CLOSED` 推进；分派动作负责把 `OPEN` 推进到 `PROCESSING`。索赔严格沿 `OPEN → SUBMITTED → APPROVED/REJECTED → CLOSED` 推进。

异常可以直接关联订单，也可以额外引用该订单的轨迹事件。`trackingEventId` 不写入被冻结的 `exception_case` 表，而是写入创建审计详情；创建前必须以 `tenant_id + shipment_order_id + tracking_event.id` 验证归属。

## 数据与租户边界

- 列表、异常详情、索赔详情及所有写操作都使用 JWT `tenant_id` 作为 Mapper 条件。
- 跨租户和不存在统一返回 `COMMON-1006/404`，不泄露资源是否属于其他租户。
- `exception_case` 没有受派人列。第一版将受派人保存在只追加的 `EXCEPTION_ASSIGN` 审计详情，查询使用最近一次分派审计投影。
- 创建异常和创建索赔使用 `api_idempotency_record`；其他动作由状态前置条件、版本锁和条件更新防止重复。

## 索赔资格

索赔创建必须同时满足：异常状态为 `RESOLVED`；订单状态为 `DELIVERED`、`RETURNED` 或 `LOST`；金额大于零且最多两位小数；索赔币种与订单币种一致；该异常尚无索赔记录。数据库的 `UNIQUE(exception_case_id)` 是并发情况下的最终唯一性屏障。

## 不变量

本模块只写 `exception_case`、`claim_record`、`api_idempotency_record` 和 `audit_log`。不更新 `shipment_order`、`tracking_event`、`shipment_quote_snapshot`、`fee_adjustment` 或 `warehouse_measurement`，因此不会改变报价快照、订单历史金额、轨迹事实或仓库复称记录。

## 验证边界

单元测试覆盖正常状态链、跨租户 404、非法跳跃、重复操作、幂等创建和索赔前置条件；Mapper XML 测试检查租户谓词、乐观锁、审计与财务边界；MockMvc 测试检查 JWT 租户、权限、CSRF 和统一错误响应。本轮按约束不连接 MySQL，不运行迁移、数据库集成测试或真实 HTTP。

## 给项目负责人的问题

业务问题：索赔审核最终应由租户管理员、平台运营还是物流商专用角色执行？当前权限模型没有独立 `claim:review`，第一版沿用 `order:operate`。

技术问题：后续是否允许新增异常分派历史表，替代从 `audit_log.detail` 投影当前受派人的第一版实现？
