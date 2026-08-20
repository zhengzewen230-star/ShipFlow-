# 商家业务员工作台第三阶段 P3-02 数据契约与状态模型设计

更新时间：2026-08-17（Asia/Shanghai）
文档性质：只读分析与设计，不代表代码已经实现。
适用范围：商家业务员工作台的运营概览、订单、仓库、物流、轨迹、异常/索赔、财务和账单对账查询契约。

## 1. 目标与边界

P3-02 的目标是先把工作台各领域的字段、状态、权限、时间和错误响应统一为可实施的数据契约，作为后续后端概览接口和前端工作台的唯一依据。本子阶段不修改 Java、Vue/TypeScript、SQL、Flyway 或 OpenAPI 文件，不连接数据库，不调用顺丰接口，不执行迁移，不提交或推送 Git。

本设计保留已经通过的认证、租户/RBAC、店铺、报价、订单、仓库、物流、轨迹、异常/索赔、账单与对账能力。统一工作台只做只读聚合，不直接推进订单、面单、费用、异常、索赔或对账状态。

### 1.1 本次检查依据

已读取 P3-01 三份文档，并检查以下当前实现：

| 层次 | 当前依据 | 本次发现 |
|---|---|---|
| 工作台旧接口 | `OperationsController`、`OperationsQueryApplicationService`、`OperationsMapper.xml` | `/operations/summary` 和 `/operations/todos` 只有租户级聚合，不能表达店铺授权、统计窗口、刷新时间、最近订单和风险。 |
| 仓库概览 | `WarehouseOverviewController`、`WarehouseOverviewApplicationService`、`WarehouseOverviewMapper.xml` | 已有待入库、待复称、`LABEL_READY` 面单判断、待交接、在途和轨迹异常投影，但查询参数只有 `tenantId`，且“今日”当前使用 `UTC_DATE()`，不能满足 Asia/Shanghai 自然日。 |
| 订单 | `ShipmentOrderController`、`ShipmentOrderQueryApplicationService`、`ShipmentOrderMapper.xml` | 订单已带 `tenant_id`、`store_id`、状态、费用、计费重量和 UTC 时间；已有按调用人店铺范围查询的 Mapper 分支，但需要统一到工作台查询上下文。 |
| 店铺授权 | `StoreScopeMapper.xml`、`merchant_store`、`sys_user_store_scope` | 商家业务员按 ACTIVE 店铺授权，租户管理员等租户角色可看本租户 ACTIVE 店铺；后续每个聚合查询都必须复用同一范围规则。 |
| 物流和轨迹 | `provider_order`、`SfInternationalService`、`TrackingQueryApplicationService`、`ShipmentTrackingMapper.xml` | 物流面单使用 `LABEL_READY`；顺丰失败状态和轨迹事件可形成风险；旧轨迹查询按租户和订单，未统一到工作台范围。 |
| 异常与索赔 | `ExceptionClaimController`、`ExceptionClaimApplicationService`、`ExceptionClaimMapper.xml` | 异常响应已带 `storeId`、责任方、处理状态和索赔摘要；列表和详情已有店铺资源校验。处理记录、证据附件和索赔关联沿用现有表。 |
| 财务和账单 | `BillingController`、`BillingApplicationService`、`BillingMapper.xml` | 账单导入已使用文件内容 SHA-256 与 `Idempotency-Key` 组合；账单/对账 DTO 已有金额、币种、状态和 UTC 时间，但查询主要按租户/订单，工作台仍缺统一快照。 |
| 前端 | `frontend/src/services/*.ts`、`DashboardView.vue`、`WorkflowView.vue`、`WarehouseWorkView.vue`、`TrackingView.vue`、`FinanceView.vue` | 现有页面分别调用 operations、warehouse、tracking、billing 等接口，前端存在多个状态类型和旧字段，不应继续自行拼接统一统计。 |
| 契约 | `openapi/shipflow-api.yaml`、`frontend/docs/openapi-coverage.md` | 现有分页、错误、UTC 时间组件可复用；旧 operations 和 warehouse schema 仍是分散响应，尚无工作台快照 schema。 |

### 1.2 设计判断

当前核心领域表已经具备 `tenant_id`、订单 `store_id`、状态、金额/重量、时间以及异常处理关联字段，P3-02 不提出新增数据库表或迁移。工作台实现优先通过现有表和只读 Mapper 查询完成；只有后续证明现有查询不能同时满足范围、窗口和一致性，才另行提出最小结构并经过确认。

## 2. 业务流程与参与方

参与方及职责如下：

