# P4-05 店铺资源后端契约设计

更新时间：2026-08-19（Asia/Shanghai）

## 阶段状态

V020 结构迁移已在明确授权的 `shipflow` 测试业务库执行成功；V021 未创建、未执行。本文记录已落地的最小后端契约，不代表已有店铺已经配置默认地址或默认渠道。

## 资源模型

- `merchant_store_address` 保存租户店铺级发货地址，使用 `tenant_id + store_id` 复合归属；地址编码在店铺内唯一。
- `merchant_store_channel` 保存租户店铺与平台公共物流渠道的关联，使用 `tenant_id + store_id` 复合归属；同一店铺可绑定多个渠道，但 ACTIVE 默认渠道最多一个。
- 两张表均保留状态、版本、创建/更新人和 UTC 审计时间；生成列唯一索引保证 ACTIVE 默认资源唯一。
- `shipment_address` 仍是订单地址快照，历史订单不回写；没有可靠来源时不生成 V021 回填数据。

## 后端接口

| 方法 | 路径 | 用途 | 写入保护 |
|---|---|---|---|
| GET | `/api/v1/stores/{storeId}/default-address` | 查询默认地址 | tenant/user/role/scope/resource ownership |
| PUT | `/api/v1/stores/{storeId}/default-address` | 新增或更新默认地址 | 租户管理员、版本号、Idempotency-Key、审计 |
| GET | `/api/v1/stores/{storeId}/logistics-channels` | 查询店铺渠道 | tenant/user/role/scope/resource ownership |
| PUT | `/api/v1/stores/{storeId}/default-logistics-channel` | 设置默认渠道 | 租户管理员、版本号、Idempotency-Key、审计 |

未授权资源统一按现有 StoreMapper scope 过滤并返回 404；商家业务员仍仅能读取授权店铺。地址电话和邮箱、平台账号均不以原值返回。

## 未配置与停用规则

未配置资源返回 `null`，详情通过 `unavailableFields` 标识；不使用前端静态默认值。停用店铺、地址或渠道不得删除历史订单快照；停用店铺不得创建新订单，停用渠道不得用于新报价/新订单，具体业务规则仍以已批准矩阵为准。
## 对账收尾结论（2026-08-19）
本轮仅完成数据库与项目主数据对账，不执行 V021、DDL、DML、Flyway 或业务代码修改。自动匹配数量为 **0**；本轮对账收尾的数据库业务数据写入为 **0**（不含此前已完成的 V020 结构 DDL）。
已确认：`STORE_JP_001` 租户归属不唯一；`STORE_EU_001` 属于 `TENANT_DEMO_002`，不得自动迁移；`MARKETPLACE_A/B` 与 `AMAZON`、`RAKUTEN`、`SHOPIFY` 尚无批准映射；`warehouse` 和 `region_carrier_relation` 不存在；V021 只能承担结构具备且主数据来源获批后的纯数据回填。
### 业务待确认表
| 编号 | 待确认事项 | 当前结论 |
|---|---|---|
| B-01 | `STORE_JP_001` 的唯一租户归属 | 待确认，不自动迁移 |
| B-02 | `MARKETPLACE_A/B` 到业务平台编码的映射 | 待批准，不猜测 |
| B-03 | 三个业务店铺是否均属于同一商家 | 待确认，按现有 tenant 保留 |
| B-04 | 五个渠道的权威编码和属性 | 待确认，现有渠道不自动替换 |
| B-05 | `WH_SZ_01` 的正式仓库主数据归属 | 待确认 |
### DBA 待确认表
| 编号 | 待确认事项 | 当前结论 |
|---|---|---|
| D-01 | 是否批准新增 `warehouse` 结构迁移 | 待批准，独立迁移 |
| D-02 | 是否批准新增 `region_carrier_relation` 结构迁移 | 待批准，独立迁移 |
| D-03 | 字段、复合归属外键、索引、唯一约束、审计字段、备份和回滚边界 | 待设计评审 |
### 后续迁移拆分
1. 独立结构迁移：`warehouse`；
2. 独立结构迁移：`region_carrier_relation`；
3. `V021`：仅在上述结构、租户归属、平台映射、五个渠道和店铺绑定均获批准后，执行店铺地址、店铺渠道及地区关系纯数据回填。
在上述确认完成前，P4-05/P4-06 保持 `BLOCKED`；保留现有租户、店铺、订单、权限和历史快照，不删除重复店铺、不自动迁移租户、不猜测平台映射。
## 对账结论确认版（2026-08-19）

本轮仅更新设计门禁，不执行数据库或业务代码变更。自动匹配为 0，本轮业务数据写入为 0；当前最高 Flyway 版本为 V020，V021 文件不存在，未执行结构 DDL、DML 或 Flyway。

### 业务确认表

