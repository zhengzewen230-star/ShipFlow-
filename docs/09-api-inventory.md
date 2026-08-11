# ShipFlow API 接口清单

## 1. 通用字段约定

除登录、刷新 Token 和退出登录外，接口默认需要 `Authorization: Bearer <JWT>`、`X-Request-Id`（可选）和响应 `X-Trace-Id`。登录、刷新和退出需要 `XSRF-TOKEN` Cookie 与 `X-XSRF-TOKEN`；刷新和退出还需要 Refresh Cookie；租户业务接口的 `tenant_id` 从 JWT 读取，不允许请求体、查询参数或普通 Header 覆盖。

表中“请求体”只列业务字段；统一响应包装为 `ApiSuccess<T>`，错误使用 `ApiError`。分页接口使用 `page`、`pageSize`、`sortBy`、`sortDirection` 及白名单筛选字段。所有写接口的审计和幂等规则以 `08-api-conventions.md` 为准。

## 2. 接口总览

| 模块 | 接口数量 |
|---|---:|
| 认证与当前用户 | 5 |
| 平台租户管理 | 5 |
| 租户用户管理 | 6 |
| 店铺管理 | 5 |
| RBAC 查询与绑定 | 4 |
| 物流商、渠道与价格规则 | 10 |
| 运费询价与报价 | 8 |
| 物流订单与运输资料 | 12 |
| 仓库履约 | 8 |
| 物流轨迹 | 6 |
| 物流商账单 | 5 |
| 费用对账 | 3 |
| 异常件与索赔 | 10 |
| 审计查询 | 4 |
| 运营看板 | 2 |
| **目标总计** | **93** |

> ADR-021 原将总目标冻结为 90 个 operation；异常/索赔、账单/对账和审计查询按实际接口扩展后，当前目标为 93。表中后续模块数量是实施目标，不代表均已实现。

## 3. 认证与当前用户

| 编号/用途 | 方法 URL | 角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| AUTH-001 获取CSRF | `GET /api/v1/auth/csrf` | 匿名；无请求体 | 设置可读 `XSRF-TOKEN` Cookie；不返回 Refresh Token；`Cache-Control: no-store` | `COMMON-1008` | 匿名；不需要 Access Token | 只读/不使用通用缓存 | 正常获取、重复获取、Cookie属性 |
| AUTH-002 登录 | `POST /api/v1/auth/login` | 匿名；`XSRF-TOKEN` Cookie、`X-XSRF-TOKEN`；JSON `username`、`password`、可选 `tenantCode` | JSON `accessToken`、`expiresIn`；Set-Cookie Refresh；`Cache-Control: no-store` | `AUTH-1001`、`AUTH-1005`、`COMMON-1008` | 匿名；不需要 Access Token | 不使用通用幂等缓存/记录登录审计/`sys_user`、`audit_log` | 正常、CSRF错误、密码错误、用户不存在、tenantCode错误、禁用用户/租户、系统账号 |
| AUTH-003 刷新 Token | `POST /api/v1/auth/refresh` | 匿名；HttpOnly Refresh Cookie、`XSRF-TOKEN` Cookie、`X-XSRF-TOKEN` | JSON 新 `accessToken`、`expiresIn`；Set-Cookie轮换；`Cache-Control: no-store` | `AUTH-1002`、`AUTH-1003`、`AUTH-1005`、`COMMON-1008` | 不需要 Access Token；需要 Cookie 和 CSRF | 不使用通用缓存；客户端必须串行刷新/记录审计/`auth_refresh_session` |
| AUTH-004 退出登录 | `POST /api/v1/auth/logout` | Refresh Cookie、`XSRF-TOKEN` Cookie、`X-XSRF-TOKEN`；Access Token可选仅用于审计 | 清除 Refresh/XSRF Cookie；幂等成功；`Cache-Control: no-store` | `AUTH-1005`、`COMMON-1008` | 不要求 `auth:logout`；只能由 Refresh Cookie 定位 family | 撤销当前family/记录审计/不使用通用缓存 |
| AUTH-005 当前用户 | `GET /api/v1/users/me` | `Authorization: Bearer` | 用户、角色、权限、租户摘要 | `COMMON-1002`、`COMMON-1003` | 需要 Access Token | 只读/不写审计或仅记录安全访问/`sys_user`、`sys_role`、`sys_permission` | 平台用户、租户用户、权限边界 |

