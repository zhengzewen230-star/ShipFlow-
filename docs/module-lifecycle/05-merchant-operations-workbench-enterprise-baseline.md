# 商家业务员工作台企业化现状基线

## 1. 基线范围与结论

- 盘点日期：2026-08-17。
- 盘点时区：本文记录使用 `Asia/Shanghai`；后端数据库时间契约以 UTC 为准。
- 盘点范围：`frontend/src`、`backend/src/main/java`、`backend/src/main/resources/mapper`、`database/schema.sql`、`database/init_data.sql`、`database/migrations/`、`openapi/shipflow-api.yaml`、前后端测试及已有模块文档。
- 盘点方式：只读源码、SQL、契约和测试扫描；未连接生产数据库，未执行迁移，未调用顺丰生产接口。
- 工作区状态：盘点开始时已有用户未提交修改和本地构建产物；本阶段只新增本基线文档，不覆盖或清理既有修改。

当前结论：商家工作台已经具备认证、租户范围、报价创建、报价转订单、仓库作业、顺丰沙箱操作、轨迹查询、异常/索赔后端服务、账单对账后端能力和部分 Vue 入口，但前端仍有多个通用演示式页面。当前“有路由/有 service/有 Controller”不等于“业务闭环已交付”。下一阶段必须先统一状态和数据契约，再按模块补齐页面操作。

## 2. 业务流程、参与方和边界

### 2.1 当前业务闭环

```text
商家业务员/租户管理员
    └─选择店铺与渠道 → 创建正式报价 → 校验有效期 → 从报价创建草稿订单
                                  ↓
                           补充地址与货物明细
                                  ↓
                           提交订单 DRAFT → PENDING_INBOUND
                                  ↓
仓库操作员：确认入库 → 复称/测方 → 费用可能调整 → 准备出库
                                  ↓
平台/顺丰能力：创建顺丰订单 → 获取面单 → 上传清关资料 → 交接出库
                                  ↓
物流商回调/沙箱模拟：轨迹事件入库、幂等处理、状态查询
                                  ↓
商家业务员/客服：轨迹异常 → 创建异常 → 分派 → 处理 → 索赔
                                  ↓
财务人员：账单导入 → 订单匹配 → 零差异自动关闭/非零差异人工确认
                                  ↓
所有关键写操作进入 audit_log，查询始终按 tenant_id 隔离
```

### 2.2 参与方和职责

| 参与方 | 当前职责 | 当前 scope/权限边界 | 基线判断 |
|---|---|---|---|
| 平台管理员 | 租户、入驻、访客线索、公共物流商/渠道/价格规则、平台审计 | `PLATFORM`；`tenant:*`、`logistics:*`、`price-rule:manage`、`audit:read` | 能力集中在平台页面，不应进入租户订单数据 |
| 商家业务员 | 查看渠道、报价、报价转订单、提交/取消订单、查看轨迹、异常和索赔 | `TENANT`；主要是 `quote:*`、`order:*`、`tracking:read`、`exception:*`、`billing:read`、`operations:read` | 目标角色；当前菜单可见，但操作入口不完整 |
| 租户管理员 | 商家业务员能力外，维护店铺、用户、角色和租户内审计 | `TENANT`；由实时 permission 判定 | 可管理入口存在，店铺/用户/RBAC 按钮仍不完整 |
| 仓库操作员 | 入库、复称、出库、顺丰沙箱操作 | `TENANT`；`warehouse:manage`，并通常有 `order:read`、`tracking:read` | 仓库工作台已接真实查询和部分写操作 |
| 财务人员 | 账单导入、账单/明细查询、对账确认 | `TENANT`；`billing:read`、`finance:bill-import`、`finance:reconcile` | 财务页面已接查询和确认，但导入 UI 缺失 |
| 客服/异常专员 | 轨迹查看、异常和索赔处理 | `TENANT`；`tracking:read`、`exception:read/manage` | 后端写能力已有，前端处理台缺失 |
| 物流商/顺丰沙箱 | 通过回调或受控 client 提供外部事件/操作结果 | 非浏览器租户页面；回调使用独立签名边界 | 不应伪造成前端静态成功数据 |
| 系统/审计 | 认证、CSRF、租户授权、幂等、乐观锁和审计 | JWT `tenant_id`、`scope:*`、permission、SQL tenant 条件 | 核心边界已存在，但 traceId 展示和资源细粒度范围需继续核对 |

### 2.3 当前状态边界

| 领域 | 当前后端/数据库状态 | 当前前端表现 | 基线风险 |
|---|---|---|---|
| 报价 | `VALID`、`EXPIRED`、`CANCELLED`；服务端按 `valid_to` 计算有效性 | `WorkflowView` 显示文字状态，详情可校验报价 | 报价状态、有效期和“可转订单”尚未形成统一状态徽标与操作模型 |
| 订单 | `DRAFT` → `PENDING_INBOUND` → `INBOUND` → `PENDING_PRICE_CONFIRMATION`/`READY_FOR_OUTBOUND` → `OUTBOUND` → `IN_TRANSIT` → `DELIVERED`；另有 `CANCELLED`、`RETURNED`、`LOST` | 列表和仓库页同时展示订单/仓库投影状态 | 订单状态、仓库处理状态、物流商生命周期状态在通用页面中容易混用 |
| 仓库 | 入库、复称、费用调整、出库由 `warehouse_measurement`、`fee_adjustment`、`warehouse_outbound_record` 支撑 | `WarehouseWorkView` 已有真实列表和入库/复称/出库按钮；第二阶段已补齐费用确认申请、查询和最终确认 | 待贴标/打单页签仍被禁用；`LABEL_READY` 面单闭环仍待后续模块 |
| 顺丰物流 | `provider_order.lifecycle_status`、`tracking_event.process_status` 和沙箱操作结果独立存在 | 页面显示部分顺丰操作结果和轨迹来源 | 供应商状态、订单状态、轨迹处理状态尚未统一转换为业务员可理解的标准契约 |
| 轨迹 | 事件按 `provider_id + tracking_no + event_id` 去重；查询有订单 ID、订单号/运单号两套路径 | 全链路页展示事件时间线，订单详情可看分页事件 | 无时间范围/物流状态筛选、自动刷新和异常跳转；供应商失败信息未形成统一 UI |
| 异常/索赔 | 异常 `OPEN`、`PROCESSING`、`RESOLVED`、`CLOSED`；索赔 `OPEN`、`SUBMITTED`、`APPROVED`、`REJECTED`、`CLOSED` | 目前主要是异常列表/详情读取 | 需求中的“待物流商反馈”“待财务确认”等业务状态未建模；处理时间线、证据和索赔动作缺失 |
| 财务 | 账单批次 `PROCESSING`、`PARTIAL_SUCCESS`、`SUCCESS`、`FAILED`；明细 `IMPORTED`、`MATCHED`、`ERROR`；对账 `AUTO_CLOSED`、`PENDING_CONFIRMATION`、`CONFIRMED`、`REJECTED` | `FinanceView` 有列表、详情、错误行和对账确认 | 账单导入按钮、分页交互、费用调整查询仍缺；财务角色与审计页签需继续统一 |