| 参与方 | 可见范围 | 工作台职责 | 不应由工作台替代的职责 |
|---|---|---|---|
| 商家业务员 `MERCHANT_OPERATOR` | 当前租户且仅限 `sys_user_store_scope.status=ACTIVE` 的店铺 | 查看授权店铺的订单、仓库、物流、异常和财务待办；提交费用确认申请 | 不能最终确认费用；不能绕过店铺授权查看其他店铺 |
| 租户管理员 `MERCHANT_ADMIN` | 当前租户全部 ACTIVE 店铺 | 查看全租户工作台；可作为费用确认备用审批人 | 不能跨租户查看或修改平台数据 |
| 财务人员 `FINANCE_OPERATOR` | 当前租户全部 ACTIVE 店铺，按既有权限 | 查看费用、账单和对账；最终执行费用确认 | 不因工作台聚合而获得仓库或异常写权限 |
| 仓库人员 `WAREHOUSE_OPERATOR` | 当前租户全部 ACTIVE 店铺，按既有权限 | 查看仓库待办、复称、面单和出库状态 | 不执行财务最终确认 |
| 客服/异常处理人员 | 当前租户及既有可访问店铺 | 查看异常、责任方、处理记录、证据和索赔关联 | 不因读取风险而改变异常状态 |
| 后端工作台服务 | 认证上下文中的租户和用户范围 | 在同一快照窗口生成全部聚合项 | 不信任请求体中的 `tenant_id`、`store_id` 或前端权限判断 |

统一读取流程：

```text
认证上下文
  -> 取得 user_id、tenant_id、角色/权限
  -> 解析 store scope：租户管理员为本租户 ACTIVE 店铺，业务员为 ACTIVE 授权店铺
  -> 把时间范围从 Asia/Shanghai 自然日转换为 UTC 查询窗口
  -> 在一次只读事务/快照调用中查询指标、待办、最近订单和风险
  -> 返回同一 refreshedAt、实际 UTC 窗口和每项列表跳转条件
  -> 前端按 Asia/Shanghai 展示，点击时把后端筛选条件传给列表
```

任何订单、报价、仓库、物流、轨迹、异常、索赔、账单或对账资源，都必须同时满足 `tenant_id`、资源自身 `store_id`/订单关联店铺、调用人角色和资源归属。请求中的 `storeId` 只能收窄范围，不能扩大授权范围。

## 3. 统一工作台快照契约

### 3.1 接口定位

目标新增只读接口：

```text
GET /api/v1/operations/workbench
```

建议查询参数：

| 参数 | 类型 | 规则 |
|---|---|---|
| `timeRange` | `TODAY`、`LAST_7_DAYS`、`LAST_30_DAYS`、`CUSTOM` | 默认 `TODAY`；`TODAY` 按 Asia/Shanghai 自然日计算。 |
| `from` | UTC ISO-8601 | 仅 `CUSTOM` 使用，必须带 offset 或 `Z`。 |
| `to` | UTC ISO-8601 | 仅 `CUSTOM` 使用，必须大于 `from`，最大窗口由服务端限制。 |
| `storeId` | long，可空 | 可选收窄；后端必须重新校验当前用户是否有该店铺权限。 |
| `recentLimit` | integer | 默认 10，最大 50；不是分页列表替代品。 |
| `riskLimit` | integer | 默认 10，最大 50。 |

服务端不得接受请求体或 query 中的 `tenantId` 作为权限依据。`tenant_id` 从认证上下文取得，用户身份、角色和店铺授权在后端重新加载/校验。

### 3.2 顶层响应

目标响应使用现有 `ApiResponse<T>` 外层：

```json
{
  "success": true,
  "traceId": "...",
  "message": "OK",
  "data": {
    "businessTimeZone": "Asia/Shanghai",
    "scope": { "tenantId": "1", "storeIds": ["11", "12"], "scopeType": "AUTHORIZED_STORES" },
    "timeRange": {
      "preset": "TODAY",
      "from": "2026-08-16T16:00:00Z",
      "to": "2026-08-17T15:59:59.999Z"
    },
    "refreshedAt": "2026-08-17T02:30:00Z",
    "metrics": [],
    "todos": [],
    "recentOrders": [],
    "risks": []
  }
}
```

示例只说明形状，不是静态数据。生产响应中的数组和数量必须来自数据库真实查询。

| 字段 | 类型 | 语义 |
|---|---|---|
| `businessTimeZone` | string | 固定 `Asia/Shanghai`，前端展示和“今日”解释依据。 |
| `scope.tenantId` | string/long | 服务端认证租户，不能由前端覆盖。统一目标 DTO 建议对外使用字符串 ID 以避免 JavaScript 整数精度问题。 |
| `scope.storeIds` | string[] | 本次实际参与查询的店铺集合，不返回未授权店铺。 |
| `scope.scopeType` | enum | `ALL_TENANT_STORES` 或 `AUTHORIZED_STORES`。 |
| `timeRange.from/to` | UTC ISO-8601 | 实际统计窗口，`to` 为半开区间边界时内部使用 `[from,to)`；响应可保留精确毫秒。 |
| `refreshedAt` | UTC ISO-8601 | 本次快照服务端生成时间；所有四个区域共享同一值。 |
| `metrics` | Metric[] | 八项核心指标，每项有口径、数量和列表跳转条件。 |
| `todos` | Todo[] | 六项待办，每项是可独立解释的子口径。 |
| `recentOrders` | RecentOrder[] | 同一可见范围内按最近更新时间倒序的订单投影。 |
| `risks` | Risk[] | 后端规则生成的风险提醒；没有风险返回空数组。 |

