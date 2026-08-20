# 仓库操作员与顺丰国际履约闭环

## 1. 审计范围与结论

本轮只读检查了 `backend/src/main/java`、`backend/src/main/resources`、`frontend/src`、`openapi/shipflow-api.yaml`、数据库 schema/migrations 和仓库文档。未连接数据库、未执行迁移、未调用顺丰接口、未提交或推送 Git。

仓库现有后端能力已覆盖入库、复称、计费重量/费用调整和出库；前端已接入订单详情、仓库操作和订单轨迹查询。本轮补齐了仓库操作响应中的最新 `version`、复称乐观锁条件、物流渠道绑定校验、待处理订单列表入口和真实请求字段映射。

顺丰五个接口均未发现现有实现，当前不具备安全的沙箱联调条件，详见《顺丰国际接口接入前置条件》。

## 2. 现有仓库接口

| operationId | 方法与路径 | 前端调用 | 权限 | 状态规则 |
| --- | --- | --- | --- | --- |
| `confirmInbound` | `POST /api/v1/orders/{orderId}/inbound` | `warehouse.confirmInbound`，仓库页“确认入库” | `scope:TENANT` + `warehouse:manage` | `PENDING_INBOUND -> INBOUND` |
| `submitMeasurement` | `POST /api/v1/orders/{orderId}/measurements` | `warehouse.submitMeasurement`，仓库页“提交复称” | `scope:TENANT` + `warehouse:manage` | 仅 `INBOUND`；写入复称，更新计费重量/费用和版本 |
| `confirmPrice` | `POST /api/v1/orders/{orderId}/price-confirmation` | 现有订单 service | `scope:TENANT` + `order:price-confirm` | 费用上涨后由授权商家角色确认 |
| `confirmOutbound` | `POST /api/v1/orders/{orderId}/outbound` | `warehouse.confirmOutbound`，仓库页“确认出库” | `scope:TENANT` + `warehouse:manage` | `READY_FOR_OUTBOUND -> OUTBOUND`，必须绑定渠道产生出库记录 |
| `listShipmentOrders` / `getOrder` | `GET /api/v1/orders`、`GET /api/v1/orders/{orderId}` | 仓库页待处理列表和详情 | `scope:TENANT` + `order:read` | Service/Mapper 以 `tenant_id` 隔离 |
| `listTrackingEvents` | `GET /api/v1/orders/{orderId}/tracking-events` | 订单页轨迹区域、轨迹页 service | `scope:TENANT` + `tracking:read` | 只返回当前租户可见事件 |

仓库操作成功响应统一为 `WarehouseResult`：`orderId`、`status`、`chargeableWeight`、`currentFee`、`version`。复称请求只提交后端 DTO 支持的重量、尺寸和 `version`；出库请求只提交 `trackingNo`、可选 `remark` 和 `version`。三个写接口要求 `Idempotency-Key`，复用 `api_idempotency_record`，同一租户/操作/幂等键的重复请求只返回已有订单结果，不同请求体返回冲突。

## 3. 页面状态

仓库员进入 `/app/warehouse` 后自动加载当前租户订单列表，可按订单 ID查看详情。按钮按服务端返回的 `status` 控制：

- `PENDING_INBOUND` 才能确认入库；
- `INBOUND` 才能提交复称；
- `READY_FOR_OUTBOUND` 且已填写物流单号才能确认出库；
- 其他状态按钮禁用，并保留后端最终校验。

页面区分加载中、空列表、查询失败、操作失败、操作成功和无权限（路由/后端 403）状态。仓库操作成功后重新读取订单详情，使用服务端返回的最新版本，避免把不完整的操作结果当成订单详情。

## 4. 数据表与状态

| 表 | 用途 | 关键隔离/状态字段 | 当前能力 |
| --- | --- | --- | --- |
| `shipment_order` | 订单主表 | `tenant_id`、`current_status`、`version`、`channel_id` | 查询、状态转换、费用更新 |
| `shipment_package` | 订单包裹 | `tenant_id`、`package_status` | 仓库复称/出库关联 |
| `shipment_item` | 货物明细 | `tenant_id`、订单外键 | 订单创建时保存 |
| `warehouse_measurement` | 复称历史 | `tenant_id`、重量尺寸、`measured_at` | 追加写入 |
| `fee_adjustment` | 费用调整历史 | `tenant_id`、调整类型、前后金额 | 复称时追加写入；当前无独立查询接口 |
| `warehouse_outbound_record` | 出库记录 | `tenant_id`、`provider_id`、`tracking_no` | 出库时写入，重复记录拒绝 |
| `tracking_event` | 物流轨迹事件 | `tracking_no`、`event_id`、`process_status` | 回调/查询写入，前端租户查询 |
| `logistics_provider` | 物流服务商 | 服务商主数据 | 由平台物流模块维护 |
| `logistics_channel` | 物流渠道 | `tenant_id`、`provider_id`、启用状态 | 出库时按订单渠道绑定服务商 |
| `audit_log` | 审计记录 | `tenant_id`、操作人、请求 ID | 仓库关键操作写入 |

本轮未发现顺丰专用的 `service_code`、外部物流订单号、面单数据、请求重试记录或清关资料表，因此不能声称顺丰闭环已完成，也没有新增迁移。