## 3. 代码和契约盘点总表

| 对象 | 当前数量/位置 | 说明 |
|---|---:|---|
| Vue 页面 | 17 个 `frontend/src/views/*.vue` | 8 个核心业务域由 `DashboardView`、`DirectoryView`、`LogisticsView`、`QuoteView`、`WorkflowView`、`FinanceView`、`TrackingView`、`WarehouseWorkView` 承载；另有平台、认证和公开页 |
| 控制台菜单 | 16 项 | 见 `frontend/src/navigation/console.ts`；包含审计、用户、RBAC 等支持页面 |
| 后端 Controller | 24 个 | 含认证、平台、核心业务、顺丰回调和顺丰操作 Controller |
| 后端映射 | 约 94 个 `@*Mapping` | 与 OpenAPI path/operationId 需要持续做契约校验 |
| 后端 Service/ApplicationService | 31 个 | 核心域大多已分层，部分实现文件压缩为单行，维护和审查成本较高 |
| Java Mapper | 37 个 | XML 位于 `backend/src/main/resources/mapper` |
| OpenAPI operationId | 第一阶段盘点时文件声明 93 个 | 当时包含一个后端未发现对应 Controller 的费用确认契约；第二阶段已补齐，当前结果见实施文档第 8 节 |
| 数据库业务表 | `schema.sql` 基础表及 V007/V013/V017 等迁移扩展 | 租户业务表普遍带 `tenant_id`；公共物流主数据不带租户字段 |
| 前端业务测试 | 11 个 `*.spec.ts` | 已覆盖基础 HTTP、导航、提交锁、展示、账单、轨迹、仓库规则/服务，但核心页面仍缺视图级测试 |
| 后端测试 | 按域有 Controller WebMvc、Service、Mapper XML 和若干状态机/沙箱测试 | 未将本次基线文档视为测试通过证明；本轮验证结果见第 10 节 |

## 4. 八个核心页面映射

### 4.1 运营概览

| 项目 | 当前实现 |
|---|---|
| 页面路由 | `/app`，`frontend/src/views/DashboardView.vue` |
| 当前 API | 租户且有权限时并行调用 `GET /api/v1/operations/summary`、`GET /api/v1/operations/todos`；有 `warehouse:manage` 时另调 `GET /api/v1/warehouse/overview`，仓库概览当前每 25 秒轮询一次 |
| 请求参数 | 以上接口无查询参数；租户来自 JWT `tenant_id`，前端不传租户 ID |
| 响应字段 | `summary`：`draft`、`pendingInbound`、`inbound`、`pendingPriceConfirmation`、`readyForOutbound`、`outbound`、`inTransit`、`delivered`、`exception`、`cancelled`；`todos`：`pendingPriceConfirmation`、`pendingReconciliation`、`activeExceptions`、`submittedClaims`；仓库概览另有 8 个计数、最近订单和最近轨迹异常 |
| 数据库来源 | `shipment_order`、`reconciliation_record`、`exception_case`、`claim_record`；仓库概览还读取 `provider_order`、`tracking_event`、`audit_log`、`warehouse_outbound_record` |
| 当前角色权限 | `scope:TENANT + operations:read` 查看运营摘要；`scope:TENANT + warehouse:manage` 查看仓库指标；平台账号显示平台入口而不读租户运营数据 |
| 已完成操作 | 读取真实统计、加载/错误状态、仓库概览自动轮询、最近订单/轨迹异常列表、跳转到仓库/轨迹页面、新建报价入口按权限显示 |
| 缺失操作 | 8 个业务员核心指标未完整落地；缺少统计时间范围、刷新时间、指标到具体筛选列表的跳转、“我的待办”完整分类、风险提醒、手动刷新和错误重试；指标卡与列表没有同一查询条件/快照证明 |
| 当前错误和占位 | `warehouseStages` 是前端固定流程标签；`warehouseDate` 使用浏览器本地 `toLocaleString`，未显式指定 `Asia/Shanghai`；摘要错误只有错误文本，没有重试按钮或 traceId 展示 |
| 对应测试文件 | 前端：`frontend/src/views/dashboardLayout.spec.ts`；后端：`OperationsControllerWebMvcTest`、`OperationsQueryApplicationServiceTest`、`OperationsMapperXmlTest`、`WarehouseOverview*Test` |

### 4.2 店铺管理

