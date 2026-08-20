# P4-04 店铺资源模型设计与迁移方案

更新时间：2026-08-18（Asia/Shanghai）

## 1. 阶段结论

P4-04 当前只完成资源模型、API 契约和迁移方案设计，未创建或执行 Flyway，未写数据库，未修改 Controller、Service、Mapper/XML。

当前详情中的 `countryRegion`、`defaultShippingAddress`、`defaultLogisticsChannel` 返回 `null + unavailableFields` 是真实后端契约结果，不是前端错误。进入迁移与后端实现前，需要业务确认字段口径、历史数据回填策略、平台管理员边界，并获得数据库迁移授权。

## 2. 只读表结构证据

| 对象 | 当前结构和用途 | P4-04 判断 |
|---|---|---|
| `merchant_store` | `tenant_id`、店铺编码/名称、平台账号、`status`、`version`、时间字段；租户+店铺编码和租户+平台账号唯一 | 没有国家/地区、默认地址、默认渠道字段 |
| `shipment_address` | `tenant_id`、`shipment_order_id`、`address_type`、联系人、国家、省州、城市、地址行、邮编；订单+地址类型唯一 | 订单地址快照，不能作为店铺默认地址 |
| `logistics_channel` | 平台公共渠道，`provider_id`、渠道编码、运输方式、服务区域、状态、版本 | 没有 `tenant_id`，不能直接改成租户数据 |
| `logistics_channel_service_country` | 渠道与 ISO 国家编码关系，渠道+国家唯一 | 可用于校验渠道服务国家 |
| `quote` / `shipment_order` | 分别保存 `store_id`、`channel_id`；订单还保存目的国和地址快照 | 历史订单只读快照，不回写店铺配置 |
| `audit_log` | 租户、操作人、动作、资源、请求 ID、结果、原因、JSON detail、UTC 时间 | 可复用记录店铺地址/渠道变更 |
| `sys_user_store_scope` | `tenant_id`、`user_id`、`store_id`、ACTIVE/DISABLED；租户+用户+店铺唯一 | 所有店铺配置读写必须复用现有资源边界 |

当前迁移文件最高为 `V019__add_exception_processing_evidence.sql`。本阶段没有连接数据库读取运行态，仅依据仓库 schema、迁移和代码静态证据设计。

## 3. 推荐最小数据模型

### 3.1 店铺默认地址表：`merchant_store_address`

建议字段：

| 字段 | 类型/规则 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 地址配置主键 |
| `tenant_id` | BIGINT NOT NULL | 外键到 `tenant.id` |
| `store_id` | BIGINT NOT NULL | 与 `tenant_id` 组成店铺资源外键 |
| `address_code` | VARCHAR(64) | 租户内店铺地址编码，便于审计和幂等 |
| `contact_name` | VARCHAR(128) | 联系人 |
| `company_name` | VARCHAR(128) NULL | 公司名称 |
| `phone` | VARCHAR(64) | 联系电话；日志不得记录原值 |
| `email` | VARCHAR(128) NULL | 邮箱；日志不得记录原值 |
| `country_code` | CHAR(2) | ISO 3166-1 alpha-2，作为详情国家/地区来源 |
| `state_province` / `city` / `district` | VARCHAR(128) | 地区字段 |
| `address_line1` / `address_line2` | VARCHAR(255) | 地址行 |
| `postal_code` | VARCHAR(32) | 邮编 |
| `status` | VARCHAR(32) | `ACTIVE`、`DISABLED` |
| `is_default` | TINYINT(1) | 默认标记 |
| `version` | BIGINT | 乐观锁版本 |
| `created_by` / `updated_by` | BIGINT NULL | 操作人，外键到 `sys_user` |
| `created_at` / `updated_at` | DATETIME(3) | UTC 存储 |

约束：