## 5. 角色边界

| 角色 | 可见/可执行 | 明确禁止 |
| --- | --- | --- |
| 仓库操作员 | 授权租户订单、入库、复称、出库、查看物流状态 | 用户/RBAC、财务账单、平台权限 |
| 商户业务人员 | 本租户订单、报价、轨迹及费用确认（以权限码为准） | 仓库入库、复称、出库 |
| 商户财务 | 本租户账单、对账、费用调整查询 | 仓库操作 |
| 平台管理员/运营 | 平台物流基础资料和平台审计 | 绕过租户订单隔离 |
| 物流服务商 | 受控 server-to-server 回调/接口 | 浏览器菜单和普通 Bearer Token API |

隐藏菜单不等于授权。后端 `SecurityConfig` 继续以 `scope:TENANT`、`warehouse:manage`、`order:read` 和 `tracking:read` 进行实时校验，跨租户订单由 Mapper 的 `tenant_id` 条件拒绝。

## 6. 顺丰五个接口状态

| serviceCode | 是否已有代码 | 当前原因 |
| --- | --- | --- |
| `COM_RECE_IUOP_CREATE_ORDER` | 否 | 缺少官方请求/响应和签名资料 |
| `COM_RECE_IUOP_PRINT_ORDER` | 否 | 缺少面单响应与存储契约 |
| `COM_RECE_IUOP_QUERY_ORDER` | 否 | 缺少状态/轨迹响应契约 |
| `COM_RECE_IUOP_CANCEL_ORDER` | 否 | 缺少取消规则和幂等契约 |
| `COM_RECE_IUOP_UPLOAD_CERTIFY` | 否 | 缺少清关资料契约和持久化模型 |

不得把这五个接口加入普通浏览器菜单。取得完整官方资料后，建议按 provider adapter、配置绑定、供应商客户端、统一错误处理、重试/退避、幂等和审计分层实现。

## 7. 测试与未执行项

本轮新增/更新了仓库 service、Mapper XML 静态检查和前端 service 契约测试。最终真实命令结果以任务结束报告为准。

未执行：数据库连接、数据库迁移、真实订单写入、HTTP 全量验收、顺丰沙箱/生产调用、浏览器自动化、Git 提交和推送。

## 8. 待人工提供与确认

要进入顺丰沙箱联调，必须先提供官方沙箱账号、`partnerID`、`checkWord`/签名密钥、客户编码、月结账号、产品/服务代码、面单和清关资料格式、查询/回调契约及 IP 白名单，并确认测试件是否会产生可取消或可追踪的沙箱运单。所有值只能通过受控环境变量注入，不能写入仓库。

## 9. 本轮问题定位与修复

### 9.1 仓库概览

仓库操作员原先只有 `warehouse:manage` 时，`DashboardView.vue` 会因为缺少 `operations:read` 进入通用的“仅显示已获授权入口”分支，导致仓库概览请求虽然具备条件却没有展示。现已将两种权限分开判断：拥有 `warehouse:manage` 的租户用户调用 `GET /api/v1/warehouse/overview`，仅在两种权限都没有时才显示无业务入口提示。

概览响应包含 `pendingInbound`、`pendingMeasurement`、`pendingOutbound`、`inTransit`、`trackingExceptions`、`todayInbound`、`todayOutbound`、`recentOrders` 和 `recentTrackingExceptions`。统计和列表均使用当前 JWT 的 `tenant_id`；“今日入库/出库”以 UTC 日期边界统计；轨迹异常只统计 `tracking_event.process_status` 为 `RETRY` 或 `REJECTED` 的事件，不把普通异常工单冒充轨迹异常。

### 9.2 订单轨迹

订单页和轨迹页统一调用 `GET /api/v1/orders/{orderId}/tracking-events?page=1&pageSize=50`，由 `scope:TENANT` + `tracking:read` 保护，服务端先按 `tenant_id` 校验订单归属，再分页读取 `tracking_event`。订单不存在或跨租户访问返回 `404 COMMON-1006`，权限不足返回 `403`，无事件的成功响应展示“暂无轨迹事件”，不再误报为请求失败。

本轮补齐了轨迹页结果表格绑定、空列表状态、概览权限状态和上述 Mapper 静态约束；真实浏览器 Network、订单 31 的 HTTP 状态码和后端 traceId 尚未执行，因此不能据此声称真实环境验收通过。

## 本轮学习记录

仓库操作的乐观锁版本必须由服务端响应返回并被前端保存；只在界面上维护旧版本会让连续复称/出库在并发场景下失去可靠性。轨迹页面必须和唯一的租户分页契约保持一致，不能让旧的服务商状态接口替代订单事件查询。顺丰接入则必须先锁定官方契约和密钥边界，再进入实现阶段。
## 10. 仓库作业工作台补齐（2026-08-16）

本轮新增仓库专用只读契约：