| 项目 | 当前实现 |
|---|---|
| 页面路由 | `/app/stores`，由通用 `frontend/src/views/DirectoryView.vue` 以 `domain='stores'` 渲染 |
| 当前 API | 当前页面实际调用 `GET /api/v1/stores`；typed service 另有 `GET /stores/{id}`、`POST /stores`、`PUT /stores/{id}`、`POST /stores/{id}/status` |
| 请求参数 | 列表支持 `status`、`platformCode`、`page`、`pageSize`；创建为 `storeCode`、`storeName`、`platformCode`、`platformAccount`；更新需要 `version`；状态变更需要 `status`、`version` |
| 响应字段 | `Store`：`id`、`tenantId`、`storeCode`、`storeName`、`platformCode`、`platformAccount`、`status`、`version`、`createdAt`、`updatedAt`；列表为分页 `items` |
| 数据库来源 | `merchant_store`，查询含 `tenant_id`、`deleted=0`；当前 schema 没有店铺默认发货地址或默认物流渠道字段/关联表 |
| 当前角色权限 | `scope:TENANT + store:read` 读；创建/编辑/启停需要 `store:manage`；V011 的商家业务员只有 `store:read` |
| 已完成操作 | 列表读取；租户管理员在有 `store:manage` 时可打开创建表单；后端创建有幂等、事务和审计，更新/启停有版本校验和审计；停用店铺不删除历史订单，创建报价时服务端检查店铺必须 `ACTIVE` |
| 缺失操作 | 店铺编码/名称/平台/状态筛选不完整；没有页面分页、排序、详情、编辑、启停、默认渠道和地址配置；没有显示国家/地区、默认发货地址、默认物流渠道、更新时间的完整列表 |
| 当前错误和占位 | 通用表格只显示 4 列，错误/空数据使用通用状态；服务端存在详情和写接口但页面未调用，容易被误判为已完成 |
| 对应测试文件 | 前端：暂无店铺页面/服务专用测试；后端：`StoreApplicationServiceTest`、`StoreControllerWebMvcTest`、`StoreMapperXmlTest` |

### 4.3 物流基础资料

| 项目 | 当前实现 |
|---|---|
| 页面路由 | `/app/logistics`，`frontend/src/views/LogisticsView.vue` |
| 当前 API | 平台账号调用 `GET /api/v1/platform/logistics-providers`、`GET /api/v1/platform/logistics-channels`；租户账号调用 `GET /api/v1/logistics/channels`；service 另有详情、服务国家、生效价格规则和平台维护方法 |
| 请求参数 | 租户可用渠道支持 `countryCode`、`page`、`pageSize`；平台渠道支持 `providerId`、`status`、`page`、`pageSize`；页面当前未传筛选条件 |
| 响应字段 | 物流商：`id`、`providerCode`、`providerName`、`status`、`version`、时间；渠道：`id`、`providerId`、`channelCode`、`channelName`、`transportMode`、`serviceArea`、`status`、`serviceCountries`、`version`、时间；价格规则另含版本、币种、体积除数、进位方式和阶梯 |
| 数据库来源 | 公共 `logistics_provider`、`logistics_channel`、`logistics_channel_service_country`、`price_rule`、`price_rule_tier`；这些公共主数据不带 `tenant_id`，但平台写接口受 scope/permission 保护 |
| 当前角色权限 | 平台读为 `scope:PLATFORM + logistics:read`，平台写另需 `logistics:manage`/`price-rule:manage`；租户读为 `scope:TENANT + logistics:read` |
| 已完成操作 | 平台/租户列表读取、加载/错误/空状态；报价创建会读取启用店铺与可用渠道；后端公共主数据写操作有幂等、版本和审计能力 |
| 缺失操作 | 渠道编码/名称/服务国家/状态筛选，详情和服务国家查看，平台物流商/渠道/服务国家维护，价格规则详情与发布，停用渠道对报价/订单的明确行为提示 |
| 当前错误和占位 | 页面直接显示 `ACTIVE`、`AIR` 等技术编码，未统一中文状态/运输方式徽标；平台 service 已有写方法但页面没有对应表单 |
| 对应测试文件 | 前端：暂无物流页面/服务专用测试；后端：`LogisticsMasterApplicationServiceTest`、`LogisticsMasterControllerWebMvcTest`、`LogisticsMasterMapperXmlTest`、`TenantLogisticsControllerWebMvcTest`、`PriceRuleTierValidatorTest` |

### 4.4 报价管理

| 项目 | 当前实现 |
|---|---|
| 页面路由 | `/app/quotes` 使用 `WorkflowView(domain='quotes')`；`/app/quotes/create` 使用 `QuoteView` |
| 当前 API | `GET /api/v1/quotes`、`POST /api/v1/quotes`、`GET /api/v1/quotes/{quoteId}`、`POST /api/v1/quotes/{quoteId}/validate`；从报价转订单使用 `POST /api/v1/quotes/{quoteId}/shipment-orders` |
| 请求参数 | 列表 `storeId`、`channelId`、`status`、`page`、`pageSize`；创建 `storeId`、`channelId`、`declaredWeight`、`declaredLength`、`declaredWidth`、`declaredHeight`、`destinationCountry`；写请求由前端生成幂等键和请求 ID |
| 响应字段 | `id`、`quoteNo`、`storeId`、`channelId`、`destinationCountry`、`ruleVersionNo`、四项申报尺寸重量、`declaredVolumeWeight`、`declaredChargeableWeight`、`amount`、`currency`、`feeDetail`、`validFrom`、`validTo`、`status`、`version`；校验返回 `exists`、`expired`、`canCreateOrder`、`reason` |
| 数据库来源 | `quote`；创建时读取 `merchant_store`、`logistics_channel`、`price_rule`、`price_rule_tier`，费用明细固化到 `fee_detail`，并通过 `api_idempotency_record`、`audit_log` 记录幂等和审计 |
| 当前角色权限 | 列表/详情 `scope:TENANT + quote:read`；创建 `quote:create`；校验 `quote:validate`；转订单 `order:create` |
| 已完成操作 | 三步报价创建、真实渠道和店铺准备数据、后端 `BigDecimal` 计费、有效期校验、费用明细读取、过期/取消/已使用报价阻止转订单、地址和货物明细校验 |
| 缺失操作 | 报价号/店铺/目的地/渠道/状态/创建时间/有效期筛选不完整；没有页面分页；复制、重新报价、价格阶梯完整展示和业务化失效操作不完整；列表/详情仍由通用工作流承载 |
| 当前错误和占位 | `WorkflowView` 通过首行对象动态取最多 7 列，不能保证完整响应字段；访客预估国家、货物类型等枚举在前端固定，属于预估表单选项而非正式业务数据；金额不能由前端改写，但 traceId 未保留到页面 |
| 对应测试文件 | 前端：暂无报价页面/服务专用测试；后端：`QuoteCalculatorTest`、`QuoteCreationApplicationServiceTest`、`QuoteQueryApplicationServiceTest`、`QuoteControllerWebMvcTest`、`QuoteMapperXmlTest` |