- `FOREIGN KEY (tenant_id) REFERENCES tenant(id)`；
- 为保证租户和店铺不可串联，建议给 `merchant_store(tenant_id,id)` 增加唯一键，并使用 `FOREIGN KEY (tenant_id,store_id) REFERENCES merchant_store(tenant_id,id)`；
- `UNIQUE (tenant_id,store_id,address_code)`；
- 默认地址唯一：使用生成列 `default_store_key = IF(is_default=1, store_id, NULL)`，建立 `UNIQUE (tenant_id,default_store_key)`；非默认行允许多个；
- `status='ACTIVE'` 且店铺 `status='ACTIVE'` 才能成为有效默认地址；
- 地址变更只影响后续新订单，订单创建时复制到 `shipment_address`，不更新历史快照。

### 3.2 店铺渠道关联表：`merchant_store_channel`

建议字段：

| 字段 | 类型/规则 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 关联主键 |
| `tenant_id` | BIGINT NOT NULL | 外键到 `tenant.id` |
| `store_id` | BIGINT NOT NULL | 与 `tenant_id` 组成店铺资源外键 |
| `channel_id` | BIGINT NOT NULL | 外键到公共 `logistics_channel.id` |
| `is_default` | TINYINT(1) | 默认渠道标记 |
| `status` | VARCHAR(32) | `ACTIVE`、`DISABLED` |
| `version` | BIGINT | 乐观锁版本 |
| `created_by` / `updated_by` | BIGINT NULL | 操作人 |
| `created_at` / `updated_at` | DATETIME(3) | UTC 存储 |

约束：

- `FOREIGN KEY (tenant_id) REFERENCES tenant(id)`；
- `FOREIGN KEY (tenant_id,store_id) REFERENCES merchant_store(tenant_id,id)`；
- `FOREIGN KEY (channel_id) REFERENCES logistics_channel(id)`；
- `UNIQUE (tenant_id,store_id,channel_id)`，防止同一店铺重复绑定同一渠道；
- 使用生成列 `default_store_key = IF(is_default=1, store_id, NULL)`，建立 `UNIQUE (tenant_id,default_store_key)`，保证每店最多一个默认渠道；
- 绑定或设为默认前校验渠道、物流商均为 `ACTIVE`，且渠道服务国家包含店铺默认地址国家；
- 平台渠道停用后不删除历史关联；关联可保留为 `DISABLED` 或显示不可用，不能继续作为报价/新订单的默认渠道。

## 4. 生命周期规则

| 场景 | 规则 |
|---|---|
| 店铺停用 | 保留地址和渠道配置；禁止新增店铺配置变更和新订单；历史订单、订单地址快照和原渠道快照继续可查 |
| 地址停用/替换 | 新订单只使用当前 ACTIVE 默认地址；历史 `shipment_address` 不回写 |
| 渠道停用 | 公共渠道停用后，店铺默认渠道变为不可用，不删除关联；报价和新订单必须拒绝不可用渠道 |
| 默认切换 | 在同一事务中完成旧默认取消、新默认设置和版本校验；唯一约束防止并发产生两个默认值 |
| 删除 | 不提供物理删除；使用 DISABLED，避免破坏订单和审计关联 |
| 审计 | 地址创建、修改、停用、默认切换；渠道绑定、解绑、默认切换均写 `audit_log`，detail 只记录字段名/脱敏摘要，不记录电话、邮箱、完整地址、Token 或 Cookie |
| 时间 | 数据库 UTC；API 返回 UTC ISO-8601；前端按 Asia/Shanghai 展示 |

## 5. API 契约变更清单（设计，不实现）

### 5.1 店铺详情扩展

`GET /api/v1/stores/{storeId}` 保留现有权限校验，成功响应增加：

- `countryRegion`：来自 ACTIVE 默认地址 `country_code`；无默认地址时为 `null`；
- `defaultShippingAddress`：仅返回脱敏摘要或结构化地址视图，不返回不必要的敏感字段；
- `defaultLogisticsChannel`：返回渠道 ID、编码、名称、状态和是否可用；
- `configurationVersion`：地址/渠道配置聚合版本，供前端刷新提示。

### 5.2 默认地址

- `GET /api/v1/stores/{storeId}/default-address`：读取当前默认地址，未配置返回明确空数据，不伪造；
- `PUT /api/v1/stores/{storeId}/default-address`：租户管理员维护，要求 `Idempotency-Key`、`X-Request-Id`、`version`；同一租户、店铺和幂等键绑定请求哈希；
- 返回 `401/403/404/409/422`，未授权店铺统一 `404`；手机号、邮箱和完整地址不写错误日志或审计 detail。

