# ShipFlow MySQL 数据库设计

## 1. 基础规范

- 数据库：MySQL 8.0。
- 字符集：`utf8mb4`，排序规则：`utf8mb4_0900_ai_ci`。
- 业务主键统一使用 `BIGINT`。
- 金额统一使用 `DECIMAL(18,2)`。
- 重量和尺寸统一使用 `DECIMAL(18,3)`。
- 重量入库单位统一为 kg，尺寸入库单位统一为 cm。
- 所有表包含 `created_at` 和 `updated_at`。
- 历史记录表不设置逻辑删除字段。
- 不使用数据库触发器。
- 所有表统一使用 `utf8mb4_0900_ai_ci`，包括 `claim_record`。

## 2. 租户和公共数据规则

以下为租户业务表，必须有非空 `tenant_id`：店铺、报价、订单、订单快照、地址、包裹、商品、复称、费用调整、轨迹、异常、账单批次、账单明细、对账、索赔。

以下对象为平台公共数据，不强制包含 `tenant_id`：物流商、物流渠道、价格规则、价格阶梯、权限和角色权限关系。

`sys_user.tenant_id`、`sys_role.tenant_id` 和 `audit_log.tenant_id` 允许为空：

- `NULL`：平台对象或平台级审计。
- 非 `NULL`：租户对象或租户业务审计。

`sys_user.scope_tenant_id` 和 `sys_role.scope_tenant_id` 是生成列，表达式为：

```sql
GENERATED ALWAYS AS (IFNULL(tenant_id, 0)) STORED
```

用户唯一索引为 `UNIQUE(scope_tenant_id, username)`；角色唯一索引为 `UNIQUE(scope_tenant_id, role_code)`。

`tracking:callback` 权限只分配给 Mock 物流商接口账号或平台系统账号，不分配给普通商家、仓库或财务用户。

## 3. 表用途和关键设计

| 表 | 用途 | 关键设计 |
|---|---|---|
| `tenant` | 签约商家主体 | 租户编码唯一，支持启用/停用 |
| `merchant_store` | 店铺、品牌或平台账号 | 租户内店铺编码唯一 |
| `sys_user` | 平台和租户用户 | `tenant_id` 可空，使用生成列唯一约束 |
| `sys_role` | 平台或租户角色 | `role_scope` 为 `PLATFORM` 或 `TENANT` |
| `sys_permission` | 权限定义 | 平台公共数据 |
| `sys_user_role` | 用户角色关系 | 绑定用户、角色和可选租户 |
| `sys_role_permission` | 角色权限关系 | 公共关系表 |
| `logistics_provider` | 物流商 | 平台统一维护 |
| `logistics_channel` | 物流渠道 | 平台统一维护，租户只读使用 |
| `logistics_channel_service_country` | 渠道服务国家 | 渠道与国家多行关系，国家编码唯一 |
| `price_rule` | 价格业务版本 | `version` 技术锁，`version_no` 业务版本 |
| `price_rule_tier` | 价格阶梯 | `FIXED` 或 `FIRST_CONTINUE` |
| `quote` | 报价 | 计算结果不可修改，使用 `version` 控制生命周期状态 |
| `shipment_order` | 订单主表 | 幂等键、状态机、费用和版本 |
| `shipment_quote_snapshot` | 报价快照 | 创建后不可修改，不设置乐观锁 |
| `shipment_address` | 地址快照 | 每个订单一条寄件人和一条收件人 |
| `shipment_package` | 单包裹数据 | 与订单一对一 |
| `shipment_item` | 商品申报 | 一个包裹多个商品 |
| `warehouse_measurement` | 仓库复称历史 | 一个包裹多次复称 |
| `fee_adjustment` | 费用调整 | 可追溯到复称记录 |
| `warehouse_outbound_record` | 仓库出库交接 | 一个订单最多一条，物流单号不可重复 |
| `tracking_event` | 轨迹回调 | 组合幂等键，保存原始报文 |
| `exception_case` | 异常单 | 地址、清关、运输异常 |
| `bill_import_batch` | CSV 导入批次 | 文件 Hash 防重复 |
| `bill_detail` | 账单明细 | 物流商明细号防跨文件重复 |
| `reconciliation_record` | 对账记录 | 一条账单明细最多一条 |
| `claim_record` | 索赔记录 | 关联异常单 |
| `audit_log` | 审计日志 | 创建后只追加，不更新、不删除 |

## 4. 价格规则计算

### `FIXED`

使用命中阶梯的 `tier_fee`：

```text
fee = tier_fee
```

### `FIRST_CONTINUE`

使用首重和续重费用：

```text
fee = first_fee + ceil((chargeable_weight - first_weight) / additional_weight) * additional_fee
```