### 4.5 订单管理

| 项目 | 当前实现 |
|---|---|
| 页面路由 | `/app/orders` 使用 `WorkflowView(domain='orders')`；仓库独立页面为 `/app/warehouse` |
| 当前 API | `GET /api/v1/orders`、`GET /api/v1/orders/{orderId}`、`POST /api/v1/quotes/{quoteId}/shipment-orders`、`PUT /api/v1/orders/{orderId}`、`POST /api/v1/orders/{orderId}/submit`、`POST /api/v1/orders/{orderId}/cancel`；订单详情另外读取分页轨迹事件 |
| 请求参数 | 列表只有 `status`、`storeId`、`page`、`pageSize`；创建包含发/收件地址和货物项；草稿更新为非计价地址/货物项及 `version`；提交/取消为 `version` 和原因 |
| 响应字段 | `ShipmentOrder`：`id`、`orderNo`、`quoteId`、`status`、`estimatedFee`、`currency`、`chargeableWeight`、`version`、`createdAt`；创建链路还落地址、包裹、商品和报价快照 |
| 数据库来源 | `shipment_order`、`shipment_quote_snapshot`、`shipment_address`、`shipment_package`、`shipment_item`；履约阶段再关联 `warehouse_measurement`、`fee_adjustment`、`warehouse_outbound_record`、`provider_order`、`tracking_event`、`exception_case`、`reconciliation_record` |
| 当前角色权限 | 查询 `scope:TENANT + order:read`；创建 `order:create`；草稿编辑/提交/取消 `order:manage`；业务员使用 `order:price-request` 提交申请，财务和租户管理员使用 `order:price-confirm` 最终确认 |
| 已完成操作 | 从有效报价创建草稿订单，消费报价快照；幂等键和唯一约束防重复创建；草稿编辑、提交、取消有事务、版本校验和审计；订单详情可读取轨迹事件 |
| 缺失操作 | 订单号/店铺/目的地/渠道/顺丰单号/创建时间筛选，分页排序、批量选择、导出，订单完整时间线，费用调整/费用确认、仓库/轨迹/异常/索赔的统一详情入口，以及成功/失败 traceId 复制 |
| 当前错误和占位 | OpenAPI 声明 `POST /orders/{orderId}/price-confirmation`，但当前未发现匹配 Controller，`frontend/src/services/warehouse.ts` 也没有 `confirmPrice`；通用列表只显示部分字段，不能作为企业订单详情 |
| 对应测试文件 | 前端：暂无订单页面/服务专用测试；后端：`ShipmentOrderApplicationServiceTest`、`ShipmentOrderLifecycleApplicationServiceTest`、`ShipmentOrderControllerWebMvcTest`、`ShipmentOrderManagementControllerWebMvcTest`、`ShipmentOrderMapperXmlTest` |

### 4.6 轨迹全链路

| 项目 | 当前实现 |
|---|---|
| 页面路由 | `/app/tracking`，`frontend/src/views/TrackingView.vue`；订单详情还使用 `listTrackingEvents` |
| 当前 API | 全链路 `GET /api/v1/orders/{orderNo}/tracking`，路径变量实际可按订单号或顺丰单号查询；订单分页事件 `GET /api/v1/orders/{orderId}/tracking-events`；另有 `/shipment-orders/{orderId}/tracking` 和 `/status` typed service |
| 请求参数 | 全链路只有 `reference` 路径变量；分页事件有 `page`、`pageSize`；页面没有时间范围、物流状态或自动刷新参数 |
| 响应字段 | 全链路事件：`id`、`orderId`、`orderNo`、`waybillNo`、`statusCode`、`title`、`description`、`location`、`source`、`occurredAt`；分页事件另有 `trackingNo`、`eventId`、`eventCode`、`eventDescription`、`eventTime`、`receivedTime`、`processStatus` |
| 数据库来源 | `shipment_order`、`warehouse_outbound_record`、`provider_order`、`tracking_event`；外部回调通过 provider 校验、事件幂等键和处理状态入库；沙箱调度器按受控配置推进测试订单 |
| 当前角色权限 | `scope:TENANT + tracking:read`；回调是独立 server-to-server 入口，不属于浏览器菜单 |
| 已完成操作 | 按订单号/运单号查全链路、按 UTC 返回时间并在页面按 `Asia/Shanghai` 展示、加载/空数据/失败状态、区分仓内/顺丰来源、订单详情分页事件和租户过滤 |
| 缺失操作 | 订单号/顺丰单号/时间范围/物流状态筛选，手动刷新与自动刷新，异常事件跳转，供应商错误与系统 traceId 的明确展示，乱序事件的 UI 说明；未确认事件不能显示为签收的状态契约仍需统一测试 |
| 当前错误和占位 | 输入框示例使用固定测试样式订单号；错误只显示中文失败消息，没有可复制 traceId；全链路页没有事件分页和刷新按钮 |
| 对应测试文件 | 前端：`frontend/src/services/tracking.spec.ts`；后端：`ShipmentTrackingControllerWebMvcTest`、`ShipmentTrackingQueryApplicationServiceTest`、`ShipmentTrackingMapperXmlTest`、`TrackingEventsControllerWebMvcTest`、`TrackingQueryApplicationServiceTest`、`TrackingQueryControllerWebMvcTest`、`TrackingQueryMapperXmlTest`、回调/状态机/沙箱测试 |

### 4.7 异常与索赔

