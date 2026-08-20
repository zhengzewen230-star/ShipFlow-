# P4-05 店铺资源迁移与后端契约实施记录

更新时间：2026-08-19（Asia/Shanghai）

## 数据库门禁与迁移

- 目标库：`shipflow`；MySQL 8.0.46；迁移前 Flyway 版本 V019。
- V020 `V020__add_merchant_store_resource_model.sql`：PASS，创建店铺地址表、店铺渠道关联表、复合归属外键、默认唯一索引、状态/版本/审计字段。
- 迁移后 Flyway 版本：V020。
- V020 后两张资源表均为 0 行；业务数据 DML 回填 0 行。
- V021 `V021__backfill_merchant_store_resources.sql`：SKIPPED，未创建，原因是没有可靠且获批准的地址/渠道初始化来源；不得从订单地址快照或公共渠道主数据推断。
- 未修改订单历史快照，未执行顺丰生产调用，未提交或推送 Git。

## 代码变更

- 新增 StoreAddress、StoreChannel 领域模型、响应 DTO、更新请求 DTO、StoreResourceMapper 及 XML。
- StoreApplicationService 增加详情资源摘要、默认地址读写、渠道读取和默认渠道设置。
- 写接口继续校验 `tenant_id`、`user_id`、角色、`active sys_user_store_scope` 与资源归属；租户管理员配置写权限采用版本号和 Idempotency-Key，并写入审计日志。
- `GlobalExceptionHandler` 增加无效公共渠道映射 `STORE-1004`。
- OpenAPI 增加 4 个店铺资源 operation、schema 和 422 响应。
- 前端 stores service 增加资源类型和 4 个真实 API 方法；未添加静态数据或自动回填。
- `database/schema.sql` 将店铺渠道到公共渠道的外键改为在公共渠道表创建后追加，避免全量初始化脚本的建表顺序问题；不重复执行 V020。

## 边界

V021 初始化、店铺配置前端表单的完整浏览器写入验收、生产库迁移和生产物流调用均未执行。当前可进入后端契约测试复核，但不能宣称已有店铺已具备默认地址/渠道。

## 主数据对账结果（2026-08-19）

- 数据库与项目均确认：V020 已成功注册；本次对账未执行任何 DDL、DML 或 Flyway。
- `STORE_US_001`、`STORE_JP_001` 在 `TENANT_DEMO_001` 下存在，但平台实际为 `MARKETPLACE_A`；`STORE_EU_001` 在 `TENANT_DEMO_002` 下存在，平台实际为 `MARKETPLACE_B`。不能自动迁移租户，也不能把平台编码猜测为 AMAZON、RAKUTEN 或 SHOPIFY。
- 权威 5 个渠道编码在数据库中不存在；现有 4 个渠道编码为另一套 Alpha/Beta 主数据，不能自动替换。
- 项目和数据库没有 `warehouse` 主数据表，仅有 `warehouse_measurement`、`warehouse_outbound_record` 等作业表；没有 `region_carrier_relation`。`logistics_channel_service_country` 仅是公共渠道服务国家表，不能直接替代店铺/租户区域关系。
- 结论：V021 执行条件不满足，保持 `BLOCKED`；需要先完成正式租户、平台、仓库 Schema、区域关系 Schema 和渠道绑定对账。
## 对账收尾实施记录（2026-08-19）
- 自动匹配：0；本轮对账收尾数据库业务数据写入：0。
- `STORE_JP_001` 的租户归属不唯一；`STORE_EU_001` 明确属于 `TENANT_DEMO_002`，不做自动修正或迁移。
- `MARKETPLACE_A/B` 与 `AMAZON`、`RAKUTEN`、`SHOPIFY` 没有批准映射；五个权威渠道编码和属性未完成对账。
- `warehouse`、`region_carrier_relation` 不存在；现有表不能安全替代，因此不生成 V021 结构内容。
- V021 的职责边界固定为纯数据回填，仅能在结构迁移和主数据确认后执行店铺地址、店铺渠道、地区关系回填。
- 本轮未执行 V021、DDL、DML、Flyway，未修改业务代码、权限数据、订单、轨迹、账单或历史快照。
## 推荐实施顺序
1. 业务确认租户归属、平台映射、店铺同属商家关系、五个渠道权威属性及 `WH_SZ_01` 归属；
2. DBA 单独评审并批准 `warehouse` 结构迁移；
3. DBA 单独评审并批准 `region_carrier_relation` 结构迁移；
4. 仅在可靠来源、备份、回滚方案和执行授权齐备后创建 V021，并执行幂等纯数据回填；
5. 回填后再进行后端契约、权限和验收复核。
当前状态：`BLOCKED`。不得通过恢复 tenant-only 查询、跨租户迁移或猜测平台编码来解除阻塞。
## 对账结论确认版（2026-08-19）

- 本轮无数据库或业务代码变更，业务数据写入 0。
- `STORE_JP_001` 归属不唯一；`STORE_EU_001` 保持 `TENANT_DEMO_002`，不自动合并或迁移。
- 平台映射、渠道属性、目的地区域、店铺渠道绑定和仓库主数据均未获批准。
- `warehouse` 与 `region_carrier_relation` 缺失，V021 不承担建表职责。
- 后续仅按“两个独立结构迁移 → V021 纯数据回填”的顺序规划，当前不创建任何 SQL 文件。
- 当前状态：`BLOCKED`。
## 最终差异处理实施边界（2026-08-19）

本轮不执行数据库或代码变更。实施前置处理如下：

- `STORE_JP_001`：业务确认唯一租户后，才能生成带明确租户和主键范围的处理脚本；在此之前不回填、不删除、不停用。
- `STORE_EU_001`：固定保留在`TENANT_DEMO_002`，并作为跨租户 404 验收对象；禁止迁移。
- 平台和渠道：店铺平台域与物流渠道域分开对账，5 条权威渠道均标记为未匹配，未确认不写入。
- `warehouse`、`region_carrier_relation`：由 DBA 分别评审独立结构迁移；V021 不创建表。
- 本次回滚不得使用无条件 DELETE，必须绑定变更批次、明确主键范围、审计记录和备份点；订单、报价、轨迹、账单及历史地址快照不在回滚范围。

保护性基线继续为：订单 7、报价 14、轨迹事件 20、订单地址快照 14；本轮数据库写入 0，V021 未创建、未执行。
