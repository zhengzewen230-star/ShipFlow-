# P4-05 店铺资源迁移与后端契约验收

更新时间：2026-08-19（Asia/Shanghai）

| 验收项 | 状态 | 证据/边界 |
|---|---|---|
| 测试库识别与 Flyway V019 门禁 | PASS | 目标库为 `shipflow`，MySQL 8.0.46，迁移前版本 V019 |
| V020 结构迁移 | PASS | 迁移后版本 V020；两张新表、复合外键、唯一索引和审计字段已验证 |
| V021 回填 | SKIPPED | 无可靠且获批来源；回填 0 行，未创建文件 |
| 后端店铺资源契约 | PASS | 详情扩展、地址/渠道读写接口、scope 校验、版本/幂等/审计已实现 |
| 前端 stores service 契约 | PASS | 真实 URL、版本字段、Idempotency-Key 测试已补齐 |
| 完整前端页面配置能力 | 未执行 | 当前阶段只补 service 契约，未伪造配置成功 |
| 生产数据库/顺丰生产调用 | 未执行 | 明确禁止 |

## 必须保留的限制

现有店铺没有被自动填充地址、渠道或地区；详情在没有配置时返回 `null + unavailableFields`。商家业务员不能通过接口绕过 `tenant_id + user_id + role + active sys_user_store_scope`。V020 不得重复执行，V021 需另行批准可靠来源和回填范围。

## V021 再次门禁核验（2026-08-19）

- 只读数据库核验：`shipflow`、MySQL 8.0.46、Flyway 最新成功版本 V020。
- `merchant_store` 不含国家/地址/默认渠道字段；`shipment_address` 仅为订单地址快照。
- `merchant_store_address` 0 行、`merchant_store_channel` 0 行；公共 `logistics_channel` 有数据但没有店铺绑定来源。
- 结论：V021 仍为 `SKIPPED/BLOCKED`，未创建、未执行、未写入业务数据。不得从订单快照或公共渠道猜测店铺资源。
- P4-06 暂不进入；前置条件是批准可审计的地址/渠道来源和明确回填范围。

## 数据库与项目主数据对账（2026-08-19）

| 权威主数据 | 数据库实际值 | 自动匹配 | 结论 |
|---|---|---|---|
| `STORE_US_001` / AMAZON | `TENANT_DEMO_001` / `MARKETPLACE_A` / ACTIVE | 否 | 平台编码需确认 |
| `STORE_JP_001` / RAKUTEN | `TENANT_DEMO_001` 和 `TENANT_DEMO_002` 均有同编码 | 否 | 租户归属和重复编码需确认 |
| `STORE_EU_001` / SHOPIFY | `TENANT_DEMO_002` / `MARKETPLACE_B` / ACTIVE | 否 | 不得自动迁移到商家一 |
| `WH_SZ_01` | `warehouse` 表不存在 | 否 | 需正式仓库主数据 Schema |
| 5 个 SF 渠道编码 | 权威编码均不存在；数据库有 4 个其他渠道编码 | 否 | 需平台编码映射和渠道明细确认 |
| 地区关系 | `region_carrier_relation` 不存在 | 否 | 需正式结构设计 |

当前数据库对象存在：`merchant_store`、`merchant_store_address`、`merchant_store_channel`、`logistics_channel`、`sys_user_store_scope`、`audit_log`；不存在 `warehouse`、`region_carrier_relation`。V020 与现有数据库兼容，但只覆盖店铺地址/渠道资源表；V021 必须保持为数据回填，缺失主数据结构需另行结构迁移。
## 对账收尾验收（2026-08-19）
| 验收项 | 结果 | 证据/限制 |
|---|---|---|
| 自动匹配数量 | PASS（0） | 无对象满足安全自动匹配条件 |
| `STORE_JP_001` 唯一租户归属 | BLOCKED | 数据显示租户归属不唯一，待业务确认 |
| `STORE_EU_001` 租户处理 | PASS | 保留在 `TENANT_DEMO_002`，未迁移 |
| 平台编码映射 | BLOCKED | `MARKETPLACE_A/B` 与业务平台编码无批准映射 |
| `warehouse` | BLOCKED | 表不存在，需独立结构迁移评审 |
| `region_carrier_relation` | BLOCKED | 表不存在，需独立结构迁移评审 |
| V021 | SKIPPED/BLOCKED | 未创建、未执行；只能承担纯数据回填 |
| 本轮数据库业务数据写入 | PASS（0） | 未执行 DML；历史订单、轨迹、账单和快照未改变 |
P4-05/P4-06 不能进入实现或验收阶段，直到业务确认主数据事项、DBA 批准两个独立结构迁移及备份/回滚方案，并批准 V021 的可靠回填来源和范围。
## 对账结论确认版（2026-08-19）

| 项目 | 结果 |
|---|---|
| 自动匹配 | PASS（0） |
| 本轮业务数据写入 | PASS（0） |
| `STORE_JP_001` 唯一归属 | BLOCKED |
| `STORE_EU_001` 保持 `TENANT_DEMO_002` | PASS，未迁移 |
| 平台、渠道、区域和店铺绑定 | BLOCKED，未获批准 |
| `warehouse` 结构 | BLOCKED，不存在 |
| `region_carrier_relation` 结构 | BLOCKED，不存在 |
| V021 | SKIPPED，文件不存在，仅允许规划为纯数据回填 |

本轮未执行数据库、Flyway、结构迁移或业务代码变更；P4-05/P4-06 不具备进入条件。
## 最终差异处理验收门禁（2026-08-19）

| 验收项 | 结果 | 责任方/后置条件 |
|---|---|---|
| 自动匹配数量 | PASS（0） | 无对象满足安全回填条件 |
| `STORE_JP_001` 重复租户 | BLOCKED | 业务确认保留/停用/删除策略后，DBA 才能按主键范围实施 |
| `STORE_EU_001` | PASS（保留现租户） | 纳入跨租户 404 验收，不得迁移 |
| 5 条权威渠道 | BLOCKED | 物流产品和平台主数据负责人确认编码、承运商、区域、计费规则 |
| `warehouse` | BLOCKED | DBA 确认独立 Schema/迁移 |
| `region_carrier_relation` | BLOCKED | DBA 确认独立 Schema/迁移或替代字段契约 |
| V021 | BLOCKED | 未创建、未执行，仅允许纯数据回填 |
| 业务数据保护 | PASS（未变更） | 订单7、报价14、轨迹20、快照14 |

未授权跨租户资源的验收对象应使用`TENANT_DEMO_002`下的`STORE_EU_001`，确认其他租户访问统一404。回滚验收必须验证不触碰订单、报价、轨迹、账单和历史快照，且不得使用无条件 DELETE。