| 项目 | 当前实现 |
|---|---|
| 页面路由 | `/app/exceptions` 使用 `WorkflowView(domain='exceptions')` |
| 当前 API | `GET /api/v1/exceptions`、`GET /api/v1/exceptions/{exceptionId}`；service 已声明创建、分派、状态变更、创建/查询/提交/审核/关闭索赔的 typed 方法 |
| 请求参数 | 列表只有 `orderId`、`status`、`page`、`pageSize`；写操作含异常类型/描述/报告时间/轨迹事件、负责人、原因、`version`、索赔金额/币种等 |
| 响应字段 | 异常：`id`、`orderId`、`exceptionNo`、`exceptionType`、`status`、`description`、`reportedAt`、`assignedToUserId`、`version`、`createdAt`、`updatedAt`、嵌套 `claim`；索赔：`id`、`exceptionId`、`claimNo`、`status`、`claimAmount`、`currency`、提交/解决时间、版本和时间 |
| 数据库来源 | `exception_case`、`claim_record`、`tracking_event`、`shipment_order`、`api_idempotency_record`、`audit_log`；当前 schema 没有证据附件或处理记录表的工作台查询/写契约 |
| 当前角色权限 | 查询 `scope:TENANT + exception:read`；创建、分派、状态和索赔写操作需 `exception:manage` |
| 已完成操作 | 后端列表/详情、异常状态机、索赔资格校验、金额/币种校验、幂等、版本并发控制和审计已存在；前端能读取列表/详情并显示 claim 字段 |
| 缺失操作 | 异常类型/订单号/店铺/责任方/创建时间筛选，创建、分派、处理记录、证据上传、索赔发起/审核/关闭、完整时间线；业务状态缺少“待物流商反馈”“待财务确认” |
| 当前错误和占位 | 页面没有任何异常/索赔写操作按钮；`WorkflowView` 的 ID 查询提示仍是通用“异常单 ID”，不能呈现下一步动作、权限原因和恢复方式 |
| 对应测试文件 | 前端：暂无异常页面/服务专用测试；后端：`ExceptionClaimApplicationServiceTest`、`ExceptionClaimControllerWebMvcTest`、`ExceptionClaimMapperXmlTest`、`ExceptionClaimStateMachineTest` |

### 4.8 账单与对账

| 项目 | 当前实现 |
|---|---|
| 页面路由 | `/app/billing`，`frontend/src/views/FinanceView.vue` |
| 当前 API | `POST /api/v1/billing/import-batches`；批次列表/详情/错误行、账单明细列表、对账列表/详情/确认，以及按权限显示 `GET /api/v1/audit-logs` |
| 请求参数 | 批次支持 `providerId`、`status`、`page`、`pageSize`；明细支持 `batchId`、`status`、分页；对账支持 `orderId`、`status`、分页；确认提交 `resolutionType`、`remark`、`version`；导入为 multipart 文件和 `providerId` |
| 响应字段 | 批次：批次号、文件、哈希/大小、状态、总数/成功数/失败数、版本和时间；明细：供应商明细号、行号、订单、运单、金额、币种、费用类型、状态、错误；对账：系统金额、供应商金额、差异、状态、处理人/说明/版本和时间 |
| 数据库来源 | `bill_import_batch`、`bill_detail`、`reconciliation_record`、`shipment_order`、`audit_log`；`fee_adjustment` 有数据写入链路，但当前没有按租户查询接口 |
| 当前角色权限 | 页面页签按 `billing:read`、`finance:bill-import`、`finance:reconcile`、`audit:read` 控制；后端按 `scope:TENANT` 和相应权限保护导入、读取、确认、审计 |
| 已完成操作 | 批次/错误行/明细/对账列表、详情和状态筛选；零差异由后端自动关闭，非零差异进入待确认；确认操作有表单、版本、CSRF/请求 ID/幂等请求头和审计 |
| 缺失操作 | 账单导入文件选择和结果入口、总行/成功/错误/重复行完整展示、分页/排序、费用调整查询、对账驳回/补充说明的明确业务动作；平台/财务审计权限显示还需和角色矩阵统一 |
| 当前错误和占位 | 页面明确显示“后端尚未提供 `fee_adjustment` 查询契约”，未伪造金额；列表默认只取第一页；导入 service 已有但 FinanceView 没有导入按钮；非零差异处理表单目前只有确认路径 |
| 对应测试文件 | 前端：`frontend/src/services/billing.spec.ts`；后端：`BillingApplicationServiceTest`、`BillingControllerWebMvcTest`、`BillingMapperXmlTest`，另有审计查询测试 |

## 5. 租户隔离、scope、资源归属和写操作基线

### 5.1 已确认的安全边界

1. `SecurityConfig` 按 `scope:PLATFORM`/`scope:TENANT` 和实时 permission 保护主要路由；前端菜单和路由守卫只是体验层，不是授权边界。
2. 租户业务 Controller 从 JWT `tenant_id` 取租户，不接受前端传入的任意租户 ID；Mapper SQL 普遍带 `tenant_id=#{tenantId}`。
3. 店铺、报价、订单、仓库、轨迹、异常、账单、对账和审计查询均有租户条件；跨租户资源通常返回统一 404，避免通过 ID 探测资源。
4. 公共物流商、渠道、服务国家、价格规则是平台主数据，不带 `tenant_id`；租户只读接口由 `scope:TENANT + logistics:read` 保护，平台写接口由平台 scope 和管理权限保护。
5. 创建报价、订单、店铺、物流主数据、异常、索赔等主要写操作使用幂等记录或唯一约束；订单/店铺/角色/仓库/异常/索赔/对账更新使用版本或状态条件避免并发覆盖；关键写操作大多使用 `@Transactional` 并写 `audit_log`。
6. 后端以 `BigDecimal`/`DECIMAL` 保存金额、重量和尺寸；数据库和后端时钟以 UTC；OpenAPI 时间字段声明为 UTC ISO-8601，前端通用展示工具转换为北京时间。

### 5.2 第一阶段待补齐边界与第二阶段结论