当计费重量不超过首重时：

```text
fee = first_fee
```

阶梯区间使用左闭右开规则：

```text
[min_weight, max_weight)
```

最后一个阶梯的 `max_weight` 可以为空。价格规则发布前必须检查阶梯无重叠、无空缺、按 `tier_no` 顺序排列。

`currency`、`volume_divisor`、`rounding_mode` 和 `rounding_increment` 只保存在 `price_rule`，不在阶梯表重复保存。

价格规则约束：

- `version_no > 0`。
- `tier_no > 0`。
- 所有价格字段非负。
- `min_weight` 左闭、`max_weight` 右开，最后一个 `max_weight` 可为空。
- 发布前必须检查阶梯无重叠、无空缺和顺序正确。

## 5. 可修改性和不可变数据

允许修改：

- 租户、店铺、用户、角色等基础配置，使用乐观锁。
- `DRAFT` 状态的价格规则，使用 `version` 乐观锁。
- `quote` 的生命周期状态可以按规则变化，使用 `version` 乐观锁；报价计算输入、价格规则引用、金额和 `fee_detail` 不允许修改。
- 订单履约状态和财务确认状态，使用 `version` 乐观锁。

创建后不可修改：

- `shipment_quote_snapshot`。
- `shipment_address` 地址快照。
- `shipment_item` 商品申报快照。
- `warehouse_measurement` 历史复称记录。
- `tracking_event` 轨迹事件。
- `bill_detail` 原始账单明细。
- `audit_log` 审计日志。

已发布的 `price_rule` 和阶梯不可修改，调价必须创建新的 `version_no`。报价 `EXPIRED` 优先通过 `valid_to` 动态判断，状态字段用于生命周期记录和查询优化。

订单费用规则：

- 创建订单时 `current_fee = estimated_fee`。
- 费用降低时更新 `current_fee`。
- 费用增加且未确认时不更新 `current_fee`。
- 商家确认涨价后更新 `current_fee` 和 `confirmed_fee`。
- `reconciliation_record.system_amount` 取 `shipment_order.current_fee`。

报价 `fee_detail` 为必填 JSON；创建订单时复制到 `shipment_quote_snapshot.fee_detail`。

费用明细示例：

```json
{
  "base_fee": 18.00,
  "fuel_surcharge": 2.00,
  "remote_area_fee": 0.00,
  "total_fee": 20.00,
  "currency": "CNY",
  "rule_version_no": 1
}
```

第一版不使用触发器保护快照，通过应用层更新限制、数据库权限、审计日志和自动化测试保证不可变。

## 6. 典型查询示例

### 查询租户订单及当前包裹

```sql
SELECT o.order_no, o.current_status, p.package_no,
       p.declared_weight, p.declared_chargeable_weight
FROM shipment_order o
JOIN shipment_package p
  ON p.shipment_order_id = o.id
WHERE o.tenant_id = ?
  AND o.order_no = ?;
```

### 查询订单按事件时间排序的轨迹

```sql
SELECT event_code, event_description, event_time, received_time, process_status
FROM tracking_event
WHERE tenant_id = ?
  AND shipment_order_id = ?
ORDER BY event_time, id;
```

### 查询待财务确认的对账差异

```sql
SELECT r.id, r.shipment_order_id, r.bill_detail_id,
       r.system_amount, r.billed_amount, r.difference_amount
FROM reconciliation_record r
WHERE r.tenant_id = ?
  AND r.reconciliation_status = 'PENDING_CONFIRMATION';
```

### 检查租户幂等订单

```sql
SELECT id, order_no, current_status
FROM shipment_order
WHERE tenant_id = ?
  AND idempotency_key = ?;
```

## 7. 索引设计原则

- 所有业务外键建立普通索引。
- 订单按 `(tenant_id, current_status, created_at)` 查询。
- 轨迹按 `(tenant_id, shipment_order_id, event_time)` 查询。
- 账单按 `(tenant_id, provider_id, provider_bill_detail_no)` 唯一查找。
- 账单批次按 `(tenant_id, provider_id, imported_at)` 查询。
- 对账按 `(tenant_id, reconciliation_status, created_at)` 查询。
- 费用调整按 `(tenant_id, shipment_order_id, created_at)` 查询。

## 8. 一致性约束