### 3.3 指标、待办和跳转字段

统一组件字段如下：

| 组件 | 必填字段 | 说明 |
|---|---|---|
| `Metric` | `key`、`label`、`count`、`window`、`refreshedAt`、`target` | `count` 为真实计数；`target` 是列表路由和后端允许的筛选条件。 |
| `Todo` | `key`、`label`、`count`、`window`、`target` | 待财务处理必须拆成费用确认、费用差异和对账子项后再合计。 |
| `Target` | `route`、`query`、`resourceType` | 前端不得自行推断状态或拼接跨域过滤；只使用后端返回的白名单路由和 query。 |
| `RecentOrder` | `orderId`、`orderNo`、`storeId`、`storeName`、`destination`、`orderStatus`、`chargeableWeight`、`estimatedFee`、`currency`、`sfTrackingNo`、`updatedAt`、`nextAction`、`target` | `updatedAt` 为 UTC；金额和重量按统一数值规则返回。 |
| `Risk` | `id`、`type`、`level`、`title`、`resourceType`、`resourceId`、`storeId`、`occurredAt`、`description`、`target` | `resourceId` 必须能被目标列表/详情再次按租户和店铺校验。 |

核心指标口径和目标列表：

| `key` | 口径 | 目标筛选 |
|---|---|---|
| `PENDING_ORDERS` | 可见店铺内尚未取消、完成且仍需要业务下一步处理的订单；排除 `DELIVERED`、`CANCELLED`，具体状态集合由后端常量统一维护。 | 订单列表 `status in (...)` |
| `PENDING_INBOUND` | 订单 `PENDING_INBOUND`。 | 仓库作业 `status=PENDING_INBOUND` |
| `PENDING_MEASUREMENT` | 已 `INBOUND` 且当前包裹没有可用最新复称记录。 | 仓库作业 `status=INBOUND` 且待复称筛选 |
| `PENDING_LABEL` | 订单 `READY_FOR_OUTBOUND` 且关联物流面单生命周期不是 `LABEL_READY`，包括没有 provider row 的订单。 | 仓库/物流作业 `labelStatus != LABEL_READY` |
| `PENDING_OUTBOUND` | 订单 `READY_FOR_OUTBOUND` 且面单状态为 `LABEL_READY`。 | 仓库作业 `status=READY_FOR_OUTBOUND&labelStatus=LABEL_READY` |
| `IN_TRANSIT` | 订单 `IN_TRANSIT`。 | 订单或轨迹列表 `status=IN_TRANSIT` |
| `TRACKING_EXCEPTION` | 可见订单的轨迹事件 `processStatus in (RETRY, REJECTED)`，以及后端已判定的轨迹异常；同一事件只计一次。 | 轨迹异常/异常列表 `processStatus in (...)` |
| `PENDING_FINANCE` | 概览聚合指标，等于待确认费用、账单导入错误、费用对账差异和待财务复核四项子项的合计。响应必须同时带 `breakdown`。 | 财务列表，按子口径分别跳转 |

`PENDING_FINANCE` 的固定子项为：

1. `PENDING_FEE_CONFIRMATION`：费用确认状态为 `PENDING_CONFIRMATION` 或 `REQUESTED`。
2. `BILL_IMPORT_ERRORS`：账单导入批次或账单明细存在真实导入错误，批次状态为 `FAILED`/`PARTIAL_SUCCESS` 或明细状态为 `ERROR`。
3. `RECONCILIATION_DIFFERENCE`：费用对账存在差异且对账状态为 `PENDING_CONFIRMATION` 或 `REJECTED`。
4. `PENDING_FINANCE_REVIEW`：异常已进入 `PENDING_FINANCE_CONFIRMATION` 或其他需要财务复核的既有财务关联记录。

“报价即将过期”和“订单长时间未处理”的平台默认阈值统一为 24 小时。P3-03 先使用平台默认值；设计上保留后续按租户或业务类型配置阈值的扩展点，当前不新增配置表或迁移。

六项待办：