| 风险 | 当前证据 | 后续要求 |
|---|---|---|
| store_id 细粒度范围 | 当前权限和 SQL 主要是租户级，没有独立业务员店铺授权集合 | 明确是否所有租户用户可见所有店铺；若不是，增加 store scope 与资源归属测试 |
| traceId 丢失 | `ApiSuccess` 有 `traceId`，但 `frontend/src/services/http.ts` 的 `unwrap` 只返回 `data`；`useSubmit` 只保存 message/code | 统一响应 DTO 或请求结果包装，支持复制订单号、报价号和 Trace ID |
| 状态契约漂移 | DB、OpenAPI、TS union、页面标签同时存在订单/仓库/供应商/处理状态 | 建立后端标准状态字典和契约测试，禁止前端自行推断 |
| 时间显示 | 后端返回 UTC；`display.ts` 和 TrackingView 指定 `Asia/Shanghai`，但 Dashboard/Directory/平台页面仍使用浏览器本地 `toLocaleString` | 统一明确时区的时间格式化器并测试 UTC→北京时间 |
| 重量单位 | schema/API 注释以 kg 为主；当前订单投影公共显示 helper 按既有约定将数值除以 1000，而报价字段保持 kg | 在状态/契约阶段固化字段级单位，保留订单投影 `5400 → 5.40 kg` 的既有约定，禁止套用到报价 |
| 订单费用确认 | 第一阶段盘点时 OpenAPI 和安全配置已有 `order:price-confirm`，但后端 Controller/service/前端方法缺失 | 第二阶段本轮已补齐接口、最小数据库结构、审计、幂等/版本、租户/店铺隔离和测试；真实数据库断言仍待迁移获批准后执行 |
| 账单导入幂等 | Controller 接收的 `Idempotency-Key` 当前参数名为 ignored，Service 以租户+供应商+文件哈希去重 | 明确“同文件重放”与“同幂等键不同文件”的冲突契约并测试 |
| 异常处理记录/证据 | 当前 API/表主要覆盖异常与索赔主记录，未发现处理记录/附件工作台契约 | 先补数据模型和权限，再接 UI，不能用备注字段冒充证据链 |

## 6. 静态数据、演示数据和前端计算检查

| 位置 | 发现 | 是否属于正式业务数据 |
|---|---|---|
| `frontend/src/views/HomeView.vue` | 公开首页有“工作台演示数据”及明确免责声明 | 否；属于营销展示，不得复用到 `/app` |
| `frontend/src/views/DashboardView.vue` | 指标值来自真实 API；流程阶段名称和状态标签由前端固定；没有假数值 | 标签可保留，但状态/阶段应由后端契约驱动；当前不是静态业务结果 |
| `frontend/src/views/WarehouseWorkView.vue` | “待打单/贴标”页签固定禁用，并显示“等待 V013 迁移和顺丰官方面单契约” | 是当前工作台占位；V013 已在仓库，需重新核对实际面单契约后处理 |
| `frontend/src/views/FinanceView.vue` | 明确写出 `fee_adjustment` 查询缺口且不展示伪造数据 | 正确的缺口提示，不应删除为假数据 |
| `frontend/src/components/EmptyState.vue` | 默认文案为“后续阶段按契约逐步接入” | 通用占位文案；核心页面完成后应替换为具体中文业务空态 |
| `frontend/src/views/QuoteView.vue`、`WorkflowView.vue` | 国家、运输方式、访客货物类型、订单表单选项在前端固定 | 是表单枚举/展示字典，不是统计假数据；第二阶段需与 OpenAPI/后端枚举做契约测试 |
| `frontend/src/views/WorkflowView.vue` | 列表列由第一条响应对象动态取最多 7 个字段，部分状态和详情只在前端格式化 | 不是假数据，但会造成响应字段丢失和页面能力被错误隐藏 |

## 7. OpenAPI、前后端 DTO 和实现差异

### 7.1 已对齐的主要契约

- 前端使用相对 `/api/v1` Axios、`withCredentials`、内存 Bearer Token、写请求 CSRF；后端统一返回 `success`、`traceId`、`data` 或错误结构。
- 报价、订单、仓库、轨迹、异常、账单、对账和审计的主要路径均已在 OpenAPI 和后端目录中找到对应声明。
- 分页统一使用 `page`、`pageSize`、`total`、`totalPages`、`items`；后端普遍限制 `pageSize <= 100`。
- 时间字段以 UTC 偏移时间返回；金额和重量 DTO 使用十进制定点数语义，前端不负责重算报价金额。

### 7.2 当前差异清单

| 优先级 | 差异 | 影响 |
|---|---|---|
| P0（第一阶段记录，已关闭） | 第一阶段盘点时 OpenAPI 的 `confirmPrice` (`POST /orders/{orderId}/price-confirmation`) 与 `order:price-confirm` 权限存在，但后端 Controller 和前端 `confirmPrice` service 缺失 | 第二阶段已补齐费用申请、查询和最终确认；仍需在批准迁移后做真实数据库验收 |
| P1 | OpenAPI/typed service 的列表查询参数比页面实际使用的多；多数页面没有服务端分页、筛选和排序控件 | 运营人员只能看默认第一页，统计与列表难以保持同一筛选快照 |
| P1 | `WorkflowView` 用动态字段生成表格，且只取前 7 个字段；店铺/物流页面也只展示部分字段 | 订单/报价/异常/账单响应字段存在“后端有、页面无”的隐性丢失 |
| P1 | 异常、索赔、店铺、物流、用户/RBAC 等 typed service 已有多项写方法，但核心业务页面未提供动作入口 | service/Controller 覆盖率不能代表业务操作完成率 |
| P1 | `ApiSuccess.traceId` 在 `unwrap` 后被丢弃，写操作反馈无法复制系统 Trace ID | 不满足关键操作的可追踪交付要求 |
| P2 | 技术编码、中文标签和状态徽标未统一；部分页面直接输出 `ACTIVE`、`AIR` 等编码 | 业务员无法稳定理解跨领域状态，易误操作 |
| P2 | OpenAPI 只覆盖已有主记录，异常处理记录/证据、费用调整查询和完整订单时间线没有契约 | 后续不能通过前端拼装或静态字段补齐这些能力 |

## 8. 当前已完成与当前缺失的操作矩阵