## 4. 第二阶段统一安全约定

- 以下所有接口均要求 Bearer Access Token；所有写接口还要求认证模块既有的 CSRF 双提交校验。
- 请求中的 `tenantId` 只能出现在平台管理资源路径中；租户资源一律从实时重载的当前身份取得 `tenant_id`，不得由 body、query 或 Header 覆盖。
- 对租户资源，资源不存在、已删除或不属于当前租户统一返回 `COMMON-1006`（404）；调用者已通过资源归属判断但没有动作权限时返回 `COMMON-1004`（403）。
- 每次请求都重载用户、租户、角色和权限有效性；已停用的用户、租户或角色立即拒绝。前端菜单隐藏不是授权控制。
- `PLATFORM_ADMIN` 只拥有平台作用域；`MERCHANT_ADMIN`、`MERCHANT_OPERATOR`、`WAREHOUSE_OPERATOR`、`FINANCE_OPERATOR` 只拥有本租户作用域；`MOCK_LOGISTICS_SYSTEM` 不可调用本模块管理接口。

## 5. 平台租户管理

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| TENANT-001 创建租户 | `POST /api/v1/platform/tenants` | `PLATFORM_ADMIN`；`tenant:create`；平台作用域 | `tenantCode`、`tenantName`、`initialAdmin`（含 `temporaryPassword`） | `201` 租户和初始管理员摘要 | `TENANT-1001`、`USER-1001`、`COMMON-1001` | `Idempotency-Key`幂等/单事务/写审计/`tenant`、`sys_user`、`sys_role`、`sys_user_role` |
| TENANT-002 查询租户 | `GET /api/v1/platform/tenants` | `PLATFORM_ADMIN`；`tenant:read`；平台作用域 | 分页、`status`、`tenantCode` | 分页租户列表 | `COMMON-1002`、`COMMON-1004` | 只读/`tenant` |
| TENANT-003 查询租户详情 | `GET /api/v1/platform/tenants/{tenantId}` | `PLATFORM_ADMIN`；`tenant:read`；平台作用域 | `tenantId` | 租户详情 | `COMMON-1006`、`COMMON-1004` | 只读/`tenant` |
| TENANT-004 修改租户 | `PUT /api/v1/platform/tenants/{tenantId}` | `PLATFORM_ADMIN`；`tenant:manage`；平台作用域 | `tenantName`、`version` | 更新后租户 | `TENANT-1002`、`COMMON-1005` | `Idempotency-Key`幂等/乐观锁/写审计/`tenant` |
| TENANT-005 启用/停用租户 | `POST /api/v1/platform/tenants/{tenantId}/status` | `PLATFORM_ADMIN`；`tenant:manage`；平台作用域 | `status`、`version` | 新状态和版本 | `TENANT-1002`、`COMMON-1005` | 非幂等/乐观锁/写审计/`tenant` |