### 5.3 店铺渠道

- `GET /api/v1/stores/{storeId}/logistics-channels`：读取店铺已绑定渠道及默认标记；可同时返回公共渠道有效性；
- `PUT /api/v1/stores/{storeId}/default-logistics-channel`：租户管理员设置默认渠道，要求 `channelId`、`version`、`Idempotency-Key`；
- 复用 `GET /api/v1/logistics/channels` 作为公共可用渠道候选，不复制平台渠道数据；
- 停用渠道或不服务默认地址国家时返回业务校验错误，不写入默认配置。

### 5.4 错误码建议

| 错误码 | HTTP | 含义 |
|---|---:|---|
| `STORE-1003` | 422 | 默认地址字段或国家编码无效 |
| `STORE-1004` | 422 | 渠道不可用或不服务该国家 |
| `STORE-1005` | 422 | 店铺当前状态不允许修改配置 |
| `COMMON-1005` | 409 | 配置版本冲突 |
| `COMMON-1006` | 404 | 店铺不存在或无权访问 |
| `COMMON-1009` | 409 | 幂等键复用不同请求 |

## 6. 权限矩阵

| 角色 | 店铺配置读取 | 默认地址写入 | 默认渠道写入 | 数据范围 |
|---|---:|---:|---:|---|
| `MERCHANT_OPERATOR` | 是 | 否 | 否 | 仅 active `sys_user_store_scope` 店铺 |
| `MERCHANT_ADMIN` | 是 | 是 | 是 | 当前租户全部店铺，仍校验资源归属 |
| `FINANCE_OPERATOR` | 按现有 `store:read` 权限 | 否 | 否 | 沿用现有权限矩阵，不扩大写权限 |
| `WAREHOUSE_OPERATOR` | 按现有 `store:read` 权限 | 否 | 否 | 沿用现有权限矩阵 |
| `CUSTOMER_SERVICE_OPERATOR` | 按现有 `store:read` 权限 | 否 | 否 | 沿用现有权限矩阵 |
| `PLATFORM_ADMIN` | 不通过租户业务员接口直接访问 | 否 | 否 | 平台渠道主数据维护；是否提供受审计的跨租户支持读取需业务确认 |

所有接口继续执行 authentication、`scope:TENANT`、角色、`tenant_id`、`user_id`、`store_id`、active scope 和资源归属校验；不得恢复 tenant-only 查询。

## 7. 迁移方案（设计，不执行）

建议新增下一版本迁移，版本号以当前环境实际 Flyway 历史为准，不能假定直接使用固定编号：

1. 预检查：确认数据库为目标 ShipFlow schema，确认 `merchant_store`、`tenant`、`logistics_channel`、`sys_user`、`audit_log` 存在；检查店铺租户归属异常和渠道外键异常。
2. 为 `merchant_store` 增加 `(tenant_id,id)` 唯一键（若已存在则跳过）。
3. 创建 `merchant_store_address`，创建租户、复合店铺、创建人和更新人外键、唯一键、状态约束和默认唯一生成列。
4. 创建 `merchant_store_channel`，创建租户、复合店铺、渠道、操作人外键、唯一键、状态约束和默认唯一生成列。
5. 不回填虚构地址或渠道；已有店铺保持“未配置”，详情继续返回 null/unavailableFields。
6. 仅在业务确认回填来源后，单独制定受控回填脚本；回填必须校验每个店铺唯一默认值并产生审计记录。
7. 迁移后只读校验表、索引、外键、默认唯一约束和现有订单行数不变。

Flyway 自动迁移当前不启用；本方案不执行 V018/V019，也不创建新迁移文件。

## 8. 回滚方案

