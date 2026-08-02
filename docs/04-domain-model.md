# ShipFlow 领域模型

## 1. 建模范围

ShipFlow 第一版面向需要向日本、美国和欧洲发货的中国跨境商家，覆盖报价、订单、单包裹仓储作业、物流轨迹、账单导入、费用对账、异常和索赔。

报价阶段使用商家申报重量和尺寸；仓库复称阶段单独保存仓库实际重量和尺寸。两类数据不可混淆：报价快照只保存订单创建时的申报数据和规则依据，仓库实际数据保存在复称记录中。

## 2. 领域对象职责

| 对象 | 表 | 职责 |
|---|---|---|
| 租户 | `tenant` | 表示一个签约商家主体 |
| 商家店铺 | `merchant_store` | 保存租户下的店铺、品牌或平台账号 |
| 用户 | `sys_user` | 保存平台用户和租户用户；`tenant_id` 为空表示平台用户 |
| 角色 | `sys_role` | 保存平台角色或租户角色，范围由 `role_scope` 区分 |
| 权限 | `sys_permission` | 保存公共权限定义 |
| 用户角色关系 | `sys_user_role` | 关联用户与角色 |
| 角色权限关系 | `sys_role_permission` | 关联角色与权限 |
| 物流商 | `logistics_provider` | 平台统一维护的公共物流商 |
| 物流渠道 | `logistics_channel` | 平台统一维护的公共渠道和基础计费配置 |
| 渠道服务国家 | `logistics_channel_service_country` | 保存渠道可服务的国家编码 |
| 价格规则 | `price_rule` | 保存渠道价格规则；`version_no` 是业务版本，`version` 是技术乐观锁 |
| 价格阶梯 | `price_rule_tier` | 表达固定价或首重续重计费区间 |
| 报价 | `quote` | 保存商家申报数据对应的报价结果和有效期 |
| 物流订单 | `shipment_order` | 保存订单主信息、幂等键和履约状态 |
| 订单报价快照 | `shipment_quote_snapshot` | 保存订单创建时不可变的报价依据 |
| 订单地址 | `shipment_address` | 保存寄件人和收件人地址快照 |
| 订单包裹 | `shipment_package` | 第一版与订单一对一，保存申报重量、尺寸、体积重量和计费重量 |
| 订单商品 | `shipment_item` | 保存跨境物流必要的商品申报数据 |
| 仓库复称 | `warehouse_measurement` | 保存仓库实际重量、尺寸、复称人员和复称时间 |
| 费用调整 | `fee_adjustment` | 保存费用变化，必须可追溯到订单和具体复称记录；人工调整可无复称记录 |
| 仓库出库交接 | `warehouse_outbound_record` | 保存仓库出库、物流商和物流单号交接事实 |
| 轨迹事件 | `tracking_event` | 保存物流商回调、原始报文和处理结果 |
| 异常处理单 | `exception_case` | 管理地址、清关和运输异常 |
| 账单导入批次 | `bill_import_batch` | 保存统一 CSV 文件摘要和导入统计 |
| 账单明细 | `bill_detail` | 保存物流商账单行和业务唯一明细号 |
| 对账记录 | `reconciliation_record` | 保存系统金额与账单金额差异及财务处理结果 |
| 索赔记录 | `claim_record` | 保存异常件索赔信息 |
| 审计日志 | `audit_log` | 保存平台级或租户级审计事件，创建后不可修改和删除 |

## 3. 关系基数

### 租户、用户和权限

- `Tenant 1:N MerchantStore`：一个租户管理多个店铺。
- `Tenant 1:N SysUser`：租户用户；平台用户不绑定租户。
- `Tenant 1:N SysRole`：租户角色；平台角色不绑定租户。
- `SysUser N:M SysRole`：通过 `SysUserRole` 关联。
- `SysRole N:M SysPermission`：通过 `SysRolePermission` 关联。

### 公共物流配置

- `LogisticsProvider 1:N LogisticsChannel`。
- `LogisticsChannel 1:N LogisticsChannelServiceCountry`。
- `LogisticsChannel 1:N PriceRule`。
- `PriceRule 1:N PriceRuleTier`。

物流商、渠道和价格规则由平台统一维护，不属于单个租户。租户只能使用已启用的公共渠道，不能修改渠道基础配置。价格规则为公共渠道配置，已发布版本不可修改，只能创建新的 `version_no`。

### 报价、订单和包裹

- `Tenant 1:N Quote`。
- `MerchantStore 1:N Quote`。
- `LogisticsChannel 1:N Quote`。
- `Quote 1:0..1 ShipmentOrder`：一份报价最多创建一个订单；未使用报价可以没有订单。
- `ShipmentOrder 1:1 ShipmentQuoteSnapshot`：订单必须且只能有一份报价快照。
- `ShipmentOrder 1:1 ShipmentPackage`：第一版一个订单只能有一个包裹。
- `ShipmentPackage 1:N ShipmentItem`。
- `ShipmentOrder 1:1 ShipmentAddress(SENDER)`。
- `ShipmentOrder 1:1 ShipmentAddress(RECEIVER)`。

### 仓库、费用和异常