| `key` | 真实来源和边界 | 目标 |
|---|---|---|
| `PENDING_FEE_CONFIRMATION` | `fee_adjustment.confirmation_status in (PENDING_CONFIRMATION, REQUESTED)`，商家业务员可申请，财务/租户管理员可最终确认。 | 订单费用确认列表 |
| `MISSING_ADDRESS` | 订单寄件或收件地址缺失、无效或被后端标记待补充。 | 订单列表 `addressStatus=MISSING` |
| `MISSING_CUSTOMS_DOCUMENT` | 物流/仓库资料要求存在但尚未上传或尚未通过校验。 | 物流资料/仓库作业列表 |
| `PENDING_WAREHOUSE` | 待入库、待复称、待面单/打单、待出库的子项合计；不得与这四项列表重复时隐藏子项。 | 仓库作业列表 |
| `PENDING_EXCEPTION_FOLLOW_UP` | 异常状态不为 `CLOSED`，且存在责任方或处理记录待跟进。 | 异常列表 |
| `PENDING_RECONCILIATION` | 对账状态 `PENDING_CONFIRMATION`。 | 对账列表 `status=PENDING_CONFIRMATION` |

## 4. 统一状态模型

### 4.1 订单状态

订单主状态来自 `shipment_order.current_status`，不能由前端另造状态。现有集合为：

```text
DRAFT
  -> PENDING_INBOUND
  -> INBOUND
  -> PENDING_PRICE_CONFIRMATION（发生费用调整且等待最终确认时）
  -> READY_FOR_OUTBOUND（费用确认成功，或无须费用确认且流程完成）
  -> OUTBOUND
  -> IN_TRANSIT
  -> DELIVERED

DRAFT/PENDING_INBOUND -> CANCELLED
IN_TRANSIT/异常业务结果 -> RETURNED 或 LOST（按现有业务动作）
```

费用规则必须固定：商家业务员只能提交费用确认申请；财务人员最终确认，租户管理员为备用审批人；最终确认成功后订单必须进入 `READY_FOR_OUTBOUND`。P3-02 不改变 `V018` 已定义的费用确认数据结构。

### 4.2 仓库状态

仓库状态是面向作业的投影，不新增第二个订单状态字段。建议统一使用以下展示/筛选值，并在 DTO 中同时保留主订单状态和作业状态：

| 仓库状态 | 判定来源 | 可执行下一步 |
|---|---|---|
| `PENDING_INBOUND` | 主订单 `PENDING_INBOUND` | 确认入库 |
| `PENDING_MEASUREMENT` | 主订单 `INBOUND` 且无最新复称 | 提交复称 |
| `PENDING_LABEL` | 主订单 `READY_FOR_OUTBOUND` 且 provider 面单不是 `LABEL_READY` | 创建/打印面单 |
| `PENDING_OUTBOUND` | 主订单 `READY_FOR_OUTBOUND` 且 provider 面单 `LABEL_READY` | 交接出库 |
| `OUTBOUND` | 已存在仓库出库记录或主订单已出库 | 查看轨迹 |
| `IN_TRANSIT` | 主订单 `IN_TRANSIT` | 查看轨迹 |
| `COMPLETED` | 主订单 `DELIVERED` | 查看详情 |

旧前端出现的 `PENDING_HANDOVER`、`PENDING_LABEL` 等文字必须在统一服务中收敛到上述状态；`PENDING_HANDOVER` 只作为兼容映射，不作为新增数据库状态。

### 4.3 物流状态

物流商 provider order 的生命周期与订单主状态分离：

```text
PROCESSING -> CREATED -> LABEL_READY ->（交接后由订单/轨迹继续推进）
PROCESSING -> FAILED
CREATED/LABEL_READY -> CANCELLED
```

`LABEL_READY` 是唯一的待打单/贴标完成状态。`PRINT_ORDER` 成功后写入 `LABEL_READY`；`PENDING_LABEL` 是工作台派生筛选值，不是 provider lifecycle 值。物流失败至少映射为 `FAILED` 风险，返回 provider order 的错误摘要和资源 ID，不能伪造成功。

### 4.4 轨迹状态

轨迹回调处理状态来自 `tracking_event.process_status`：

| 状态 | 含义 | 工作台处理 |
|---|---|---|
| `PENDING` | 已接收，尚未处理 | 不计异常，除非超过后端配置阈值 |
| `PROCESSED` | 已成功处理 | 可用于最近轨迹 |
| `RETRY` | 处理失败，等待重试 | 计入轨迹异常 |
| `REJECTED` | 被拒绝或不可处理 | 计入轨迹异常并生成风险 |

物流业务节点如 `SF_PICKED_UP`、`CUSTOMS_EXPORT_CLEARED`、`AIR_IN_TRANSIT`、`DELIVERED` 是事件码，不与处理状态混用。事件时间和接收时间均为 UTC。

### 4.5 异常和索赔状态

异常单沿用 `exception_case.status`：

```text
OPEN -> PROCESSING -> WAITING_PROVIDER_FEEDBACK -> RESOLVED -> CLOSED
PROCESSING/RESOLVED -> PENDING_FINANCE_CONFIRMATION（按现有规则）
```

异常响应必须保留 `storeId`、责任方 `responsibleParty`、指派人、处理记录数量/摘要、证据附件元数据和索赔摘要。处理记录来自 `exception_handling_record`，附件来自 `exception_evidence_attachment`，索赔关联来自 `claim_record`；不因工作台聚合而把这些信息压缩成只有一个异常数量。