- 迁移执行前必须有 schema 备份和变更窗口；本设计阶段不创建备份、不连接数据库。
- 若仅创建空表且无业务数据，可在批准的回滚窗口删除新表，再删除新增复合唯一键。
- 若已产生配置数据，不允许直接 DROP；先停止新写入，导出并核对配置和审计，再按 DBA 批准脚本回滚。
- 回滚不得删除或更新 `shipment_address`、`shipment_order`、`quote` 历史快照。
- 应用回滚前先回退 API 路由和前端入口，保留旧详情的 `null + unavailableFields` 契约。

## 9. 测试方案

### 静态和契约测试

- Flyway SQL 静态检查：字段、唯一键、外键、CHECK、UTC 注释和无危险 DROP；
- OpenAPI 检查：新增 operationId 唯一、状态码和错误 schema 对齐；
- Mapper XML 检查：每条店铺配置查询含 `tenant_id`、`store_id` 和资源归属条件，不出现 tenant-only 放宽。

### 后端测试

- Service：默认地址创建/更新/停用、默认唯一、国家编码、停用店铺拒绝、幂等、版本冲突；
- Service：渠道绑定/默认切换、停用渠道拒绝、服务国家校验、幂等、版本冲突；
- Controller：`401/403/404/409/422`、Trace ID、敏感字段不进入响应错误或审计 detail；
- Mapper：租户隔离、复合店铺外键对应、默认唯一和排序；
- 集成测试：只在测试库变量完整且获得授权后执行，验证数据库断言和 API 断言；不连接生产库。

### 前端和浏览器测试

- 详情真实显示已配置/未配置，不把 null 当成假数据；
- 管理员可见维护入口，业务员只读；
- 加载、空数据、失败、401/403/404/409/422、重试、防重复和 Trace ID；
- Asia/Shanghai 展示，刷新/返回保持店铺详情路由；
- 真实浏览器只读验收先于任何受控写入，写入需单独授权。

## 10. 进入实现阶段的前置条件

- 业务确认默认地址是否允许多条历史配置，以及默认地址国家是否作为店铺国家/地区唯一来源；
- 业务确认店铺可绑定多个渠道还是只允许一个渠道；
- 业务确认平台管理员是否需要跨租户支持读取；
- DBA 确认新迁移版本号、复合外键兼容性、生成列/唯一索引实现方式和回滚窗口；
- 明确是否存在可脱敏回填的正式地址/渠道来源。

在以上事项确认前，P4-04 仅具备设计完成条件，不具备执行迁移和后端契约实现条件。

## 11. 业务规则确认清单（当前未确认）

以下规则必须由业务负责人逐项确认后，才能冻结 API 和数据库约束：

| 规则项 | 当前建议 | 待确认结论 |
|---|---|---|
| 默认发货地址数量 | 每个店铺最多一个 ACTIVE 默认地址；允许保留多条历史/非默认配置 | 是否允许多条历史配置，以及停用后是否必须立即指定新默认地址 |
| 默认物流渠道数量 | 每个店铺最多一个 ACTIVE 默认渠道；店铺可绑定多个渠道 | 是否允许多渠道绑定，以及是否允许无默认渠道 |
| 商家业务员写权限 | 仅可读取其 active `sys_user_store_scope` 授权店铺，不可维护地址/渠道 | 是否维持只读边界 |
| 租户管理员写权限 | 可维护本租户店铺地址和渠道配置 | 是否允许维护停用店铺的历史配置 |
| 平台管理员边界 | 仅维护公共 `logistics_channel` 主数据，不通过租户接口写入店铺配置 | 是否需要受审计的跨租户支持读取 |
| 停用店铺 | 禁止新报价/新订单及配置变更；保留历史配置和订单查询 | 是否允许只读查看停用店铺配置 |
| 停用地址 | 不得作为新订单默认地址；历史订单快照不变 | 是否允许停用当前默认地址而暂时无默认地址 |
| 停用渠道 | 不得用于新报价/新订单；保留店铺关联，不删除历史快照 | 是否自动清除默认标记，或要求人工切换 |
| 历史配置版本 | 使用版本号和审计日志；建议保留历史记录，不物理删除 | 是否需要面向用户查询完整版本历史 |

在确认完成前，不将“当前建议”视为已批准业务规则，不创建写接口、不改变前端为可配置状态。

### 11.1 业务确认表