| 编号 | 待确认事项 | 当前状态 |
|---|---|---|
| B-01 | `STORE_JP_001` 唯一租户归属 | 待业务确认 |
| B-02 | `STORE_EU_001` 是否继续保持 `TENANT_DEMO_002` | 默认保持，禁止自动迁移 |
| B-03 | `MARKETPLACE_A/B` 与 `AMAZON`、`RAKUTEN`、`SHOPIFY` 的批准映射 | 待批准，禁止猜测 |
| B-04 | 3 个店铺是否属于同一商家 | 待业务确认 |
| B-05 | 渠道属性、目的地区域及店铺渠道绑定 | 待业务确认 |
| B-06 | `WH_SZ_01` 仓库归属和负责人 | 待业务确认 |

### DBA 确认表

| 编号 | 待确认事项 | 当前状态 |
|---|---|---|
| D-01 | `warehouse` Schema | 待 DBA 评审 |
| D-02 | `region_carrier_relation` Schema | 待 DBA 评审 |
| D-03 | 迁移版本规划 | 待 DBA 确认 |
| D-04 | tenant/store 复合外键和唯一索引 | 待 DBA 确认 |
| D-05 | 初始化数据来源 | 待批准 |
| D-06 | 备份与回滚方案 | 待批准 |
| D-07 | 测试库/业务库执行权限 | 待确认，未授权前不执行 |

### 迁移拆分

1. 独立 `warehouse` 结构迁移；
2. 独立 `region_carrier_relation` 结构迁移；
3. `V021` 仅执行店铺地址、店铺渠道和地区关系纯数据回填。

在所有业务和 DBA 门禁完成前，P4-05/P4-06 继续 `BLOCKED`。
## 最终差异处理方案（2026-08-19）

### 冲突与责任方

| 对象 | 当前冲突 | 责任方 | 处理原则 |
|---|---|---|---|
| `STORE_JP_001` | 同时存在于两个租户 | 业务确认 + DBA 执行 | 不使用跨租户 upsert，不删除或停用任一记录 |
| `STORE_EU_001` | 属于 `TENANT_DEMO_002` | 业务确认 | 保持现租户，不迁移到 `TENANT_DEMO_001` |
| 平台编码 | `MARKETPLACE_A/B` 与业务平台编码未映射 | 业务/平台主数据负责人 | 不猜测映射 |
| 权威渠道 | 5 条目标渠道均未与现有记录安全匹配 | 物流产品/平台主数据负责人 | 未确认不写入 |
| 缺失结构 | `warehouse`、`region_carrier_relation` 不存在 | DBA/架构评审 | 不由 V021 隐含创建 |

### STORE_JP_001 重复记录方案

在业务确认前，两个租户记录均保持原状，V021 禁止回填该店铺。业务必须明确：

1. 保留哪条记录作为唯一有效店铺；
2. 另一条记录是删除、停用还是保留为历史/待核查记录；
3. 若停用，生效时间、原因和审计责任人；
4. 若删除，必须提供明确主键范围、备份和审批记录。

落地时必须使用明确的`tenant_id + store_id`或完整主键范围，禁止使用仅按`store_code`匹配的`ON DUPLICATE KEY UPDATE`。

### 平台与渠道差异表

| 权威渠道 | 承运商 | 目的地区域 | 计费规则 | 现有记录 | 状态 |
|---|---|---|---|---|---|
| `SF_INTL_EXP_US` | 顺丰国际 | US | 除数5000；首续重0.5/0.5kg | 无 | 待确认 |
| `SF_INTL_STD_US` | 顺丰国际 | US | 除数6000；首续重0.05/0.05kg | 无 | 待确认 |
| `SF_INTL_STD_JP` | 顺丰国际 | JP | 除数6000；首续重0.5/0.5kg | 无 | 待确认 |
| `SF_INTL_STD_DE` | 顺丰国际 | DE/FR/IT/ES | 除数6000；首续重0.1/0.1kg | 无 | 待确认 |
| `SF_INTL_STD_GB` | 顺丰国际 | GB | 除数6000；首续重0.1/0.1kg | 无 | 待确认 |

现有`merchant_store.platform_code`与`logistics_channel.channel_code/provider_id`分属不同主数据域，不得互相替换；`MARKETPLACE_A/B`不得猜测映射为`AMAZON`、`RAKUTEN`或`SHOPIFY`。未完成编码、承运商、区域和计费规则确认前，不写入渠道或店铺渠道关联。

### 缺失表处理

`warehouse`和`region_carrier_relation`不属于 V021。DBA 必须分别确认是否新增独立结构迁移；若提议使用`logistics_channel.service_area/destination_regions`替代地区关系，必须先补充字段定义、区域粒度、租户/店铺归属、唯一性、历史版本和兼容性评审，否则仍视为不可替代。

### V021 边界

V021 只能对已存在且已确认归属的`merchant_store`、`merchant_store_address`、`merchant_store_channel`及既有公共`logistics_channel`执行纯数据回填。当前自动匹配 0、V021 未创建/未执行、数据库业务数据写入 0，因此继续 BLOCKED。