- `GET /api/v1/warehouse/orders`：支持 `status`、`orderNo`、`page`、`pageSize`，所有 SQL 均带当前 `tenant_id` 条件；
- `GET /api/v1/warehouse/orders/{orderId}`：返回同一租户的作业详情，跨租户或不存在统一拒绝为 `404 COMMON-1006`；
- 返回字段来自现有 `shipment_order`、`shipment_package`、`warehouse_measurement`、`fee_adjustment`、`warehouse_outbound_record`、`tenant`，包括业务单号、顺丰运单号（出库记录已有值时）、申报/实测规格、体积重、计费重量、费用差额预警、仓库状态和物流状态。

前端 `/app/warehouse` 已改为 `WarehouseWorkView.vue`，包含业务单号筛选、真实分页查询、状态 Tab、详情、DWS 复称和入库/出库按钮。五个 Tab 中“待打单/贴标”目前以禁用状态展示，因为正式 schema 尚未执行 `V013__add_provider_order_and_sf_artifacts.sql`，当前数据库没有面单/供应商订单状态字段；页面明确提示该契约缺口，不使用固定数字或模拟成功。

已完成状态映射：`PENDING_INBOUND`（待入库）、`INBOUND`（待复称/测方）、`READY_FOR_OUTBOUND`（待交接/出库）、`OUTBOUND/IN_TRANSIT`（已出库）。非法状态转换仍由后端 `warehouse:manage`、租户条件、乐观锁和幂等校验拒绝。

顺丰五个 service code 的后端适配层仍为 fail-closed：已有 `SfInternationalService`、`SfApiClient`、沙箱地址白名单和五个 service code，但因官方签名算法、报文和沙箱凭据缺失，尚未调用真实接口，也未伪造 provider 订单、面单或清关状态。完成联调前必须提供官方资料并评审 V013 迁移；本轮未执行迁移、未连接业务数据库、未调用顺丰生产接口。

本轮验证：JDK 21 隔离构建目录 `mvn -Dshipflow.build.directory=... compile` 通过；完整 Maven `test` 通过（326 tests）；仓库定向 WebMvc、Service、Mapper XML 测试通过；`npm run test:unit` 通过（27 tests）；`npm run build` 通过；`git diff --check` 通过。真实 HTTP、数据库和顺丰沙箱验收未执行。

## 11. 作业页面与顺丰操作入口（2026-08-16）

仓库作业页面已按仓库设计资料整理为完整工作台，列表字段来自 `GET /api/v1/warehouse/orders` 的真实响应：

- 业务订单号（`businessOrderNo`）、顺丰运单号（`sfTrackingNo`）、所属商户、目的国；
- 申报重量、实测长宽高/实测重量、计费重量；
- 费用差额预警、仓库状态、详情入口；
- 业务订单号查询、状态筛选、分页、加载/空数据/失败/操作成功状态。

详情页包含基础信息、DWS 复称测方、计费费用、面单/清关资料入口、交接出库和订单轨迹。入库、复称、出库仍分别调用现有 `POST /api/v1/orders/{orderId}/inbound`、`/measurements`、`/outbound`，由后端执行租户、权限、状态、乐观锁和幂等校验。当前“待打单/贴标”Tab 保持禁用，原因是 `V013__add_provider_order_and_sf_artifacts.sql` 尚未执行，不能从不存在的表中构造状态或统计数字。交接批次号和清单引用已补充到待评审的 `V014__add_warehouse_handover_references.sql`，该迁移同样未执行。

顺丰页面入口统一调用后端 `POST /api/v1/orders/{orderId}/sf-international/{operation}`，操作值对应：

| 操作 | serviceCode | 当前行为 |
| --- | --- | --- |
| 创建顺丰订单 | `COM_RECE_IUOP_CREATE_ORDER` | 后端入口和权限已接通；缺官方签名/报文时返回 `422 SF-1004`，不伪造成功 |
| 获取发货面单 | `COM_RECE_IUOP_PRINT_ORDER` | 后端入口和权限已接通；未完成 V013 持久化和官方面单契约 |
| 查询顺丰状态 | `COM_RECE_IUOP_QUERY_ORDER` | 后端入口和权限已接通；未完成官方响应字段映射 |
| 取消顺丰订单 | `COM_RECE_IUOP_CANCEL_ORDER` | 后端入口和权限已接通；状态规则和官方取消契约待确认 |
| 上传清关资料 | `COM_RECE_IUOP_UPLOAD_CERTIFY` | 后端入口和权限已接通；资料格式、存储和审计契约待确认 |

`SfProperties` 只接受受控环境变量，`SF_API_BASE_URL` 受顺丰沙箱白名单限制；`SfSignUtil` 在官方签名算法缺失时 fail-closed。前端不直连顺丰，也不展示密钥、签名、完整 `msgData` 或供应商原始响应。当前没有执行数据库迁移、真实业务库写入、顺丰沙箱/生产调用或浏览器 HTTP 验收。

## 12. 本轮新增测试

- 前端 `warehouse.spec.ts` 验证仓库作业分页/筛选和顺丰操作请求路径、幂等请求头；
- 后端 `SfInternationalServiceTest` 验证官方配置缺失和空幂等键时不发起供应商请求；
- 后端全量测试继续覆盖仓库状态机、租户隔离、Mapper SQL 和权限。