索赔状态沿用：

```text
OPEN -> SUBMITTED -> APPROVED 或 REJECTED -> CLOSED
```

索赔金额使用金额契约，索赔状态不能反写为订单状态。

### 4.6 财务状态

| 领域 | 状态 | 说明 |
|---|---|---|
| 费用确认 | `PENDING_CONFIRMATION`、`REQUESTED`、`CONFIRMED` | 业务员只能触发 `REQUESTED`；财务或租户管理员执行最终 `CONFIRMED`；确认成功订单为 `READY_FOR_OUTBOUND`。 |
| 账单批次 | `PROCESSING`、`PARTIAL_SUCCESS`、`SUCCESS`、`FAILED` | 文件重复使用内容 SHA-256；请求重复还必须匹配 `Idempotency-Key` 的请求摘要。 |
| 账单明细 | `IMPORTED`、`MATCHED`、`ERROR` | 明细错误不能被概览隐藏，应形成账单风险或待办。 |
| 对账 | `AUTO_CLOSED`、`PENDING_CONFIRMATION`、`CONFIRMED`、`REJECTED` | `PENDING_CONFIRMATION` 进入待对账确认；金额差异保留系统金额、账单金额和差异金额。 |

## 5. 字段对照表

下表区分数据库字段、当前后端 DTO、当前前端字段和统一目标字段。目标字段未实现前不得在文档外宣称已经存在。

| 业务对象 | 数据库/现有字段 | 当前后端/前端表现 | P3-02 统一目标 | 处理策略 |
|---|---|---|---|---|
| 租户 | `tenant_id` | 多数 DTO 不对外返回；部分仓库投影返回 `tenantId` | 由认证上下文确定；快照只返回脱敏 scope 信息 | 后端强制注入，前端不可提交覆盖 |
| 店铺 | `shipment_order.store_id`、`quote.store_id`、`merchant_store.id` | 订单查询支持 `storeId`；旧 operations/warehouse overview 不带 | 所有工作台 item 带 `storeId`，必要时带 `storeName`；每个查询按授权范围 | 复用现有表和 `StoreScopeMapper` |
| 订单号 | `shipment_order.order_no` | `ShipmentOrderResponse.orderNo`、仓库 `businessOrderNo` | 统一 `orderNo` | DTO 映射层收敛，不改列名 |
| 订单状态 | `current_status` | 前端同时有 `status`、`currentStatus` | `orderStatus`，枚举为订单主状态集合 | 新快照使用明确名称，旧接口兼容 |
| 仓库状态 | 由订单、复称、provider order、出库记录派生 | `WarehouseWorkItem.warehouseStatus`、`logisticsStatus`，旧概览直接返回 status | `warehouseStatus` 与 `orderStatus` 分开，采用第 4.2 节集合 | 统一派生函数，不能写新状态表 |
| 面单状态 | `provider_order.lifecycle_status` | 前端部分使用 `logisticsStatus` | `labelStatus`，唯一完成值 `LABEL_READY` | 保留 provider 原值，工作台提供派生 `PENDING_LABEL` |
| 顺丰单号 | `provider_order.tracking_no`、`warehouse_outbound_record.tracking_no` | `trackingNo`、`sfTrackingNo` 并存 | `sfTrackingNo`，无值为 null，不使用空字符串冒充 | 以 provider 优先并按租户/订单关联 |
| 物流事件 | `tracking_event.event_code/event_time/process_status` 等 | `TrackingEventResponse`、`ShipmentTrackingEventResponse` 字段不完全一致 | `eventCode`、`eventTime`、`receivedAt`、`processStatus`、`description` | 事件码与处理状态分开 |
| 异常 | `exception_case.*` | `ExceptionCaseResponse` 已含 `storeId`、`responsibleParty`、claim | `exceptionStatus`、`responsibleParty`、`handlingRecords`/`evidence`/`claim` 摘要 | 复用现有关联表，不在快照重复返回大文件内容 |
| 费用 | `estimated_fee/current_fee/confirmed_fee`、`fee_adjustment.*` | DTO 使用 `estimatedFee/currentFee/confirmedFee` | `estimatedFee`、`currentFee`、`differenceAmount`、`currency`、`confirmationStatus` | 金额统一 scale 2；不使用浮点计算 |
| 账单 | `bill_import_batch.file_hash`、`bill_detail.*` | `fileHash`、`billedAmount`、批次/明细状态 | `billStatus`、`fileContentSha256`、`billedAmount`、`currency` | 文件名仅展示，不能作为重复判断 |
| 对账 | `reconciliation_record.*` | `systemAmount`、`billedAmount`、`differenceAmount`、`reconciliationStatus` | 保持字段，并增加 `target` 和店铺归属链路 | 通过订单关联校验 store |
| 时间 | DATETIME(3)，schema 注释为 UTC | Java `LocalDateTime` 转 `OffsetDateTime(UTC)`；前端 `display.ts` 用 Asia/Shanghai | API 所有时间为 UTC ISO-8601；前端统一转换 Asia/Shanghai | 禁止裸本地时间和服务器默认时区 |
| ID | BIGINT | 前端 `Id=string`，部分 OpenAPI 仍 long | 对外统一 string ID；内部 Java 保持 Long | 新快照按 string 序列化，列表逐步迁移 |