- `shipment_order.quote_id` 唯一，确保一份报价最多创建一个订单。
- `shipment_quote_snapshot.shipment_order_id` 唯一，确保一个订单只有一份快照。
- `shipment_package.shipment_order_id` 唯一，确保一单一包裹。
- `warehouse_outbound_record.shipment_order_id` 唯一，确保一个订单最多一条出库交接记录。
- `warehouse_outbound_record(provider_id, tracking_no)` 唯一，确保物流单号交接不重复。
- `shipment_address(shipment_order_id, address_type)` 唯一，确保地址类型不重复。
- `fee_adjustment.warehouse_measurement_id` 唯一且允许为空，确保一次复称最多一条费用调整。
- `exception_case.claim_record` 一对零或一，通过 `claim_record.exception_case_id` 唯一实现。
- `logistics_channel_service_country(channel_id, country_code)` 唯一，确保渠道服务国家不重复。
- `tracking_event(provider_id, tracking_no, event_id)` 唯一，确保回调幂等。
- `bill_import_batch(tenant_id, provider_id, file_hash)` 唯一，确保文件不重复导入。
- `bill_detail(tenant_id, provider_id, provider_bill_detail_no)` 唯一，确保账单明细不跨文件重复。
- `reconciliation_record.bill_detail_id` 唯一，确保一条账单明细最多一个对账记录。

## 9. API 支撑表

### api_idempotency_record

该表用于持久化通用幂等请求。最终唯一索引为 `UNIQUE(scope_tenant_id, operation_id, idempotency_key)`；`request_path` 保留用于请求摘要和审计，不进入唯一索引。状态只能是 `PROCESSING`、`SUCCEEDED`、`FAILED` 或 `EXPIRED`。`response_body` 只允许保存脱敏响应，认证、Token 和密码接口不得使用通用响应缓存。

### auth_refresh_session

该表保存 Refresh Token 会话摘要和轮换链，具有 `UNIQUE(token_hash)`、`UNIQUE(previous_session_id)`、`INDEX(user_id, status, expires_at)`、`INDEX(family_id, status)` 和 `INDEX(expires_at)`。`user_id`、`tenant_id` 和 `previous_session_id` 分别关联现有用户、租户和本表；只保存 HMAC-SHA256 或 SHA-256 摘要，不保存明文 Token。
## 10. API 测试执行契约

`database/qa/003_upgrade_api_test_case_execution_contract.sql` 将 `api_test_case` 从自然语言用例升级为通用 pytest 执行器契约。该迁移只操作独立的 `shipflow_qa` 数据库。

新增字段：

- `setup_steps JSON`：请求前动作数组，支持 `get_csrf`、`login`、`refresh`、`logout`、`create_token_fixture`。
- `extractors JSON`：从响应中提取运行时变量，例如 `json.$.data.accessToken` 保存为 `ACCESS_TOKEN`，Cookie 提取保存为 `REFRESH_COOKIE`。
- `teardown_steps JSON`：请求后的清理动作数组。
- `tags JSON`：模块、测试类型和执行特征标签。
- `execution_order INT`：稳定的执行排序号；状态流用例不得依赖前一条用例的内存上下文。
- `automation_status VARCHAR(32)`：`READY` 可由通用 HTTP 执行器运行，`BLOCKED` 依赖数据库状态或业务 fixture，`DEFERRED` 依赖特殊 JWT 签发或生产 Cookie 配置。
- `environment_scope VARCHAR(32)`：适用环境，例如 `QA`、`LOCAL`、`INTEGRATION` 或 `PRODUCTION`。

### assertions 对象协议

`assertions` 的每个元素必须是对象，不再允许中文字符串：

```json
{
  "source": "status_code|json|header|cookie|body",
  "path": "字段路径，没有则为null",
  "operator": "eq|ne|exists|not_exists|not_empty|contains|not_contains|is_empty",
  "expected": "预期值，没有则为null"
}
```

`source=json` 的 `path` 使用 JSONPath；`source=header` 和 `source=cookie` 使用名称；`source=body` 用于完整响应体或特殊处理断言。数据库状态断言不能伪装成 HTTP 断言，迁移会保留其语义并将用例标记为 `BLOCKED`，由 pytest fixture 进行数据库断言。

### 执行上下文与敏感数据

执行器维护单次用例的内存上下文。SQL 中只允许出现 `${变量名}` 占位符；真实密码、Access Token、Refresh Cookie、HMAC 密钥和数据库密码必须由运行时环境或响应提取器注入，不得写入 `shipflow_qa` 或测试报告。Refresh Token 只从 Cookie 上下文读取，不能从 JSON 响应读取。

`STATE_FLOW` 用例必须在 `setup_steps` 中独立创建自己的 Token、CSRF 或测试 fixture，并在 `teardown_steps` 中清理；不能通过 `execution_order` 或其他用例的副作用建立前置条件。

迁移末尾的校验语句确认：认证用例总数为 52、`case_no` 重复数为 0、`assertions` 中不存在非对象元素、执行字段不存在空值，并输出 `READY`、`BLOCKED`、`DEFERRED` 数量。