本轮实际验证：JDK 21 下 `mvn '-Dshipflow.build.directory=D:\\Projects\\shipflow\\.codex-build\\backend-target' test` 通过（336 个测试）；`npm run test:unit` 通过（28 个测试）；`npm run build` 通过；OpenAPI 路径静态检查通过；`git diff --check` 通过。未执行迁移、数据库写入、真实 HTTP 或顺丰调用。
## 2026-08-16 顺丰实现状态补充

`SfInternationalService` 已通过 `SfApiClient` 调用五个 serviceCode，并保留租户隔离、幂等键、状态机、有限重试和失败审计。创建订单从订单地址和商品明细生成稳定 JSON；面单和清关资料引用写入 `shipment_label`、`customs_document`，外部订单号、运单号和供应商状态写入 `provider_order`。前端仓库详情已启用“待打单/贴标”入口、清关测试资料字段及面单/发票引用展示，浏览器仍只调用 ShipFlow 后端。

本轮使用的新变量前缀为 `SF_OPEN_*`，代码不读取旧的 `SF_API_*` 变量。当前未执行 V013/V014，也未调用顺丰沙箱，因为当前 Codex 进程缺少所需配置变量；因此不能声称已获得真实顺丰单号、面单或轨迹。

## 2026-08-16 复称保存与顺丰创建问题修复

### 根因

1. `WarehouseWorkView.vue` 将未复称字段初始化为 `0`，没有把空值、非有限数、超限和超过 3 位小数作为页面校验错误；复称请求失败后 `useSubmit` 已吞掉异常，但页面仍继续显示成功并刷新旧详情，导致错误看起来像静默失败。
2. 页面创建顺丰按钮只检查 `READY_FOR_OUTBOUND`，没有同时检查四个实际复称字段和顺丰运单号；失败复称不会改变订单状态，因而创建按钮必须继续禁用。
3. 后端 DTO 原来只有正数校验，没有最大值和小数精度约束；顺丰服务只检查订单状态，没有再次确认租户订单存在复称记录。
4. `application.yml` 曾为 `partner-id` 和 `check-word` 提供源码默认值，并关闭严格模式。现在敏感值只能从 `SF_OPEN_PARTNER_ID`、`SF_OPEN_CHECK_WORD` 和 `SF_OPEN_CUSTOMER_CODE` 注入，缺失时返回 `422 SF-1002`，不调用供应商。

### 修改文件与规则

- 前端 `WarehouseWorkView.vue` 使用 `warehouseWorkRules.ts` 统一计算复称校验、保存按钮和顺丰创建按钮；保存成功后重新请求订单详情和列表，使用服务端最新测量数据、`logisticsStatus`、`version`。HTTP 错误优先展示后端中文 `error.message`，参数校验详情也会展示。
- 后端 `MeasurementRequest` 和 `WarehouseApplicationService` 统一使用 kg/cm、`BigDecimal`、正数、最多 3 位小数和不超过 `1,000,000` 的范围；复称继续在事务中追加 `warehouse_measurement`、记录费用调整并以 `tenant_id + order_id + version` 更新订单。
- 顺丰 `SfProviderOrderMapper.hasMeasurement` 使用租户条件检查实际复称记录；创建顺丰订单必须满足“已完成复称 + `READY_FOR_OUTBOUND` + 沙箱配置完整”，缺失配置或白名单地址不通过时 fail-closed。

### 接口请求示例

```http
POST /api/v1/orders/{orderId}/measurements
Idempotency-Key: warehouse-measurement-{request-id}
Content-Type: application/json

{"actualWeight":1.250,"actualLength":10.000,"actualWidth":20.000,"actualHeight":5.000,"version":3}
```

复称成功后，若费用不变或降低，状态为 `READY_FOR_OUTBOUND`；若费用上涨，状态为 `PENDING_PRICE_CONFIRMATION`，必须先完成商家费用确认。只有前一种状态且四个实际测量值完整时，页面才启用：

```http
POST /api/v1/orders/{orderId}/sf-international/CREATE_ORDER
Idempotency-Key: sf-create-{request-id}
Content-Type: application/json

{"msgData":""}
```

租户不匹配/订单不存在返回 `404 COMMON-1006`，状态或版本冲突返回 `409`，非法复称返回 `400 COMMON-1001`（Controller 参数校验）或 `422 WAREHOUSE-1002`（Service 直接调用），顺丰配置未完成返回 `422 SF-1002`。这些响应均由前端展示具体中文原因。

### 验证结果与外部边界

- 前端 `npm run test:unit`：31 tests passed；新增复称成功后的按钮状态、复称校验失败、保存失败后顺丰按钮保持禁用测试。
- 前端 `npm run build`：passed。
- 后端 Java 21.0.11 定向测试：28 tests passed，覆盖复称 Service/Controller、参数、状态、租户隔离、Mapper、顺丰配置和复称前创建拦截。
- 后端 Java 21.0.11 全量测试：在测试 JVM 使用 `-Dshipflow.security.jwt.enabled=false` 后 351 tests passed；未提供 JWT key 时的默认全量命令会因既有 Context 测试要求 key locations 而失败，未将该环境失败伪装成通过。
- `git diff --check`：通过（仅保留工作树既有的换行告警，无 whitespace error）。
- 只读运行检查：`GET /actuator/health` 返回 HTTP 200；未认证访问仓库概览和复称路由均返回 HTTP 401 `COMMON-1002`，确认路由已注册但未绕过认证。浏览器自动化运行时无可用浏览器实例，因此没有执行登录后的页面点击和 Network 全链路验收。
- 未执行数据库迁移、真实业务库写入和真实顺丰沙箱调用。顺丰公网 IP 白名单、官方签名契约及沙箱账号是否可用仍是外部环境前置条件，不能据此声称真实顺丰联调通过。