## 6. 分页、排序、筛选、金额、重量和时间

### 6.1 分页

所有列表接口统一：

```json
{
  "page": 1,
  "pageSize": 20,
  "total": 0,
  "totalPages": 0,
  "items": []
}
```

规则：`page >= 1`，`1 <= pageSize <= 100`，空结果仍返回 `items=[]` 和真实的 `total=0`。`totalPages` 使用向上取整，total 为 0 时为 0。概览的 `recentLimit`/`riskLimit` 只是有上限的投影数量，不替代可分页列表。

### 6.2 排序与筛选

目标列表统一接受：

| 参数 | 规则 |
|---|---|
| `sortBy` | 只允许后端白名单，例如 `updatedAt`、`createdAt`、`reportedAt`、`occurredAt`；禁止把任意列名直接拼入 SQL。 |
| `sortDirection` | `ASC` 或 `DESC`，默认 `DESC`。 |
| `storeId` | 后端校验后收窄；不得通过空值、重复参数或别的 tenantId 扩权。 |
| `status` | 只接受对应领域枚举；未知值返回 `COMMON-1001`。 |
| `from/to` | UTC ISO-8601，服务端校验范围和顺序。 |

稳定排序必须追加主键，例如 `updated_at DESC, id DESC`，保证分页和概览跳转可重复。

### 6.3 金额

内部继续使用 Java `BigDecimal` 和数据库 `DECIMAL(18,2)`。统一目标 API 金额字段为十进制定点字符串，正则为 `^-?\\d+(\\.\\d{1,2})?$`，并始终另带 `currency` 三位大写币种；零值为 `"0.00"`，没有金额为 null。这样避免浏览器 Number 的浮点误差。现有接口暂时保留兼容的 BigDecimal JSON 表现，只有工作台新契约和后续版本化列表使用统一字符串格式。

### 6.4 重量和尺寸

重量统一 kg，长度/宽度/高度统一 cm；重量保留最多三位小数，尺寸保留最多三位小数。工作台目标使用十进制定点字符串，避免不同页面把克和千克混用；兼容现有订单投影的特殊展示规则：订单投影原始 `5400` 按既有约定显示为 `5.40 kg`，报价字段已经是 kg，不再次除以 1000。重量字段必须明确 `unit` 或由字段名固定单位，不允许裸数字跨域复用。

### 6.5 时间

数据库继续 UTC 存储，Java 查询和写入使用 UTC；接口时间统一 `YYYY-MM-DDTHH:mm:ss.SSSZ` 或带明确 UTC offset 的 ISO-8601。前端 `Asia/Shanghai` 展示；不能把浏览器本地时区当作业务时区。

业务“今日”窗口按：

```text
Asia/Shanghai 当日 00:00:00（含）
  -> 转 UTC 作为 from
次日 00:00:00（不含）
  -> 转 UTC 作为 to
```

例如北京时间 2026-08-17 的窗口为 `2026-08-16T16:00:00Z <= eventTime < 2026-08-17T16:00:00Z`。所有响应必须回显实际 `from`、`to` 和 `businessTimeZone`，不得使用数据库 `UTC_DATE()` 代替上海自然日。

## 7. 权限矩阵与资源归属

### 7.1 工作台读取矩阵

| 角色 | 工作台 | 订单/报价 | 仓库 | 轨迹 | 异常/索赔 | 财务/账单 | 店铺范围 |
|---|---|---|---|---|---|---|---|
| `MERCHANT_OPERATOR` | 允许，需 `operations:read` | 读/业务流程按既有权限 | 仅既有授权能力 | 读授权订单 | 读授权订单 | 只读可见财务待办，费用最终确认禁止 | `sys_user_store_scope` ACTIVE |
| `MERCHANT_ADMIN` | 允许，需相应权限 | 租户范围 | 租户范围 | 租户范围 | 租户范围 | 可作为费用确认备用审批人 | 当前租户全部 ACTIVE 店铺 |
| `FINANCE_OPERATOR` | 允许，需财务/operations 读权限 | 财务相关订单 | 只读所需关联 | 只读所需关联 | 只读财务关联异常 | 最终费用确认、对账确认按既有写权限 | 当前租户全部 ACTIVE 店铺 |
| `WAREHOUSE_OPERATOR` | 允许，需 operations/warehouse 读权限 | 只读仓库关联订单 | 仓库作业 | 读仓库关联轨迹 | 依既有权限 | 不得最终费用确认 | 当前租户全部 ACTIVE 店铺 |
| 平台角色 | 不显示租户工作台 | 不可使用租户数据接口 | 不可使用 | 不可使用 | 不可使用 | 不可使用 | 无租户店铺范围 |