## 6. 租户用户管理

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| USER-001 创建用户 | `POST /api/v1/users` | `MERCHANT_ADMIN`；`user:manage`；当前租户 | `username`、`displayName`、`temporaryPassword`、`roleIds` | `201` 用户摘要 | `USER-1001`、`USER-1003`、`COMMON-1001` | `Idempotency-Key`幂等/单事务/写审计/`sys_user`、`sys_user_role` |
| USER-002 查询用户 | `GET /api/v1/users` | `MERCHANT_ADMIN`；`user:manage`；当前租户 | 分页、`status`、`username` | 分页用户列表 | `COMMON-1004` | 只读/`sys_user` |
| USER-003 查询用户详情 | `GET /api/v1/users/{userId}` | `MERCHANT_ADMIN`；`user:manage`；当前租户 | `userId` | 用户与角色摘要 | `COMMON-1006`、`COMMON-1004` | 只读/`sys_user`、`sys_user_role` |
| USER-004 修改用户 | `PUT /api/v1/users/{userId}` | `MERCHANT_ADMIN`；`user:manage`；当前租户 | `displayName`、`version` | 更新后用户 | `USER-1003`、`COMMON-1005` | `Idempotency-Key`幂等/乐观锁/写审计/`sys_user` |
| USER-005 启用/停用用户 | `POST /api/v1/users/{userId}/status` | `MERCHANT_ADMIN`；`user:manage`；当前租户 | `status`、`version` | 新状态和版本 | `USER-1002`、`COMMON-1005`、`COMMON-1006` | 非幂等/乐观锁/写审计/`sys_user` |
| USER-006 分配角色 | `PUT /api/v1/users/{userId}/roles` | `MERCHANT_ADMIN`；`user:manage`；当前租户 | `roleIds`、`version` | 用户角色列表 | `USER-1003`、`ROLE-1002`、`COMMON-1005`、`COMMON-1006` | 单事务/乐观锁/写审计/`sys_user`、`sys_user_role` |

## 7. 店铺管理

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| STORE-001 创建店铺 | `POST /api/v1/stores` | `MERCHANT_ADMIN`；`store:manage`；当前租户 | `storeCode`、`storeName`、`platformCode`、`platformAccount` | `201` 店铺摘要 | `STORE-1001`、`COMMON-1001` | `Idempotency-Key`幂等/写审计/`merchant_store` |
| STORE-002 查询店铺 | `GET /api/v1/stores` | `MERCHANT_ADMIN`或`MERCHANT_OPERATOR`；`store:read`；当前租户 | `status`、`platformCode` | 店铺列表 | `COMMON-1004` | 只读/`merchant_store` |
| STORE-003 查询店铺详情 | `GET /api/v1/stores/{storeId}` | `MERCHANT_ADMIN`或`MERCHANT_OPERATOR`；`store:read`；当前租户 | `storeId` | 店铺详情 | `COMMON-1006`、`COMMON-1004` | 只读/`merchant_store`；跨租户404 |
| STORE-004 修改店铺 | `PUT /api/v1/stores/{storeId}` | `MERCHANT_ADMIN`；`store:manage`；当前租户 | `storeName`、`platformCode`、`platformAccount`、`version` | 更新后店铺 | `STORE-1001`、`COMMON-1005`、`COMMON-1006` | `Idempotency-Key`幂等/乐观锁/写审计/`merchant_store` |
| STORE-005 启用/停用店铺 | `POST /api/v1/stores/{storeId}/status` | `MERCHANT_ADMIN`；`store:manage`；当前租户 | `status`、`version` | 新状态和版本 | `STORE-1002`、`COMMON-1005`、`COMMON-1006` | 非幂等/乐观锁/写审计/`merchant_store` |

## 8. RBAC 查询与绑定

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 |
|---|---|---|---|---|---|---|
| RBAC-001 查询角色 | `GET /api/v1/roles` | `MERCHANT_ADMIN`；`role:read`；当前租户 | 分页、`status` | 仅当前租户 TENANT 角色及权限 | `COMMON-1004` | 只读/`sys_role`、`sys_role_permission`、`sys_permission` |
| RBAC-002 查询角色详情 | `GET /api/v1/roles/{roleId}` | `MERCHANT_ADMIN`；`role:read`；当前租户 | `roleId` | 当前租户角色和权限 | `COMMON-1006`、`COMMON-1004` | 只读；跨租户404 |
| RBAC-003 查询权限 | `GET /api/v1/permissions` | `MERCHANT_ADMIN`；`permission:read`；当前租户 | 无 | 平台公共权限字典 | `COMMON-1004` | 只读/`sys_permission` |
| RBAC-004 绑定角色权限 | `PUT /api/v1/roles/{roleId}/permissions` | `MERCHANT_ADMIN`；`role:manage`；当前租户 | `permissionIds`、`version` | 更新后角色和权限 | `ROLE-1002`、`COMMON-1005`、`COMMON-1006` | 单事务/乐观锁/写审计/`sys_role`、`sys_role_permission` |

