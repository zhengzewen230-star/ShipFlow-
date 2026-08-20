# 商家业务员工作台第三阶段 P3-03 后端统一数据契约实现

更新时间：2026-08-17（Asia/Shanghai）  
阶段状态：已完成后端实现，等待确认进入 P3-04 前端实现。

## 1. 实施范围

本子阶段只实现工作台后端只读快照，不修改已有订单、仓库、物流、轨迹、异常、账单或费用确认状态机，不新增数据库迁移，不启用 Flyway，不调用顺丰生产接口。目标接口为：

```text
GET /api/v1/operations/workbench
```

调用链为：JWT `subject`/`tenant_id` → Controller 解析查询参数 → Service 固定 UTC 快照时间和 Asia/Shanghai 业务窗口 → Mapper 以租户、店铺和角色范围查询现有表 → DTO 返回指标、待办、最近订单、风险、窗口和刷新时间。

## 2. 统一契约实现

- 查询参数：`timeRange`、`from/to`、`storeId`、`page/pageSize`、`sortBy/sortDirection`、`recentLimit/riskLimit`。
- 默认窗口：Asia/Shanghai 当日自然日，转为 UTC `[from,to)`；自定义窗口要求带 offset 或 `Z` 且 `to > from`。
- 时间：数据库查询参数和接口 `refreshedAt`、订单/风险时间均使用 UTC；业务时区明确返回 `Asia/Shanghai`。
- 金额/重量：后端继续使用 `BigDecimal`，预计费用沿用订单 `current_fee` 投影，重量沿用订单 kg 字段；未引入浮点计算或单位转换。
- 错误：非法查询参数返回 `COMMON-1001/400`，调用方身份缺失或非法返回 `COMMON-1004/403`，越权 `storeId` 返回 `COMMON-1004/403`。
- 跳转：指标、待办、最近订单和风险均返回后端白名单路由与查询条件，前端不自行拼接状态口径。

“待财务处理”返回四个子项并以四项之和作为聚合数量：`PENDING_FEE_CONFIRMATION`、`BILL_IMPORT_ERRORS`、`RECONCILIATION_DIFFERENCE`、`PENDING_FINANCE_REVIEW`。报价即将过期和订单长时间未处理均按平台默认 24 小时阈值计算，代码调用保留阈值参数扩展点。

## 3. 数据隔离实现

`findVisibleStoreIds` 和所有工作台订单关联查询均校验：

1. 当前用户存在、ACTIVE、属于 JWT 的 `tenant_id`；
2. 店铺属于同一租户、ACTIVE、未删除；
3. `MERCHANT_OPERATOR` 必须存在 ACTIVE 的 `sys_user_store_scope`；
4. `MERCHANT_ADMIN`、财务、仓库和客服租户角色只能查看当前租户 ACTIVE 店铺；
5. 账单、对账、异常、轨迹和清关资料通过订单关联回到 `store_id`，不把跨店铺或跨租户记录混入概览。

前端传入的 `storeId` 只用于收窄范围，先由后端查询可见店铺并再次判断归属；未授权店铺不会执行后续业务查询。

## 4. 现有表复用和状态口径

| 工作台字段 | 现有来源 | 实现口径 |
|---|---|---|
| 订单状态 | `shipment_order.current_status` | 使用既有订单状态，不创建前端状态；费用确认成功后的 `READY_FOR_OUTBOUND` 保持不变 |
| 面单状态 | `provider_order.lifecycle_status` | 完成值统一为 `LABEL_READY`；没有 provider 记录也计入待贴标 |
| 待复称 | `warehouse_measurement` | `INBOUND` 且没有可用复称记录 |
| 费用确认 | `fee_adjustment.confirmation_status` | `PENDING_CONFIRMATION/REQUESTED` 计入待确认，业务员只申请、财务最终确认 |
| 账单错误 | `bill_import_batch`、`bill_detail` | 批次 `FAILED/PARTIAL_SUCCESS` 或明细 `ERROR` |
| 对账差异 | `reconciliation_record` | `PENDING_CONFIRMATION/REJECTED` 且差异非零 |
| 轨迹异常 | `tracking_event.process_status` | `RETRY/REJECTED`，按订单去重指标 |
| 异常/索赔 | `exception_case`、已有处理记录/证据/索赔关联 | 工作台只读投影，不改变责任方、处理记录或索赔关系 |
| 清关资料 | `customs_document` | 作业订单没有 `UPLOADED` 资料时计入待补充/风险 |

## 5. 修改文件

- 后端：`OperationsController.java`、`OperationsQueryApplicationService.java`、`OperationsWorkbenchQuery.java`、`OperationsMapper.java`、`OperationsMapper.xml`、工作台 domain/response DTO。
- 测试：`OperationsQueryApplicationServiceTest.java`、`OperationsWorkbenchControllerWebMvcTest.java`、`OperationsControllerWebMvcTest.java`、`OperationsMapperXmlTest.java`。
- 契约：`openapi/shipflow-api.yaml` 新增 `getOperationsWorkbench`、查询参数和工作台 schemas。
- 生命周期：本文件、P3-03 学习笔记和第三阶段任务清单状态更新。

## 6. 验证和边界

已使用 JDK 21 的隔离构建目录运行工作台定向测试：7 项全部通过，覆盖 JWT 租户/用户传递、平台角色禁止、Asia/Shanghai 今日窗口、财务四项拆分、24 小时风险阈值和未授权店铺拒绝。`OperationsMapperXmlTest` 同时确认 Mapper 只读、不含 `SELECT *`，且包含租户条件和账单/异常资源来源。

尚未在本子阶段执行数据库连接、Flyway/V018/V019、顺丰生产调用、前端 P3-04 改造、Git commit 或 push。全量后端测试、前端测试/构建和最终契约检查需在本阶段交付验证中继续完成；在用户确认前不进入 P3-04。