| 模块 | 当前已完成 | 当前缺失/仅后端有能力 |
|---|---|---|
| 运营概览 | 真实摘要、待办、仓库概览、最近订单/异常、基础加载错误 | 指标跳转、时间范围、刷新时间、风险提醒、完整我的待办、统计一致性证明 |
| 店铺 | 列表、创建、后端详情/编辑/启停 | 页面详情、编辑、启停、完整筛选/分页、地址/渠道配置 |
| 物流 | 租户/平台列表，报价可用渠道加载 | 详情、服务国家、平台维护、价格规则详情/发布、停用行为提示 |
| 报价 | 创建、列表、详情、校验、报价转订单 | 复制、重新报价、完整价格明细、完整筛选分页、统一状态徽标 |
| 订单 | 报价转草稿、草稿更新、提交、取消、费用确认申请/最终确认、部分轨迹读取 | 完整时间线、批量/导出、详情聚合、完整筛选分页 |
| 仓库/物流 | 入库、复称、出库、费用确认、顺丰沙箱操作、真实仓库列表 | 待打单闭环、供应商错误恢复、完整状态契约 |
| 轨迹 | 订单号/运单号时间线、分页事件、来源区分、加载/空/错状态 | 筛选、刷新、异常跳转、trace 展示、乱序/未确认状态统一 |
| 异常/索赔 | 后端状态机、查询、资格校验、幂等/审计 | 页面创建、分派、处理记录、证据、索赔全生命周期、业务状态补充 |
| 账单/对账 | 批次/明细/错误/对账查询、详情、零差异自动关闭、差异确认 | 导入 UI、分页、重复行统计、费用调整查询、完整人工处理动作 |

## 9. 当前测试覆盖矩阵

| 领域 | Controller/WebMvc | Service/领域 | Mapper XML/数据库契约 | 前端 |
|---|---|---|---|---|
| 概览 | `OperationsControllerWebMvcTest`、仓库概览 Controller 测试 | `OperationsQueryApplicationServiceTest`、仓库概览 Service 测试 | `OperationsMapperXmlTest`、`WarehouseOverviewMapperXmlTest` | `dashboardLayout.spec.ts`，无真实 API 与列表一致性视图测试 |
| 店铺 | `StoreControllerWebMvcTest` | `StoreApplicationServiceTest` | `StoreMapperXmlTest` | 无店铺专用测试 |
| 物流 | `LogisticsMasterControllerWebMvcTest`、`TenantLogisticsControllerWebMvcTest` | `LogisticsMasterApplicationServiceTest`、价格阶梯测试 | `LogisticsMasterMapperXmlTest` | 无物流专用测试 |
| 报价 | `QuoteControllerWebMvcTest` | `QuoteCreationApplicationServiceTest`、`QuoteQueryApplicationServiceTest`、`QuoteCalculatorTest` | `QuoteMapperXmlTest` | 无报价页面/服务测试 |
| 订单 | `ShipmentOrderControllerWebMvcTest`、`ShipmentOrderManagementControllerWebMvcTest` | 创建、生命周期 Service 测试 | `ShipmentOrderMapperXmlTest` | 无订单页面/服务测试 |
| 仓库 | `WarehouseControllerWebMvcTest`、概览/作业 Controller 测试 | `WarehouseApplicationServiceTest`、概览/作业 Service 测试、重量测试 | `WarehouseMapperXmlTest`、概览/作业 Mapper XML 测试 | `warehouse.spec.ts`、`warehouseWorkRules.spec.ts` |
| 轨迹 | 回调、分页事件、全链路 Controller 测试 | 查询、回调、状态映射、沙箱调度器测试 | 轨迹 Mapper XML/迁移静态测试 | `tracking.spec.ts`，无全链路页面行为测试 |
| 异常/索赔 | `ExceptionClaimControllerWebMvcTest` | Service 和异常/索赔状态机测试 | `ExceptionClaimMapperXmlTest` | 无异常页面/服务测试 |
| 账单/对账 | `BillingControllerWebMvcTest`、`AuditQueryControllerWebMvcTest` | `BillingApplicationServiceTest`、`AuditQueryApplicationServiceTest` | `BillingMapperXmlTest`、`AuditQueryMapperXmlTest` | `billing.spec.ts`，无导入组件测试 |

缺失的测试类型与附件要求中的后续目标一致：核心页面视图行为、统计与列表一致性、筛选分页、状态流转、重复提交、401/403/404/409/422/500、租户/店铺归属、后端拒绝和真实数据库断言仍需逐模块增加。

## 10. 第一阶段验证记录

### 10.1 已执行的只读检查

- 已扫描前端路由、菜单、页面、组件、service、状态和测试文件。
- 已扫描后端 Controller、Service、Mapper/XML、SecurityConfig、审计/幂等实现和测试文件。
- 已扫描 `database/schema.sql`、`database/init_data.sql`、迁移和 OpenAPI 路径/operationId。
- 已执行静态占位/演示数据检索，并区分公开营销演示、明确缺口提示和工作台占位。
- 未连接生产数据库，未执行数据库写入或迁移，未调用顺丰生产接口。

### 10.2 本阶段文档变更

- 新增：`docs/module-lifecycle/05-merchant-operations-workbench-enterprise-baseline.md`。
- 本阶段未修改 Java、TypeScript/Vue、SQL、OpenAPI 或配置代码。

### 10.3 尚未在本阶段由本文证明的内容

- 本文不是数据库现网数据核对报告；未证明统计数量与某个运行租户的现网列表完全相等。
- 本文不是完整测试通过报告；前端单元测试、生产构建、后端编译和后端测试需在文档落盘后单独执行并记录真实结果。
- 第一阶段报告没有把当时 OpenAPI 中声明但后端缺失的费用确认接口写成已完成；该缺口已由第二阶段本轮实现补齐。
- 未把前端 typed service 已有但页面未调用的写操作写成已完成。

## 11. 是否进入第二阶段

第一阶段盘点和基线文档可以进入验证；验证通过后可以进入第二阶段“统一业务状态和数据契约”。进入前需要项目负责人确认：