| 编号 | 待确认规则 | 当前设计建议 | 确认状态 |
|---|---|---|---|
| B-01 | 每店默认发货地址数量 | 最多一个 ACTIVE 默认地址，可保留非默认历史配置 | 待业务确认 |
| B-02 | 每店默认物流渠道数量 | 最多一个 ACTIVE 默认渠道，建议允许绑定多个渠道 | 待业务确认 |
| B-03 | 商家业务员权限 | 仅读取 active `sys_user_store_scope` 范围，不得维护配置 | 待业务确认 |
| B-04 | 租户管理员权限 | 可维护本租户授权店铺的地址和渠道配置 | 待业务确认 |
| B-05 | 平台管理员边界 | 只维护公共物流商/渠道主数据，不通过租户接口写店铺配置 | 待业务确认 |
| B-06 | 停用店铺 | 禁止新报价、新订单和配置写入；历史订单及快照仍可查询 | 待业务确认 |
| B-07 | 停用地址 | 不得作为新订单默认地址；历史地址快照不变 | 待业务确认 |
| B-08 | 停用渠道 | 不得用于新报价、新订单；保留关联和历史快照 | 待业务确认 |
| B-09 | 历史配置版本 | 使用版本号和追加审计；不物理删除历史配置 | 待业务确认 |
| B-10 | 国家/地区来源 | 由 ACTIVE 默认地址的 ISO 国家编码提供，未配置时返回 null/unavailableFields | 待业务确认 |

业务负责人确认时应逐项给出“同意当前建议/修改为具体规则”，并明确生效范围；未确认项不得进入 API 实现。

### 11.2 DBA 确认表

| 编号 | 待确认事项 | 当前状态 |
|---|---|---|
| D-01 | 目标 Schema、当前 Flyway 历史和实际新版本号 | 待 DBA 确认 |
| D-02 | `merchant_store(tenant_id,id)` 复合唯一键及既有外键兼容性 | 待 DBA 确认 |
| D-03 | 生成列/唯一索引、CHECK、外键删除策略及 MySQL 版本兼容性 | 待 DBA 确认 |
| D-04 | 测试库 Flyway 执行权限、备份、执行窗口和回滚权限 | 待 DBA 确认 |
| D-05 | 初始化来源、脱敏范围、审计写入和重复执行保护 | 待 DBA/业务确认 |

DBA 应逐项确认“可执行/需调整”，并给出目标环境、变更窗口和回滚负责人；未完成前不允许创建或执行迁移。

## 12. 迁移前 DBA 门禁（当前未批准）

### 12.1 计划中的迁移文件（仅计划，不创建）

| 计划文件 | 范围 | 状态 |
|---|---|---|
| `V020__add_merchant_store_resource_model.sql`（版本号待 DBA 确认） | `merchant_store` 复合唯一键、`merchant_store_address`、`merchant_store_channel`、索引、外键和默认唯一约束 | 仅计划 |
| `V021__backfill_merchant_store_resources.sql`（仅在有正式来源时） | 已确认来源的地址/渠道受控回填及审计 | 不建议与结构迁移合并；当前不创建 |

实际版本号必须以目标环境 Flyway 历史和变更审批为准，不能直接假定使用 V020/V021。当前迁移目录最高为 V019，未创建上述文件。

### 12.2 DBA 必须确认

- 目标 schema、当前 Flyway 历史和迁移版本号；
- `merchant_store(tenant_id,id)` 复合唯一键是否兼容现有外键，及是否需要调整既有外键；
- MySQL 版本是否支持设计中的生成列唯一索引；若不支持，改用受控唯一键/事务实现；
- 外键删除/更新策略、索引命名和 `DATETIME(3)` UTC 存储约定；
- 是否允许空表上线、回填来源、执行窗口、备份和回滚窗口；
- 是否明确批准 Flyway 执行。未获得明确批准前，Flyway 为禁止项。

### 12.3 初始化、失败和回滚