## 7. 物流渠道查询

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| CHANNEL-001 查询可用渠道 | `GET /api/v1/logistics/channels` | 租户用户；查询国家 | `countryCode`、`status=ACTIVE` | 渠道公开列表 | `COMMON-1001` | 只读/`logistics_channel`、`logistics_channel_service_country` |
| CHANNEL-002 查询服务国家 | `GET /api/v1/logistics/channels/{channelId}/service-countries` | 租户用户 | `channelId` | 国家编码列表 | `COMMON-1006` | 只读/`logistics_channel_service_country` |
| CHANNEL-003 查询渠道公开信息 | `GET /api/v1/logistics/channels/{channelId}` | 租户用户 | `channelId` | 渠道、物流商和价格规则摘要 | `COMMON-1006` | 只读/`logistics_provider`、`logistics_channel`、`price_rule` |
| CHANNEL-004 查询/维护价格规则 | `GET/POST /api/v1/platform/logistics-channels/{channelId}/price-rules`、`GET /api/v1/platform/logistics-channels/{channelId}/price-rules/{priceRuleId}` | 平台管理员；创建请求含 `Idempotency-Key` | `versionNo`、计费参数、tiers | 已发布版本及阶梯；`201` 新版本 | `LOGISTICS-1004`、`LOGISTICS-1005`、`COMMON-1005` | 发布即不可修改；新版本唯一；写审计/`price_rule`、`price_rule_tier` |

## 8. 运费报价

报价列表、详情和有效性校验均以当前调用者的 `tenant_id` 作为强制查询条件；报价金额与规则版本为不可变快照。

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| QUOTE-001 创建报价 | `POST /api/v1/quotes` | 当前租户用户；`Idempotency-Key` | `storeId`、`channelId`、申报重量尺寸、目的国 | `201` 不可变报价、计费重量、金额和 `feeDetail`；UTC 有效期 30 分钟 | `QUOTE-1001`、`QUOTE-1002`、`QUOTE-1006`、`COMMON-1009` | 同 key 同请求返回首次报价；写审计/`quote`、`price_rule`、`api_idempotency_record` |
| QUOTE-002 查询报价详情 | `GET /api/v1/quotes/{quoteId}` | 当前租户用户 | `quoteId` | 报价详情和费用明细 | `COMMON-1006` | 只读/`quote`；跨租户统一404 |
| QUOTE-003 查询可用报价 | `GET /api/v1/quotes` | 当前租户用户；分页 | `storeId`、`channelId`、`status` | 分页报价 | `COMMON-1001` | 只读/`quote` |
| QUOTE-004 校验报价有效性 | `POST /api/v1/quotes/{quoteId}/validate` | 当前租户用户 | `quoteId` | `valid`、失效原因 | `QUOTE-1003`、`QUOTE-1004` | 只读校验/不写业务审计/`quote`、`shipment_order` |