## 13. 运营概览页面整理（2026-08-16）

仓库操作员的“运营概览”使用 `GET /api/v1/warehouse/overview` 返回的租户范围数据，指标固定为桌面端 4×2、中等屏幕 2×4、移动端单列：

| 顺序 | 指标 | 后端字段 | 业务定义 |
| --- | --- | --- | --- |
| 1 | 待入库 | `pendingInbound` | 订单状态为 `PENDING_INBOUND` |
| 2 | 待复称 | `pendingMeasurement` | 订单已入库、等待 DWS 复称测方 |
| 3 | 待贴标/打单 | `pendingLabel` | 顺丰订单已创建，等待获取或打印面单 |
| 4 | 待交接/出库 | `pendingHandover` | 面单已就绪，等待交接出库 |
| 5 | 今日入库 | `todayInbound` | UTC 当日成功入库操作数 |
| 6 | 今日出库 | `todayOutbound` | UTC 当日成功出库操作数 |
| 7 | 在途运输 | `inTransit` | 订单状态为 `IN_TRANSIT` |
| 8 | 轨迹异常 | `trackingExceptions` | `tracking_event.process_status` 为 `RETRY` 或 `REJECTED` |

概览同时展示最近待处理订单和最近轨迹异常。待处理订单字段为业务单号、顺丰运单号、仓库状态、目的国、计费重量和当前动作；异常字段为订单号、顺丰运单号、异常时间、异常状态和最近轨迹节点。列表为空时分别显示“当前没有待处理业务”和“暂无轨迹异常”，接口失败仍显示错误状态，不伪装为空数据。

主流程由后端状态机驱动：`待入库 → 待复称 → 待贴标/打单 → 待交接/出库 → 在途运输`；轨迹异常是独立监控状态。前端仅展示 `WarehouseOverviewResponse` 字段并提供进入 `/app/warehouse`、`/app/tracking` 的入口，不自行修改订单状态。后端继续以 JWT 的 `tenant_id` 和 `warehouse:manage` 权限执行租户隔离与访问控制。

本轮复用 `WarehouseOverviewController`、`WarehouseOverviewApplicationService`、`WarehouseOverviewMapper` 及 `WarehouseOverviewMapper.xml`，并同步更新 `WarehouseOverviewResponse`、前端 `warehouse.ts`、`DashboardView.vue`、`main.css` 与 OpenAPI schema；未新增接口、表或迁移。定向后端测试 19/19、前端单元测试 35/35、前端构建和 `git diff --check` 均通过。未执行真实数据库查询、迁移、浏览器登录验收或顺丰沙箱联调。

## 14. V018/V019 人工执行后的生产库只读对账（2026-08-17）

### 14.1 核验边界与业务流程

本次只读核验针对负责人通过 Navicat 人工执行的 `V018`、`V019`。数据库为生产库 `shipflow`，MySQL 版本为 `8.0.46`。未执行 Flyway、baseline、repair、INSERT、UPDATE、DELETE、ALTER、DROP、TRUNCATE 或其他生产写操作，也未重复执行 `V018`、`V019`。

业务闭环如下：仓库人员完成复称并产生费用调整；费用上涨时进入 `PENDING_PRICE_CONFIRMATION`，商家操作员在本租户授权店铺范围内申请确认，商家管理员或财务角色完成确认。运输、清关或地址异常独立进入异常单，由责任方、处理人和追加处理记录形成过程证据；附件以同一租户、同一异常和内容 `SHA-256` 唯一去重。跨租户、跨店铺、重复附件和重复处理记录属于拒绝或幂等异常路径。

### 14.2 SQL 预期创建/修改内容对照

| 版本 | SQL 预期内容 | 租户/完整性契约 |
| --- | --- | --- |
| V018 | `fee_adjustment.confirmation_status`、`requested_by`、`requested_at` 三列；确认状态检查约束；`requested_by -> sys_user.id` 外键 | `confirmation_status` 默认 `PENDING_CONFIRMATION`，状态为 `PENDING_CONFIRMATION/REQUESTED/CONFIRMED`；金额字段仍为 `DECIMAL`；所有新增业务范围使用 `tenant_id` |
| V018 | 新建 `sys_user_store_scope`，包含 `id`、`tenant_id`、`user_id`、`store_id`、`status`、UTC 时间字段 | `(tenant_id,user_id,store_id)` 唯一；用户、店铺、租户外键；`status` 为 `ACTIVE/DISABLED`；用户和店铺查询索引按租户隔离 |
| V018 | 新增 `order:price-request` 权限，并重建商家操作员、商家管理员、财务角色的价格申请/确认绑定 | 操作员只申请，商家管理员和财务角色确认；绑定对象必须是租户角色且有效 |
| V019 | `exception_case.responsible_party`；扩展 `chk_exception_status`，增加 `WAITING_PROVIDER_FEEDBACK`、`PENDING_FINANCE_CONFIRMATION` | 责任方限定为 `MERCHANT/PROVIDER/CUSTOMS/CUSTOMER/OTHER`，允许为空；异常单仍按租户和订单关联 |
| V019 | 新建 `exception_handling_record`，保存处理人、记录类型、处理内容和 UTC 创建时间 | `(tenant_id,record_no)` 唯一；处理记录关联租户、异常单和用户；按租户/异常/时间查询 |
| V019 | 新建 `exception_evidence_attachment`，保存文件元数据、内容本体和 `content_sha256` | `(tenant_id,exception_case_id,content_sha256)` 唯一；关联租户、异常单、上传人；`file_size > 0` |