角色代码只是身份分类，实际授权仍必须检查 permission code 和资源归属。前端路由隐藏不能代替后端 401/403/404 校验。

### 7.2 后端校验顺序

1. 从 JWT/当前认证上下文取得 `user_id`、`tenant_id`，缺失或租户不匹配返回既有认证/权限错误。
2. 根据当前用户和角色确定候选店铺集合；商家业务员只从 `sys_user_store_scope` 读取 ACTIVE 授权，租户管理员等全租户角色只读取当前租户 ACTIVE 店铺。
3. 对请求 `storeId` 做集合包含校验；不在集合内不得通过 SQL 结果为空来掩盖权限问题，详情资源沿用既有 404 资源不可见语义。
4. 所有 Mapper 的订单、报价、物流、轨迹、异常、账单和对账 JOIN 必须同时约束 tenant；从订单关联店铺时必须校验关联链路的 tenant 一致性。
5. 写接口仍使用各自幂等、事务、乐观锁和状态机；工作台 GET 不产生状态变化，也不得使用前端传入的角色或金额作为权限依据。

当前缺口记录：旧 operations、warehouse overview、warehouse work、tracking query 和 billing query 仍主要按 tenant 查询，下一实施阶段必须补齐 user/store scope 参数或统一可见资源子查询；不能直接复用其租户级 SQL 作为商家业务员工作台实现。

## 8. 错误响应与前端状态

### 8.1 统一错误

继续使用现有错误外层，不在工作台另造格式：

```json
{
  "success": false,
  "traceId": "...",
  "error": {
    "code": "COMMON-1006",
    "message": "订单或工作台资源不存在或当前账号无权访问",
    "details": {}
  }
}
```

| HTTP | 语义 | 前端行为 |
|---|---|---|
| 401 | 未登录/认证失效 | 按现有 auth service 恢复或跳转登录 |
| 403 | 有登录身份但无模块权限 | 显示权限不足；隐藏不可用跳转，不显示假数据 |
| 404 | 资源不属于当前租户/店铺或不存在 | 显示资源不可见，不泄露跨租户存在性 |
| 400 | 分页、时间、筛选或参数格式错误 | 显示后端 message 和修正入口 |
| 409 | 幂等、并发或状态冲突 | 保留 traceId，提示刷新/重试，不重复写入 |
| 422 | 业务规则不允许 | 显示具体规则错误，例如费用角色不允许最终确认 |
| 500 | 未预期故障 | 显示失败时间和重试按钮，不回退静态数据 |

响应头 `X-Trace-Id` 与 body `traceId` 必须一致。工作台失败不能用旧快照悄悄冒充当前窗口；可以保留旧的刷新时间用于界面说明，但数字必须标记为上一份成功快照或清空。

### 8.2 前端状态契约

工作台各区域共享一次请求的 `loading`、`success`、`empty`、`forbidden`、`error` 状态。时间窗口或店铺切换时丢弃旧请求结果，防止较慢的旧响应覆盖新范围。刷新成功后同一响应同时更新指标、待办、最近订单、风险和 `refreshedAt`。

## 9. 前后端差异与迁移策略

| 差异 | 当前状态 | 目标 | 后续处理 |
|---|---|---|---|
| 多接口聚合 | Dashboard 同时调用 operations summary/todos 和 warehouse overview | 一个工作台快照返回四个区域 | P3-02 后端实现统一查询；P3-03 前端只调用该接口 |
| 租户级 SQL | operations/warehouse/tracking/billing 部分查询只有 tenant 条件 | 所有查询使用同一可见店铺集合 | 复用 `StoreScopeMapper` 语义，新增只读 scope 条件 |
| 状态命名 | 前端有 `status`、`currentStatus`、`warehouseStatus`、`logisticsStatus` 和 `PENDING_HANDOVER` | `orderStatus`、`warehouseStatus`、`labelStatus`、`processStatus` 分层 | 新快照先统一，旧页面逐步适配，不修改数据库枚举 |
| ID 类型 | Java/OpenAPI 常用 long，前端 `Id=string` | JSON 对外 string ID | 新契约统一 string，旧接口兼容到版本迁移 |
| 金额类型 | Java BigDecimal，前端部分定义 number | 定点字符串 + currency | 新快照采用字符串，旧 API 不在本子阶段破坏 |
| 重量单位 | 订单投影存在特殊原始单位转换，报价已是 kg | 字段/单位显式且只转换一次 | 保留 `formatChargeableWeight` 的订单规则并为快照补 unit 约定 |
| 时间 | 后端 UTC offset 输出，仓库今日 SQL 使用 `UTC_DATE()` | UTC 存储/API，Asia/Shanghai 业务窗口和展示 | 后端统一计算 UTC `[from,to)`，不使用服务器默认时区 |
| 跳转 | 现有按钮多为固定路由，无统一过滤条件 | 后端返回白名单 `Target` | P3-03 只消费 target；P3-04 实现列表 query 回显 |
| 错误处理 | `ApiErrorResponse` 已统一，但前端区域处理不一致 | 统一区域状态、重试和权限提示 | 复用 `ApiError`/`DataState`，补 service contract 测试 |