## 9. 物流订单

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| ORDER-001 基于报价创建订单 | `POST /api/v1/quotes/{quoteId}/shipment-orders` | 当前租户用户；必须 `Idempotency-Key` | `quoteId`、寄/收件地址、商品；包裹尺寸重量从报价冻结 | `201` DRAFT订单、费用与计费重量 | `ORDER-1003`、`QUOTE-1003`、`QUOTE-1004`、`COMMON-1009` | 同 key 返回原订单；不同 key 重复使用报价冲突；事务写审计、订单及不可变快照 |
| ORDER-002/003 查询订单 | `GET /api/v1/orders/{orderId}`、`GET /api/v1/orders` | 当前租户用户 | 订单号、状态、店铺、分页 | 订单详情或分页列表 | `COMMON-1001`、`COMMON-1006` | 只读/租户隔离 |
| ORDER-004 DRAFT 修改资料 | `PUT /api/v1/orders/{orderId}` | 当前租户用户 | 地址联系人、商品申报、备注、version | 更新后订单 | `ORDER-1001`、`ORDER-1007` | 仅 DRAFT；计费字段和报价快照不可变，写审计 |
| ORDER-003 分页查询订单 | `GET /api/v1/orders` | 当前租户用户；分页 | `status`、`orderNo`、时间范围、店铺 | 分页订单 | `COMMON-1001` | 只读/`shipment_order` |
| ORDER-004 提交订单 | `POST /api/v1/orders/{orderId}/submit` | 商家管理员、商家操作员；JSON `version` | `version` | `PENDING_INBOUND`订单 | `ORDER-1001`、`COMMON-1005` | 幂等/写审计/`shipment_order` |
| ORDER-005 取消订单 | `POST /api/v1/orders/{orderId}/cancel` | 商家管理员、商家操作员；JSON `version` | `reason`、`version` | `CANCELLED`订单 | `ORDER-1001`、`COMMON-1005` | 幂等/写审计/`shipment_order`、`audit_log` |
| ORDER-006 确认新费用 | `POST /api/v1/orders/{orderId}/price-confirmation` | 商家管理员、商家操作员；JSON `feeAdjustmentId`、`expectedFee`、`version` | `feeAdjustmentId`、`expectedFee`、`version`；服务端校验调整属于当前订单/租户、类型为INCREASE且未确认，并校验expectedFee等于after_amount | `READY_FOR_OUTBOUND`订单、更新`currentFee`和`confirmedFee` | `ORDER-1005`、`COMMON-1005` | 幂等/写审计/`shipment_order`、`fee_adjustment` |

## 10. 仓库操作

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| WAREHOUSE-001 确认入库 | `POST /api/v1/orders/{orderId}/inbound` | 仓库人员；JSON `version` | `version`、`inboundAt` | `INBOUND`订单 | `WAREHOUSE-1001`、`COMMON-1005` | 幂等/写审计/`shipment_order`、`shipment_package` |
| WAREHOUSE-002 提交复称 | `POST /api/v1/orders/{orderId}/measurements` | 仓库人员；JSON `version`必填 | 实际重量、尺寸、`measuredAt`、`version`；输入统一转换为kg/cm | 复称结果、费用变化、下一状态 | `WAREHOUSE-1002`、`WAREHOUSE-1003` | `Idempotency-Key`幂等/写审计/`warehouse_measurement`、`fee_adjustment`、`shipment_order` |
| WAREHOUSE-003 查询复称历史 | `GET /api/v1/orders/{orderId}/measurements` | 仓库人员、商家用户 | `orderId` | 复称列表 | `COMMON-1006` | 只读/`warehouse_measurement` |
| WAREHOUSE-004 确认出库 | `POST /api/v1/orders/{orderId}/outbound` | 仓库人员；JSON `version` | `trackingNo`、`outboundAt`、备注、`version`；provider由订单`channelId`推导 | `OUTBOUND`订单和出库记录 | `WAREHOUSE-1004`、`WAREHOUSE-1005`、`COMMON-1005` | 幂等/写审计/`shipment_order`、`warehouse_outbound_record` |
| WAREHOUSE-005 查询出库记录 | `GET /api/v1/orders/{orderId}/outbound` | 当前租户用户 | `orderId` | 出库交接记录 | `COMMON-1006` | 只读/`warehouse_outbound_record` |