### 14.3 数据库实际已存在内容对照

| 版本 | 数据库实际内容 | 只读证据 |
| --- | --- | --- |
| V018 | `fee_adjustment` 已有三列：`VARCHAR(32) NOT NULL DEFAULT 'PENDING_CONFIRMATION'`、`BIGINT NULL`、`DATETIME(3) NULL`；确认状态检查约束和 `fk_adjustment_requested_by` 已存在 | `SHOW CREATE TABLE fee_adjustment`、`information_schema.columns`、`table_constraints`、`check_constraints` |
| V018 | `sys_user_store_scope` 已存在，7 列、主键、三列组合唯一键、用户/店铺索引、三条外键和状态检查约束均存在；表为 InnoDB、`utf8mb4_0900_ai_ci` | `SHOW CREATE TABLE sys_user_store_scope` 及 `information_schema` 元数据 |
| V018 | `order:price-request` 和 `order:price-confirm` 各存在 1 条权限定义；有效绑定方向符合预期：`MERCHANT_OPERATOR -> request`，`MERCHANT_ADMIN/FINANCE_OPERATOR -> confirm` | `sys_permission`、`sys_role_permission` 只读聚合查询 |
| V019 | `exception_case.responsible_party` 已存在，类型为 `VARCHAR(32) NULL`；扩展状态检查和责任方检查均存在 | `SHOW CREATE TABLE exception_case`、检查约束元数据 |
| V019 | `exception_handling_record` 已存在，8 列、主键、租户/记录编号唯一键、查询索引、三条外键和记录类型检查约束均存在 | `SHOW CREATE TABLE exception_handling_record` |
| V019 | `exception_evidence_attachment` 已存在，10 列、`content_sha256 CHAR(64)`、`content_blob LONGBLOB`、唯一去重键、查询索引、三条外键和文件大小检查约束均存在 | `SHOW CREATE TABLE exception_evidence_attachment` |

### 14.4 差异结论与发现

在已核对的列类型、可空性、默认值、时间精度、表引擎、字符集/排序规则、主键、唯一键、普通索引、外键和检查约束范围内，数据库实际结构与 `V018`、`V019` SQL 预期结构一致；结构结论为“完全一致（针对本次 SQL 可观察结构契约）”。金额相关字段仍使用 `DECIMAL(18,2)`，没有发现用浮点数替代金额的差异。

未发现 `V018` 的权限定义缺失，但生产权限数据存在历史重复角色实例：只读聚合显示 `MERCHANT_OPERATOR` 有 21 条 `order:price-request` 绑定，`MERCHANT_ADMIN` 有 24 条、`FINANCE_OPERATOR` 有 21 条 `order:price-confirm` 绑定。该问题不是表结构差异，本次没有删除或更新权限数据；后续应由负责人单独确认角色实例治理方案。

### 14.5 Flyway 配置与历史状态

- 当前应用配置 `backend/src/main/resources/application.yml` 明确设置 `spring.flyway.enabled=false`；本地示例配置同样关闭 Flyway。
- `backend/pom.xml` 仍依赖 `flyway-core` 和 `flyway-mysql`，但没有发现自定义 Flyway 配置。
- 未发现自定义 `table`、`locations`、`baselineOnMigrate`、`validateOnMigrate` 或 `clean` 配置。当前历史表实际使用 Flyway 默认表名 `flyway_schema_history`；迁移 SQL 位于仓库 `database/migrations`，不能据当前配置断言它会被 Spring Boot 默认 classpath 迁移位置自动加载。
- `flyway_schema_history` 中最新成功版本为 `017`；没有 `018`、`019` 的成功记录，也没有对应版本的失败记录。数据库对象已存在不能替代 Flyway 历史登记。

### 14.6 历史对账方案

推荐方案：**B. 生成经过人工审核的历史补登记方案**。

理由：`V018`、`V019` 已由 Navicat 人工执行且结构已经存在；`baseline` 适合为既有整体结构建立基线，不能准确表达两个已经发生的独立版本，`repair` 主要修复已有 history 行的校验信息，不能安全补造缺失版本行；方案 C 会保留 `017 -> 后续版本` 的历史缺口，继续扩大结构与迁移历史的偏差。补登记必须由负责人审核版本号、描述、脚本摘要、执行顺序和当前结构证据后，使用受控 Flyway 管理动作完成；本次只生成审核 SQL 和说明，不执行任何补登记。