- `ShipmentPackage 1:N WarehouseMeasurement`：一个包裹可以多次复称。
- `ShipmentOrder 1:N FeeAdjustment`。
- `WarehouseMeasurement 1:0..1 FeeAdjustment`：一次复称最多产生一条费用调整；人工调整可以没有复称记录。
- `ShipmentOrder 1:1 WarehouseOutboundRecord`：订单最多一条仓库出库交接记录。
- `ShipmentPackage 1:1 WarehouseOutboundRecord`：出库记录对应订单包裹。
- `LogisticsProvider 1:N WarehouseOutboundRecord`。
- `ShipmentOrder 1:0..N ExceptionCase`。
- `ExceptionCase 1:0..1 ClaimRecord`：一个异常单最多一条索赔记录。

### 轨迹、账单和对账

- `LogisticsProvider 1:N TrackingEvent`。
- `ShipmentOrder 1:N TrackingEvent`。
- `BillImportBatch 1:N BillDetail`。
- `ShipmentOrder 1:N BillDetail`：账单明细可先未匹配订单，匹配后关联订单。
- `BillDetail 1:0..1 ReconciliationRecord`。
- `ShipmentOrder 1:N ReconciliationRecord`。

## 4. 租户隔离

租户业务数据必须包含 `tenant_id`，并在应用查询、写入、关联和授权时同时校验租户边界。`audit_log.tenant_id` 允许为空：空值表示平台级审计，有值表示租户业务审计。

平台公共对象不强制包含 `tenant_id`：`logistics_provider`、`logistics_channel`、`price_rule`、`price_rule_tier`、`sys_permission` 和 `sys_role_permission`。`sys_user`、`sys_role` 的 `tenant_id` 允许为空，空值表示平台对象。

`sys_user.scope_tenant_id` 和 `sys_role.scope_tenant_id` 使用生成列：

```sql
GENERATED ALWAYS AS (IFNULL(tenant_id, 0)) STORED
```

用于统一实现平台对象和租户对象的唯一性边界。

## 5. 报价、快照和复称

报价计算输入包括商家申报重量、长宽高、物流渠道和有效价格规则。报价保存商家申报重量、尺寸、体积重量、计费重量、金额、币种、`fee_detail`、有效期和规则版本。报价的计算输入、价格规则引用、金额和费用明细创建后不可修改；只有生命周期状态可以按规则变化。`EXPIRED` 优先依据 `valid_to` 动态判断。

报价包含技术乐观锁 `version`，用于草稿或生命周期状态更新；报价不允许修改计算结果。

订单创建时 `current_fee = estimated_fee`。费用降低时更新 `current_fee`；费用增加且未确认时保持原 `current_fee`；商家确认后更新 `current_fee` 和 `confirmed_fee`。对账的 `system_amount` 取订单 `current_fee`。

仓库复称只写入 `warehouse_measurement`，保存仓库实际重量、仓库实际尺寸、仓库实际体积重量和仓库实际计费重量。复称后产生的费用调整必须关联具体复称记录；人工费用调整允许 `warehouse_measurement_id` 为空。

订单报价快照从 `quote.fee_detail` 复制费用明细 JSON，创建后完全不可修改且不增加技术乐观锁。第一版不使用数据库触发器保护报价或报价快照；不可变性由应用层更新限制、数据库权限、审计日志和自动化测试共同保证。

## 6. 订单、轨迹、账单和对账

- 订单使用 `idempotency_key`，同一租户相同幂等键只能创建一个订单。
- 轨迹使用 `provider_id + tracking_no + event_id` 幂等；重复事件返回幂等成功，但不重复产生业务影响。
- 轨迹全部保存，按 `event_time` 展示，订单状态只能向前推进。
- 统一 CSV 导入账单，文件级使用 `file_hash`，明细级使用 `provider_bill_detail_no` 防止重复导入。
- 金额完全一致时自动关闭对账；非零差异必须由财务人员确认。

## 7. 单位和精度

所有 API 输入的重量统一转换为 kg，尺寸统一转换为 cm 后入库。数据库内部不混合保存多种单位。API 接收其他单位时必须先完成单位转换、范围校验和精度校验。

金额使用 `DECIMAL(18,2)`；重量和尺寸使用 `DECIMAL(18,3)`。

## 8. API 支撑持久化对象

### ApiIdempotencyRecord

`api_idempotency_record` 保存通用写接口的幂等处理状态、规范化请求摘要和脱敏响应。`scope_tenant_id=0` 表示平台作用域，租户接口使用真实 `tenant_id`；该字段不建立租户外键。最终唯一边界为 `scope_tenant_id + operation_id + idempotency_key`，`request_path` 只用于请求摘要和审计。

认证、Token 和密码接口不使用通用响应缓存。相同唯一键且摘要一致时返回原结果，摘要不同返回 `COMMON-1009`，处理中返回 `COMMON-1010`。

### AuthRefreshSession

`auth_refresh_session` 保存 Refresh Token 会话链的摘要和轮换状态。数据库只保存 HMAC-SHA256 或 SHA-256 摘要，不保存明文 Token。`previous_session_id` 唯一约束保证同一个会话最多生成一个后继会话，避免并发刷新产生多个后继。