## 11. 物流轨迹

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| TRACK-001 Mock回调 | `POST /api/v1/integrations/logistics/{providerCode}/tracking-events` | 应用安全配置绑定的 Mock 系统账号；`X-Provider-Signature`、`X-Provider-Timestamp`；实时要求 `tracking:callback` | 事件数组：`trackingNo`、`eventId`、`eventCode`、`eventTime`、原始报文；事件时间转 UTC；事件码映射见 OpenAPI | `202` 接收结果和每事件 `ACCEPTED`/`DUPLICATE`；重复事件仍为成功 | `COMMON-1001`、`COMMON-1004`、`TRACK-1002`、`TRACK-1003`、`TRACK-1004`、`TRACK-1005` | `(provider_id,tracking_no,event_id)` 幂等；首次成功处理写审计；乱序只保存不回退；`tracking_event`、`shipment_order`、`audit_log` |
| TRACK-002 查询订单轨迹 | `GET /api/v1/orders/{orderId}/tracking-events` | 当前租户用户 | `orderId`、分页 | 按 `eventTime` 排序的轨迹 | `COMMON-1006` | 只读/`tracking_event` |
| TRACK-003 轨迹查询 | `GET /api/v1/shipment-orders/{orderId}/tracking`、`/tracking/status` | 当前租户 JWT | `orderId` | 升序轨迹、当前状态和最新摘要 | `COMMON-1006` | 只读；按 tenant_id 隔离；不写审计日志 |
| TRACK-003 查询回调异常 | `GET /api/v1/platform/tracking-events/errors` | 平台管理员、Mock系统账号 | `providerId`、`processStatus`、分页 | 异常事件列表 | `COMMON-1004` | 只读/`tracking_event` |
| TRACK-004 重试异常回调 | `POST /api/v1/platform/tracking-events/{eventId}/retry` | 平台管理员、Mock系统账号；JSON `version` | `reason`、`version` | 重试受理结果 | `TRACK-1003`、`COMMON-1005` | 幂等/写审计/`tracking_event`、`audit_log` |

## 12. 账单导入

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| BILL-001 导入模拟 CSV | `POST /api/v1/billing/import-batches` | `scope:TENANT` + `finance:bill-import`；multipart 内存文件 | `providerId`；文件表头为 `provider_bill_detail_no,tracking_no,billed_amount,currency,fee_type` | `202` 已完成批次与统计 | `BILL-1001`、`BILL-1003`、`BILL-1004`、`BILL-1005` | `tenant_id + provider_id + file_hash` 返回原批次；逐行不落真实文件；写审计 |
| BILL-002 查询批次及结果 | `GET /api/v1/billing/import-batches`、`GET /api/v1/billing/import-batches/{batchId}`、`GET /api/v1/billing/import-batches/{batchId}/errors` | 财务人员 | 物流商、批次、状态、分页 | 批次、成功/失败统计与可保存的错误行 | `COMMON-1001`、`COMMON-1006` | 只读；全部 SQL 带 `tenant_id`；`bill_import_batch`、`bill_detail` |
| BILL-003 查询账单明细 | `GET /api/v1/billing/details` | 财务人员 | `batchId`、`status`、分页 | 账单明细分页 | `COMMON-1001`、`COMMON-1006` | 只读；不修改原始账单明细 |

## 13. 费用对账

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| RECON-001 自动生成与查询 | 导入时自动生成；`GET /api/v1/reconciliations`、`GET /api/v1/reconciliations/{reconciliationId}` | `scope:TENANT` + `finance:reconcile` | 订单、状态、分页 | 对账记录及详情 | `COMMON-1001`、`COMMON-1006` | `system_amount = shipment_order.current_fee`；相等 `AUTO_CLOSED`，非零 `PENDING_CONFIRMATION`；不修改订单/费用调整 |
| RECON-002 财务确认并关闭差异 | `POST /api/v1/reconciliations/{reconciliationId}/confirm` | `scope:TENANT` + `finance:reconcile`；JSON `version` | `resolutionType`、`remark`、`version` | `PENDING_CONFIRMATION → CONFIRMED` | `RECON-1003`、`COMMON-1006` | 乐观锁和审计；已自动关闭、已确认或版本冲突均不可重复处理 |

