# 模拟物流轨迹运输异常学习笔记

## 业务流程

顺丰沙箱轨迹模拟器每 25 秒仅处理已出库或运输中的沙箱订单。每次准备写入下一个正常节点时，使用一次随机抽样：小于 `0.30` 时生成运输异常；大于或等于 `0.30` 时继续既有的揽收、清关、国际运输和妥投轨迹，不改变正常路径。

## 异常行为

- 异常轨迹状态码为 `TRANSPORT_EXCEPTION`，标题为“运输异常（模拟）”。
- 同一事务内创建 `exception_case`：类型 `TRANSPORT`、状态 `OPEN`、责任方 `PROVIDER`，发生时间为 UTC。
- 同一事务内以系统操作人（`operator_user_id = NULL`）写入 `EXCEPTION_SIMULATION_CREATE` 审计事件，固定请求标识为 `SF_TRACKING_SIMULATION`。
- 生成异常时不推进订单状态；异常轨迹成为最新模拟节点后，调度器停止该订单的自动正常推进，等待人工在异常模块处理。
- 异常单号固定为 `SIMEX-{orderId}`，复用现有 `(tenant_id, exception_no)` 唯一约束和 `ON DUPLICATE KEY`，确保同一订单不会因重复调度或并发节点产生第二条模拟异常。

## 验证

- 随机值 `0.30` 验证正常轨迹仍会从 `OUTBOUND` 推进到 `IN_TRANSIT`。
- 随机值 `0.29` 验证创建一条 `TRANSPORT/OPEN/PROVIDER` 异常和一条异常轨迹，且不更新订单状态。
- 最新节点为 `TRANSPORT_EXCEPTION` 时验证不重复插入异常、不插入轨迹、不推进订单。
- Mapper XML 解析验证了新增 SQL 语句存在。