1. 商家业务员是否应该拥有 `exception:manage` 和订单提交/取消权限，还是仅由租户管理员/客服处理？
2. `store_id` 是否需要细分到业务员可见店铺范围，还是当前租户内所有业务员共享全部店铺？
3. `PENDING_PRICE_CONFIRMATION` 的费用确认由商家业务员、租户管理员还是财务人员执行？确认后订单是否进入 `READY_FOR_OUTBOUND`？
4. “待打单/贴标”是否以当前顺丰沙箱 `provider_order.lifecycle_status=LABEL_READY` 为正式契约，还是需要独立面单资源和下载权限？
5. 账单导入的重复判定以文件哈希、`Idempotency-Key`，还是两者组合为准？
6. 异常处理记录、证据附件、责任方和“待物流商反馈/待财务确认”是否需要新增表和对象存储契约？
7. 运营概览的“今日”按 UTC 自然日还是按业务员所在的 `Asia/Shanghai` 自然日统计？

## 12. 第一阶段真实验证补充（历史快照，2026-08-17）

### 12.1 已执行验证

- 后端使用 `C:\Program Files\Java\jdk-21.0.11` 执行 Maven 真实测试，使用独立构建目录 `D:\Projects\shipflow\.codex-build\backend-target-enterprise-baseline-20260817`，避免覆盖仍被运行进程占用的原 `backend/target`。
- 后端结果：编译成功，`Tests run: 360, Failures: 0, Errors: 0, Skipped: 0`，最终 `BUILD SUCCESS`。
- 前端 `npm run test:unit`：11 个测试文件、40 个测试全部通过。
- 前端 `npm run build`：`vue-tsc -b` 和 Vite 生产构建均通过。
- `git diff --check`：通过；同时出现的 LF/CRLF 转换提示是工作区换行告警，不是 diff 检查错误。
- OpenAPI 结构扫描：84 个 path、98 个 HTTP 方法、98 个 `operationId`，未发现重复 path；这是第一阶段快照，当时 `/orders/{orderId}/price-confirmation` 仍在 OpenAPI 和权限配置中声明，但后端 Controller/service 与前端 service 仍缺失，第二阶段已补齐。
- 敏感字面量扫描未发现私钥头、常见云厂商访问键或 `sk-/pk-` 形式 provider key；该扫描不替代凭证轮换和运行时 secret 检查。

### 12.2 尚未执行或不能宣称通过的验证

- 当前环境没有可用的 Python YAML 解析器；前端依赖中也没有本地 `yaml` Node 模块。`npx --no-install swagger-cli validate` 因本地未安装而尝试访问 registry，并被环境权限阻断，因此本阶段只记录结构扫描结果，不把完整 YAML 解析校验写成已通过。
- 未连接生产数据库，未执行生产数据写入、迁移或外部顺丰生产接口调用；本次后端测试为单元、WebMvc、service、mapper XML 等测试集，不等于租户真实数据验收。
- 前端扫描到的演示/占位标记已按性质区分：公开首页的“演示数据”是明确营销演示；账单费用调整页面明确提示后端契约缺口且不展示伪造数据；工作台部分能力明确提示待正式契约，不能作为已完成业务能力。

### 12.3 阶段结论

第一阶段的现状盘点、基线文档和本地可执行验证已完成，项目负责人已确认第二阶段的业务规则。第二阶段本轮已修改订单费用确认相关业务源代码、契约和前端入口，并完成本地测试；仍未执行数据库迁移、生产调用、Git 提交或推送。

## 13. 第二阶段负责人确认规则（2026-08-17）

1. 费用确认由财务人员最终执行；租户管理员可作为备用审批人；商家业务员只能提交费用确认申请，不能最终确认。
2. `store_id` 必须参与权限和数据隔离。商家业务员只能查看被授权店铺；租户管理员可查看本租户全部店铺；后端必须同时校验 `tenant_id`、`store_id`、角色和资源归属。
3. 费用确认成功后，订单状态必须进入 `READY_FOR_OUTBOUND`。
4. 待打单/贴标统一使用 `LABEL_READY`。
5. 账单重复判断使用文件内容哈希与 `Idempotency-Key` 组合，不得只依赖文件名。
6. 异常处理必须保留责任方、处理记录、证据附件和索赔关联；优先复用现有表，仅在确实缺失时新增最小数据库结构。
7. 数据库时间统一按 UTC 存储；业务“今日”统计和前端展示使用 `Asia/Shanghai`，并在接口文档中明确。
8. 第二阶段先落盘本基线规则和设计文档，再开始代码修改；每次只实现一个模块。
9. 代码修改前必须复核现有状态机、权限矩阵、数据库表和 OpenAPI，禁止重复定义。
10. 完成后必须执行后端、前端、契约和 `git diff --check` 验证；未确认的数据库迁移、生产调用和 Git 提交不得执行。

## 14. 异常处理模块基线补充（2026-08-17）

异常处理以订单所属店铺为资源边界。后端每次读取或写入异常、处理记录、证据附件和索赔时，必须同时校验当前用户、`tenant_id`、订单 `store_id`、角色权限和资源归属；商家业务员只允许访问 `sys_user_store_scope` 中启用的店铺，租户管理员、财务和客服等已授权租户角色只能访问本租户启用店铺。

异常责任方使用结构化字段 `exception_case.responsible_party`，不得只从审计 JSON 推导。处理记录为追加式业务事实，保留处理人、记录类型、内容和 UTC 创建时间。证据附件保留原文件名、媒体类型、大小、内容 SHA-256、上传人和内容本体；文件内容哈希用于同一异常下的重复附件判断，文件名不作为唯一依据。现有 `claim_record.exception_case_id` 继续作为异常与索赔的一对一关联，不新增重复关联表。

异常状态允许 `OPEN -> PROCESSING -> WAITING_PROVIDER_FEEDBACK -> PROCESSING` 或 `RESOLVED`，`RESOLVED -> PENDING_FINANCE_CONFIRMATION -> CLOSED`，也允许 `RESOLVED -> CLOSED`。`CLOSED` 为终态，非法跳转返回 409。处理记录和附件上传不得绕过异常资源归属或已关闭状态校验。

接口时间字段统一返回 UTC ISO-8601；前端展示和“今日”统计转换为 `Asia/Shanghai`。数据库所有新增时间列使用 UTC `DATETIME(3)` 语义。附件上传、处理记录和状态推进均要求幂等键或版本校验、事务和审计；不执行生产数据库迁移或生产调用。