## 14. 异常件与索赔

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| EXCEPTION-001 查询异常列表/详情 | `GET /api/v1/exceptions`、`GET /api/v1/exceptions/{exceptionId}` | 当前租户 JWT | `orderId`、`status`、分页或 `exceptionId` | 租户异常分页、详情及索赔摘要 | `COMMON-1001`、`COMMON-1006` | 只读；所有 SQL 带 `tenant_id`；`exception_case`、`claim_record`、分派审计投影 |
| EXCEPTION-002 从订单或轨迹创建异常 | `POST /api/v1/orders/{orderId}/exceptions` | `scope:TENANT` + `order:operate` | `Idempotency-Key`；`exceptionType`、`description`、`reportedAt`、可选 `trackingEventId` | `201`、`OPEN` 异常单 | `EXCEPTION-1001`、`COMMON-1006`、`COMMON-1009`、`COMMON-1010` | 通用幂等记录；轨迹必须属于同租户同订单；写审计 |
| EXCEPTION-003 分派/解决/关闭 | `POST /api/v1/exceptions/{exceptionId}/assign`、`POST /api/v1/exceptions/{exceptionId}/status` | `scope:TENANT` + `order:operate`；CSRF | 分派人或目标状态、原因、`version` | `OPEN→PROCESSING→RESOLVED→CLOSED` | `EXCEPTION-1002`、`COMMON-1006` | 乐观锁防重复；分派人写追加审计详情；不修改订单状态或费用 |
| CLAIM-001 创建/查询索赔 | `POST /api/v1/exceptions/{exceptionId}/claim`、`GET /api/v1/claims/{claimId}` | 写：`scope:TENANT` + `order:operate`；读：当前租户 JWT | `Idempotency-Key`、金额和币种；或 `claimId` | `201 OPEN` 索赔或索赔详情 | `CLAIM-1001`、`CLAIM-1002`、`CLAIM-1003`、`COMMON-1006` | 异常须已解决，订单须为 `DELIVERED/RETURNED/LOST`，币种须匹配；一异常一索赔；写审计 |
| CLAIM-002 提交/审核/关闭索赔 | `POST /api/v1/claims/{claimId}/submit`、`/result`、`/close` | `scope:TENANT` + `order:operate`；CSRF | 原因、`version`；审核另含 `APPROVED/REJECTED` 和 `resolvedAt` | `OPEN→SUBMITTED→APPROVED/REJECTED→CLOSED` | `CLAIM-1004`、`COMMON-1006` | 状态前置条件与乐观锁防跳跃、回退和重复；每步写审计 |

## 15. 审计日志

## 16. 第二轮契约补充

- 创建租户的 `initialAdmin`、创建初始管理员和创建租户用户请求必须包含 `temporaryPassword`。
- `temporaryPassword` 长度至少12位，并要求大小写字母、数字和特殊字符；后端使用 BCrypt 保存，不出现在响应、日志和审计详情中。
- 复称费用上涨是正常 HTTP 200 分支，返回 `feeChangeType=INCREASE` 和 `nextStatus=PENDING_PRICE_CONFIRMATION`。
- `WAREHOUSE-1003` 仅表示待确认价格状态下禁止重复复称，不表示正常涨价分支。

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| AUDIT-001 租户审计列表/详情 | `GET /api/v1/audit-logs`、`GET /api/v1/audit-logs/{auditId}` | `scope:TENANT` + `audit:read` | `resourceType`、`resourceId`、`action`、`operatorId`、UTC 时间范围、分页 | 本租户脱敏日志分页/详情 | `COMMON-1001`、`COMMON-1006` | 只读；查询必带 JWT `tenant_id`；不返回 `reason`、`requestId`、密码、JWT、Refresh Token、HMAC、认证凭据或完整地址 |
| AUDIT-002 平台指定租户查询 | `GET /api/v1/platform/audit-logs`、`GET /api/v1/platform/audit-logs/{auditId}` | `scope:PLATFORM` + `audit:read` | 必填 `tenantId`，其余筛选同租户查询 | 指定租户脱敏日志分页/详情 | `COMMON-1004`、`COMMON-1006` | 平台必须显式指定存在的租户；只读，无更新/删除接口 |