## 10. 实施顺序

### P3-02 后端实现前置顺序

1. 固化 `WorkbenchQuery`：认证用户、tenant、授权店铺集合、时间窗口、分页/limit 和排序白名单。
2. 固化状态常量与派生规则：订单主状态、仓库状态、`LABEL_READY` 面单状态、轨迹处理状态、异常/索赔状态、费用/账单/对账状态。
3. 设计统一后端 domain/DTO：scope、timeRange、metric、todo、recent order、risk 和 target；金额/重量/时间按本文件处理。
4. 以现有表建立只读 Mapper 查询，所有查询带 tenant 和 store scope；先实现指标和待办，再实现最近订单和风险。
5. 在同一只读服务调用中固定 `refreshedAt` 和窗口，保证四个区域使用同一 scope/窗口；不让前端相加多个接口。
6. 补后端 service、mapper XML、controller WebMvc 测试；测试同时断言接口字段和 Mapper 参数/SQL 具备 tenant/store 条件，避免连接数据库。
7. 再更新 OpenAPI 新 path/schema，并运行 operationId 唯一性、引用完整性和字段对照检查。

### P3-03/P3-04 前端实施顺序

1. `operations` service 增加唯一快照类型和调用方法，保留旧 service 直到页面迁移完成。
2. Dashboard 改为单次真实 API 请求，按快照渲染八项指标、六项待办、最近订单和风险；补 loading/empty/error/forbidden/retry。
3. 指标和待办只使用后端 `target` 跳转；不在前端重新计算、合并状态或生成风险。
4. 订单、仓库、轨迹、异常、账单列表读取 query 参数并回显状态、店铺和时间筛选，形成跳转闭环。
5. 统一 `Asia/Shanghai` 展示、刷新时间和时间范围切换；用单元测试验证旧响应不会覆盖新窗口。

## 11. OpenAPI 设计要求

P3-02 不修改 `openapi/shipflow-api.yaml`，但 P3-02 后端实现时必须新增并检查：

| 契约项 | 要求 |
|---|---|
| path | `/operations/workbench`，GET，只读，租户范围；不要删除旧 summary/todos，迁移完成前保持兼容。 |
| operationId | `getOperationsWorkbench`，全文件唯一。 |
| parameters | `timeRange`、`from`、`to`、`storeId`、`recentLimit`、`riskLimit`，时间参数使用 `UtcDateTime`。 |
| schema | `OperationsWorkbench`、`WorkbenchMetric`、`WorkbenchTodo`、`WorkbenchRecentOrder`、`WorkbenchRisk`、`WorkbenchTarget`、`WorkbenchScope`。 |
| response | 成功为现有 `ApiSuccess` 外层；401/403/400/404/409/422/500 按现有 response 组件。 |
| description | 明确数据库 UTC、业务今日 Asia/Shanghai、ID/金额/重量格式、tenant/store/角色校验和列表跳转一致性。 |

## 12. 验收清单与当前结论

本次只读分析阶段的验收边界：

- [x] 已读取 P3-01 任务清单、基线和学习笔记。
- [x] 已检查现有后端 DTO、Controller、Service、Mapper/XML、数据库字段、前端 service/页面和 OpenAPI。
- [x] 已建立订单、仓库、物流、轨迹、异常/索赔、财务状态模型。
- [x] 已建立字段对照表、分页/排序/筛选、金额/重量/时间和错误响应规则。
- [x] 已明确 `tenant_id`、`store_id`、角色、资源归属、UTC 存储和 Asia/Shanghai 展示。
- [x] 已明确现状缺口和实施顺序，未把设计内容写成已实现功能。
- [x] 未修改业务代码、数据库、Flyway 或 OpenAPI。
- [x] 未连接数据库、执行迁移、调用顺丰接口、提交或推送 Git。

本文件完成了“分析与文档设计”授权范围，可以作为 P3-03 前端工作台设计输入；但按 P3-01 任务清单，P3-02 的后端 DTO/Controller/Service/Mapper、测试和 OpenAPI 更新仍未实施。因此不能把完整 P3-02 标记为已完成，也不应直接开始依赖该接口的前端编码。建议先批准第 10 节的后端实施顺序，再进入 P3-02 实现子阶段；后端契约和测试通过后，才正式进入 P3-03。