- 默认初始化不写入任何地址、渠道或地区假数据；已有店铺保持“未配置”。
- 若业务确认存在正式来源，回填必须单独审批、脱敏、逐店校验唯一默认值并写入审计；不得从 `shipment_address` 直接推断默认地址。
- 失败时优先按迁移事务能力回滚结构变更；若已产生配置数据，停止新写入并由 DBA 依据备份和审计执行回滚，不直接 DROP 有业务数据的表。
- 任何回滚都不得删除或更新 `shipment_address`、`shipment_order`、`quote` 历史快照。

当前门禁结论：设计完成、实现阻塞。未获得业务规则、正式回填来源、DBA 结构方案和 Flyway 执行授权前，不允许进入 P4-05 迁移与后端契约实现。

## 13. V020/V021 计划内容与依赖边界（仅设计）

### 13.1 V020 结构迁移计划

计划文件名：`V020__add_merchant_store_resource_model.sql`，实际版本号须由 DBA 根据目标环境 Flyway 历史重新确认。

执行顺序：

1. 预检目标 schema、`merchant_store(tenant_id,id)` 现有唯一性、既有外键和异常数据；
2. 必要时为 `merchant_store` 增加 `(tenant_id,id)` 唯一键，以支持复合店铺外键；
3. 创建 `merchant_store_address`，包含地址字段、状态、默认标记、版本、操作人和 UTC 时间字段；
4. 创建 `merchant_store_channel`，包含店铺/公共渠道关联、状态、默认标记、版本、操作人和 UTC 时间字段；
5. 创建租户、复合店铺、公共渠道和操作人外键，以及店铺地址编码、渠道绑定和默认值唯一约束；
6. 仅执行结构和约束校验，不初始化业务配置数据。

依赖：B-01、B-02、B-06 至 B-10 业务确认；DBA 确认 MySQL 版本、复合外键、生成列唯一索引替代方案、命名、备份和测试库执行权限。V020 失败时不得继续 V021。

### 13.2 V021 初始化/回填计划

计划文件名：`V021__backfill_merchant_store_resources.sql`，仅在存在正式、可脱敏、可审计的地址/渠道来源并单独审批后考虑；当前不创建。

依赖：V020 已在测试库验证并获批准；业务确认初始化来源和覆盖范围；DBA 批准回填窗口、审计写入方式、重复执行保护和回滚边界。不得从 `shipment_address` 直接推断店铺默认地址，不得用平台渠道主数据自动猜测店铺绑定。

### 13.3 回滚边界与失败处理

| 阶段 | 失败处理 | 回滚边界 |
|---|---|---|
| V020 预检/建表失败 | 立即停止，不执行 V021；保留错误和迁移审计 | 仅允许回滚本次新增结构；不得触碰订单、报价、地址快照 |
| V020 已建空表 | 在批准窗口由 DBA 回滚新增表/索引/复合键 | 不得删除或更新既有业务表数据 |
| V020 已有配置数据 | 停止新写入，先导出核对配置和审计 | 禁止直接 DROP；必须使用批准的兼容回滚方案 |
| V021 单店/批次回填失败 | 按批次停止并标记失败，不继续扩大范围 | 仅回滚本批次新增配置，保留审计；不得回写历史订单 |
| 应用契约上线失败 | 先停止配置写接口或回退应用版本 | 保留新表和数据，待 DBA/应用联合评估，不直接删表 |

任何回滚不得删除或更新 `shipment_order`、`quote`、`shipment_address` 历史快照；Flyway 是否执行必须由 DBA/变更负责人书面批准。

## 14. P4-05 进入门槛

P4-05“迁移与后端契约实现”只有在以下条件全部满足后才允许开始：

1. 业务规则确认表 B-01 至 B-10 已逐项确认；
2. DBA 已确认目标 Schema、迁移版本、复合外键、唯一约束/索引和 UTC 字段方案；
3. 测试库 Flyway 执行权限、执行窗口和执行人已明确；
4. 初始化数据来源、脱敏范围、覆盖店铺范围和审计要求已批准；
5. V020/V021 失败处理、备份和回滚方案已批准；
6. 已明确“不执行生产库写入、不调用顺丰生产接口”的环境边界。

任一门槛缺失，状态保持 BLOCKED，不创建迁移 SQL，不实现 API/Mapper/XML，不把前端置为可配置状态。