### 14.7 后端启动判断与待确认操作

在当前 `spring.flyway.enabled=false` 配置下，V018/V019 所需表结构已存在，因此从数据库结构兼容性看可以启动后端；但这不代表 Flyway 治理已完成。启动前仍需确认正常运行所需的 JWT、数据库连接、外部服务和其他环境变量；不得在未解决历史对账前打开生产 Flyway 自动迁移。

需要负责人明确确认的数据库操作：

1. 是否批准方案 B，并审核 `V018`、`V019` 的脚本 checksum、描述、人工执行证据和当前结构快照。
2. 审核通过后，选择由受控 Flyway 工具完成缺失历史的正式补登记，还是由 DBA 按批准的补登记 runbook 执行；任何动作必须先做生产备份/回滚预案并再次确认 `SELECT DATABASE()` 为 `shipflow`。
3. 是否单独治理三类租户角色的重复实例及其权限绑定；本次核验不建议把权限重复清理混入迁移历史对账。
4. 是否在未来版本启用 Flyway 校验，并明确迁移位置、history 表名和 `validateOnMigrate` 策略；在策略确认前保持当前 Flyway 关闭状态。

本节只记录只读核验结果，未提交、未推送 Git；V018/V019 仍未重复执行。

## 15. V018/V019 Flyway 历史补登记方案（2026-08-17）

继续采用方案 B，但本轮只生成最终审核包，不连接生产库执行写操作。文件为 [`docs/database/flyway-history-reconciliation-v018-v019.sql`](../database/flyway-history-reconciliation-v018-v019.sql)，审核说明为 [`docs/database/flyway-history-reconciliation-v018-v019.md`](../database/flyway-history-reconciliation-v018-v019.md)。SQL 不包含 V018/V019 迁移正文，唯一业务数据写入目标是 `flyway_schema_history`；不会创建、修改或删除任何业务表，也不会删除业务数据。

### 15.1 已确认的 Flyway 历史契约

- history 表名：`flyway_schema_history`。
- 字段：`installed_rank INT`、`version VARCHAR(50)`、`description VARCHAR(200)`、`type VARCHAR(20)`、`script VARCHAR(1000)`、`checksum INT`、`installed_by VARCHAR(100)`、`installed_on TIMESTAMP`、`execution_time INT`、`success TINYINT(1)`。
- 当前版本格式为三位字符串：`'017'`；当前最大 `installed_rank=6`，前置版本 V017 成功。
- Flyway 依赖版本由 Spring Boot 依赖管理解析为 `11.7.2`。官方 checksum 实现按 UTF-8、去 BOM、逐行去除换行后计算 CRC-32；用数据库已登记的 V013–V017 交叉验证通过。

### 15.2 V018/V019 登记值与顺序

| installed_rank | version | description | type | script | checksum |
| ---: | --- | --- | --- | --- | ---: |
| 7 | `018` | `add order price confirmation scope` | `SQL` | `V018__add_order_price_confirmation_scope.sql` | `1587549683` |
| 8 | `019` | `add exception processing evidence` | `SQL` | `V019__add_exception_processing_evidence.sql` | `-85667777` |

checksum 是基于当前工作区迁移文件、Flyway 兼容算法计算的 signed `INT` 值；执行前 DBA 必须用人工执行时留存的原始脚本或证据再次确认，不能仅凭当前工作区文件替代历史凭证。

### 15.3 SQL 执行顺序与风险

1. DBA 在隔离环境审阅 SQL 文件及 checksum，确认文件未被加入迁移正文、目标库和账号均正确。
2. 完成批准的生产备份，并保存 history 表只读快照。
3. 执行只读预检查，必须确认 `DATABASE()='shipflow'`、V018/V019 不存在、V017 成功且最大 `installed_rank=6`；任一 gate 非 `PASS` 即停止。
4. 对照 version、description、script 和 signed checksum；任何 checksum 不一致都禁止执行。
5. 在受控事务中调用带 `SIGNAL`/异常回滚的临时管理过程，唯一业务数据写入是两条 history 记录；不执行迁移正文。
6. 只有 `registered_count=2`、`v018_valid_count=1`、`v019_valid_count=1` 时，才由 DBA 人工确认 `COMMIT`；否则由异常处理或人工操作 `ROLLBACK` 并停止。
7. 删除临时管理过程，再次执行只读核验和 Flyway `validate` 评估；禁止打开生产自动迁移，除非另有审批。

主要风险是历史 checksum 或人工执行时的 description 与当前文件不一致，以及并发变更导致 `installed_rank` 前置条件失效。脚本不重复执行迁移正文，但 history 补登记本身仍是生产数据库写操作，必须由负责人和 DBA 明确批准后执行。

### 15.4 回滚说明

本轮不自动执行回滚。若补登记后发现 checksum、描述或执行顺序不正确，必须先暂停后续 Flyway 活动，确认备份和审批，再由 DBA 仅针对精确的 V018/V019 history 行制定 history-only 回滚；不得用 `DROP/ALTER` 回滚已存在的业务结构，不得删除业务数据。回滚后必须重新执行只读 history 与结构核验。SQL 文件只提供人工回滚说明，不自动执行回滚。

本轮未连接生产库执行写操作，未执行补登记、迁移、baseline、repair，未提交、未推送 Git。

## 16. V018/V019 Navicat 人工审核材料（2026-08-17）

基于当前生产库只读结果，已打开并核对原审核 SQL，并生成 Navicat 专用完整副本与中文说明：

- [`docs/database/flyway-history-reconciliation-v018-v019-navicat.sql`](../database/flyway-history-reconciliation-v018-v019-navicat.sql)
- [`docs/database/flyway-history-reconciliation-v018-v019-navicat.md`](../database/flyway-history-reconciliation-v018-v019-navicat.md)

材料确认 history 表为 `flyway_schema_history`，最新成功版本为 V017；V018 checksum 为 `1587549683`，V019 checksum 为 `-85667777`。SQL 只补登记 V018/V019，不执行迁移正文，不修改业务表。checksum 或前置版本任一不匹配时，临时过程通过 `SIGNAL` 触发异常处理并回滚；SQL 不自动连接数据库、不自动执行、不自动 `COMMIT`，并明确了 Navicat 的人工执行、核验、清理和回滚边界。

本轮未连接生产库、未执行 SQL、未执行 `COMMIT`/`ROLLBACK`、未重复执行 V018/V019，未提交、未推送 Git。

## 17. 第三阶段开始前验收（2026-08-17）

### 17.1 V018/V019 历史补登记结论

V018、V019 已由负责人通过 Navicat 人工执行，并已完成 Flyway 历史补登记；本轮没有再次执行两份迁移正文。生产库只读复核结果如下：

| 数据库 | MySQL | installed_rank | version | success | checksum |
| --- | --- | ---: | --- | ---: | ---: |
| `shipflow` | `8.0.46` | 6 | `017` | 1 | `-1705736465` |
| `shipflow` | `8.0.46` | 7 | `018` | 1 | `1587549683` |
| `shipflow` | `8.0.46` | 8 | `019` | 1 | `-85667777` |

V017、V018、V019 顺序连续，成功数为 3，且 history 表为 `flyway_schema_history`。启动前后只读查询结果一致；本轮没有执行 `INSERT`、`UPDATE`、`DELETE`、`ALTER`、`DROP`、`TRUNCATE`、`COMMIT`、`ROLLBACK`、`baseline` 或 `repair`。

### 17.2 生产库后端启动验证

在已确认的生产数据库 `shipflow` 上，以独立端口 `18080` 启动当前代码实例。为避免生产业务写入，启动参数明确关闭 `SF_OPEN_SANDBOX_ENABLED`；应用配置中的 `spring.flyway.enabled=false` 保持关闭。启动日志出现 `Started ShipFlowApplication`，未出现 Flyway、V018、V019 或迁移尝试记录。

验证结果：

- `GET /actuator/health`：HTTP 200；
- `GET /v3/api-docs`：HTTP 200；
- 未认证 `GET /api/v1/exceptions`：HTTP 401，证明异常路由已注册且仍受认证保护；
- 运行时 OpenAPI 共 88 个路径，异常模块 9 个路径全部存在；静态 `openapi/shipflow-api.yaml` 的异常路径 9/9 存在；
- 临时 Vite 服务访问 `/app/exceptions` 返回 HTTP 200，应用入口 HTML 正常加载；
- 本轮启动实例已清理，未终止既有 8080 进程。

启动过程中发现默认环境未提供 JWT PEM 路径和 refresh token 配置；使用仓库既有本地 JWT PEM 与测试配置中的非生产 HMAC 配置仅作为本次启动验证参数，未写入仓库、未输出密钥。生产正式启动前必须由负责人/运维注入经过批准的生产 JWT 与 refresh token 配置。

### 17.3 真实检查结果

- 后端：JDK `21.0.11`，隔离构建目录执行 Maven 全量测试，`376` tests，Failures `0`，Errors `0`，Skipped `0`；
- 前端：`npm run test:unit`，13 个测试文件、46/46 tests 通过；
- 前端：`npm run build` 通过，Vite 完成生产构建；
- OpenAPI：静态异常路径 9/9、运行时异常路径 9/9 通过；
- `git diff --check`：通过，无 whitespace error（仅有 Git 的换行格式提示）；
- 未提交、未推送 Git，既有 dirty worktree 未清理或重置。

### 17.4 第三阶段准入结论与遗留问题

**验收结论：V018/V019 已完成人工执行和 Flyway 历史补登记，生产库结构与 history 连续性通过只读核验；后端可在保持 Flyway 关闭、顺丰沙箱关闭并补齐正式安全配置的前提下启动。第三阶段可以开始。**

第三阶段按既定顺序进入物流产品与报价、订单履约、顺丰物流交付及商家业务员企业级工作台完善。启动正式生产实例前仍需确认：

1. 生产 JWT 私钥/公钥、refresh token HMAC 等安全配置已通过受控环境注入；
2. Flyway 后续是否启用及 `validateOnMigrate`、迁移 locations 和 history 治理策略已单独审批；
3. 顺丰正式/沙箱账号、签名资料、白名单和真实联调边界已确认；
4. 既有租户角色实例的历史重复权限绑定仍需单独治理，不纳入本次历史补登记。
