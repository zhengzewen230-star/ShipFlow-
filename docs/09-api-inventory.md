# ShipFlow API 接口清单

## 1. 通用字段约定

除登录和刷新 Token 外，接口默认需要 `Authorization: Bearer <JWT>`、`X-Request-Id`（可选）和响应 `X-Trace-Id`。租户业务接口的 `tenant_id` 从 JWT 读取，不允许请求体或查询参数覆盖。

表中“请求体”只列业务字段；统一响应包装为 `ApiSuccess<T>`，错误使用 `ApiError`。分页接口使用 `page`、`pageSize`、`sortBy`、`sortDirection` 及白名单筛选字段。所有写接口的审计和幂等规则以 `08-api-conventions.md` 为准。

## 2. 接口总览

| 模块 | 接口数量 |
|---|---:|
| 认证与当前用户 | 3 |
| 平台租户管理 | 5 |
| 租户用户与角色 | 5 |
| 店铺管理 | 3 |
| 物流渠道查询 | 4 |
| 运费报价 | 4 |
| 物流订单 | 6 |
| 仓库操作 | 5 |
| 物流轨迹 | 4 |
| 账单导入 | 4 |
| 费用对账 | 4 |
| 异常件与索赔 | 4 |
| 审计日志 | 1 |
| **合计** | **52** |

## 3. 认证与当前用户

| 编号/用途 | 方法 URL | 角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| AUTH-001 登录 | `POST /api/v1/auth/login` | 匿名；JSON | `username`、`password`、可选 `tenantCode` | `accessToken`、`refreshToken`、过期时间、用户摘要 | `AUTH-1001`、`AUTH-1004`、`COMMON-1008` | 非幂等/记录登录审计/`sys_user`、`audit_log` | 正常、密码错误、禁用用户、平台/租户用户、跨租户同名用户必须指定租户 |
| AUTH-002 刷新 Token | `POST /api/v1/auth/refresh` | 匿名；JSON | `refreshToken` | 新 Token 对 | `AUTH-1002`、`AUTH-1003` | 幂等窗口内可重试/记录刷新结果/审计 | 正常、过期、重复使用、伪造 Token |
| AUTH-003 当前用户 | `GET /api/v1/users/me` | 登录用户 | 无 | 用户、角色、权限、租户摘要 | `COMMON-1002`、`COMMON-1003` | 只读/不写审计或仅记录安全访问/`sys_user`、`sys_role`、`sys_permission` | 平台用户、租户用户、权限边界 |

## 4. 平台租户管理

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| TENANT-001 创建租户 | `POST /api/v1/platform/tenants` | 平台管理员 | `tenantCode`、`tenantName`、`initialAdmin` | `201` 租户和初始管理员 ID | `TENANT-1001`、`COMMON-1001` | `Idempotency-Key`幂等/写审计/`tenant`、`sys_user`、`sys_role`、`sys_user_role` |
| TENANT-002 查询租户 | `GET /api/v1/platform/tenants` | 平台管理员；分页筛选 | `status`、`tenantCode` | 分页租户列表 | `COMMON-1002`、`COMMON-1004` | 只读/审计可选/`tenant` |
| TENANT-003 查询租户详情 | `GET /api/v1/platform/tenants/{tenantId}` | 平台管理员；路径 `tenantId` | `tenantId` | 租户详情 | `COMMON-1006`、`COMMON-1004` | 只读/审计可选/`tenant` |
| TENANT-004 启用/停用租户 | `POST /api/v1/platform/tenants/{tenantId}/status` | 平台管理员；JSON `version` | `status`、`version` | 新状态和版本 | `TENANT-1002`、`COMMON-1005` | 非幂等/写审计/`tenant` |
| TENANT-005 创建初始管理员 | `POST /api/v1/platform/tenants/{tenantId}/initial-admin` | 平台管理员；JSON | `username`、`displayName` | 用户和角色摘要 | `USER-1001`、`TENANT-1002` | `Idempotency-Key`幂等/写审计/`sys_user`、`sys_role`、`sys_user_role` |

## 5. 租户用户与角色

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| USER-001 创建用户 | `POST /api/v1/users` | 商家管理员 | `username`、`displayName`、`roleIds` | `201` 用户摘要 | `USER-1001`、`COMMON-1001` | `Idempotency-Key`幂等/写审计/`sys_user`、`sys_user_role` |
| USER-002 查询用户 | `GET /api/v1/users` | 商家管理员；分页 | `status`、`username` | 分页用户列表 | `COMMON-1004` | 只读/审计可选/`sys_user` |
| USER-003 启用/停用用户 | `POST /api/v1/users/{userId}/status` | 商家管理员；JSON `version` | `status`、`version` | 新状态和版本 | `USER-1002`、`COMMON-1005`、`USER-1003` | 非幂等/写审计/`sys_user` |
| USER-004 分配角色 | `PUT /api/v1/users/{userId}/roles` | 商家管理员；JSON `version` | `roleIds`、`version` | 用户角色列表 | `USER-1003`、`COMMON-1005` | 非幂等/写审计/`sys_user_role`、`audit_log` |
| USER-005 查询角色和权限 | `GET /api/v1/roles` | 商家管理员 | `scope` | 角色和权限列表 | `COMMON-1004` | 只读/不写业务审计/`sys_role`、`sys_permission`、`sys_role_permission` |

## 6. 店铺管理

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| STORE-001 创建店铺 | `POST /api/v1/stores` | 商家管理员 | `storeCode`、`storeName`、`platformCode`、`platformAccount` | `201` 店铺摘要 | `COMMON-1001`、`TENANT-1003` | `Idempotency-Key`幂等/写审计/`merchant_store` |
| STORE-002 查询店铺 | `GET /api/v1/stores` | 商家管理员、商家操作员 | `status`、`platformCode` | 店铺列表 | `COMMON-1004` | 只读/`merchant_store` |
| STORE-003 启用/停用店铺 | `POST /api/v1/stores/{storeId}/status` | 商家管理员；JSON `version` | `status`、`version` | 新状态 | `COMMON-1005`、`COMMON-1006` | 非幂等/写审计/`merchant_store` |

## 7. 物流渠道查询

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| CHANNEL-001 查询可用渠道 | `GET /api/v1/logistics/channels` | 租户用户；查询国家 | `countryCode`、`status=ACTIVE` | 渠道公开列表 | `COMMON-1001` | 只读/`logistics_channel`、`logistics_channel_service_country` |
| CHANNEL-002 查询服务国家 | `GET /api/v1/logistics/channels/{channelId}/service-countries` | 租户用户 | `channelId` | 国家编码列表 | `COMMON-1006` | 只读/`logistics_channel_service_country` |
| CHANNEL-003 查询渠道公开信息 | `GET /api/v1/logistics/channels/{channelId}` | 租户用户 | `channelId` | 渠道、物流商和价格规则摘要 | `COMMON-1006` | 只读/`logistics_provider`、`logistics_channel`、`price_rule` |
| CHANNEL-004 维护价格规则 | `POST /api/v1/platform/channels/{channelId}/price-rules` | 平台管理员；JSON `version` | `versionNo`、计费参数、tiers | `201` 价格规则 | `QUOTE-1002`、`COMMON-1005` | `Idempotency-Key`幂等/写审计/`price_rule`、`price_rule_tier` |

## 8. 运费报价

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| QUOTE-001 创建报价 | `POST /api/v1/quotes` | 商家管理员、商家操作员 | `storeId`、`channelId`、申报重量尺寸、目的国 | `201` 报价和 `feeDetail` | `QUOTE-1001`、`QUOTE-1002` | `Idempotency-Key`幂等/写审计/`quote`、`price_rule` |
| QUOTE-002 查询报价详情 | `GET /api/v1/quotes/{quoteId}` | 当前租户用户 | `quoteId` | 报价详情和费用明细 | `COMMON-1006`、`TENANT-1003` | 只读/`quote` |
| QUOTE-003 查询可用报价 | `GET /api/v1/quotes` | 当前租户用户；分页 | `storeId`、`channelId`、`status` | 分页报价 | `COMMON-1001` | 只读/`quote` |
| QUOTE-004 校验报价有效性 | `POST /api/v1/quotes/{quoteId}/validate` | 当前租户用户 | `quoteId` | `valid`、失效原因 | `QUOTE-1003`、`QUOTE-1004` | 只读校验/不写业务审计/`quote`、`shipment_order` |

## 9. 物流订单

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| ORDER-001 创建订单 | `POST /api/v1/orders` | 商家管理员、商家操作员；必须 `Idempotency-Key` | `quoteId`、地址、包裹、商品 | `201` 订单、当前状态、费用 | `ORDER-1002`、`ORDER-1003`、`QUOTE-1003` | 幂等/写审计/`shipment_order`、`shipment_quote_snapshot`、`shipment_address`、`shipment_package`、`shipment_item` |
| ORDER-002 订单详情 | `GET /api/v1/orders/{orderId}` | 当前租户用户 | `orderId` | 订单、包裹、地址、费用、状态 | `COMMON-1006`、`TENANT-1003` | 只读/`shipment_order`及关联表 |
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
| TRACK-001 Mock回调 | `POST /api/v1/integrations/logistics/{providerCode}/tracking-events` | Mock系统账号；签名头 | 事件数组：`trackingNo`、`eventId`、`eventCode`、`eventTime`、原始报文 | 接收结果和每事件处理状态 | `TRACK-1004`、`TRACK-1002` | 业务键幂等/首次处理写审计/`tracking_event`、`shipment_order` |
| TRACK-002 查询订单轨迹 | `GET /api/v1/orders/{orderId}/tracking-events` | 当前租户用户 | `orderId`、分页 | 按 `eventTime` 排序的轨迹 | `COMMON-1006` | 只读/`tracking_event` |
| TRACK-003 查询回调异常 | `GET /api/v1/platform/tracking-events/errors` | 平台管理员、Mock系统账号 | `providerId`、`processStatus`、分页 | 异常事件列表 | `COMMON-1004` | 只读/`tracking_event` |
| TRACK-004 重试异常回调 | `POST /api/v1/platform/tracking-events/{eventId}/retry` | 平台管理员、Mock系统账号；JSON `version` | `reason`、`version` | 重试受理结果 | `TRACK-1003`、`COMMON-1005` | 幂等/写审计/`tracking_event`、`audit_log` |

## 12. 账单导入

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| BILL-001 上传 CSV | `POST /api/v1/billing/import-batches` | 财务人员；multipart `file` | `file`、`providerId` | `202` 批次和文件摘要 | `BILL-1001`、`BILL-1002` | 文件 Hash 幂等/写审计/`bill_import_batch`、`bill_detail` |
| BILL-002 查询导入批次 | `GET /api/v1/billing/import-batches` | 财务人员 | `providerId`、`status`、分页 | 批次列表 | `COMMON-1001` | 只读/`bill_import_batch` |
| BILL-003 查询行级错误 | `GET /api/v1/billing/import-batches/{batchId}/errors` | 财务人员 | `batchId`、分页 | 行号和错误原因 | `COMMON-1006` | 只读/`bill_detail` |
| BILL-004 查询账单明细 | `GET /api/v1/billing/details` | 财务人员 | `providerBillDetailNo`、订单号、分页 | 账单明细列表 | `BILL-1003`、`COMMON-1001` | 只读/`bill_detail` |

## 13. 费用对账

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| RECON-001 执行对账 | `POST /api/v1/reconciliations` | 财务人员 | `batchId` | 对账批次处理结果 | `RECON-1001` | 幂等/写审计/`reconciliation_record`、`shipment_order`、`bill_detail` |
| RECON-002 查询对账记录 | `GET /api/v1/reconciliations` | 财务人员 | `status`、订单号、分页 | 对账记录列表 | `COMMON-1001` | 只读/`reconciliation_record` |
| RECON-003 财务确认差异 | `POST /api/v1/reconciliations/{reconciliationId}/confirm` | 财务人员；JSON `version` | `resolutionType`、`remark`、`version` | 确认后的对账记录 | `RECON-1002`、`RECON-1004`、`COMMON-1005` | 幂等/写审计/`reconciliation_record`、`audit_log` |
| RECON-004 查询订单费用调整 | `GET /api/v1/orders/{orderId}/fee-adjustments` | 财务人员、商家用户 | `orderId`、分页 | 费用调整历史 | `COMMON-1006` | 只读/`fee_adjustment`、`warehouse_measurement` |

## 14. 异常件与索赔

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| CLAIM-001 创建异常单 | `POST /api/v1/orders/{orderId}/exceptions` | 商家操作员、商家管理员、平台管理员 | `exceptionType`、`description`、`reportedAt` | `201` 异常单 | `CLAIM-1001`、`COMMON-1001` | `Idempotency-Key`幂等/写审计/`exception_case` |
| CLAIM-002 更新异常状态 | `POST /api/v1/exceptions/{exceptionId}/status` | 商家操作员、商家管理员、平台管理员；JSON `version` | `status`、`reason`、`version` | 新异常状态 | `COMMON-1005`、`CLAIM-1001` | 幂等/写审计/`exception_case` |
| CLAIM-003 创建索赔 | `POST /api/v1/exceptions/{exceptionId}/claim` | 商家操作员、商家管理员 | `claimAmount`、`currency` | `201` 索赔记录 | `CLAIM-1001`、`CLAIM-1002`、`CLAIM-1003` | `Idempotency-Key`幂等/写审计/`claim_record` |
| CLAIM-004 更新索赔结果 | `POST /api/v1/claims/{claimId}/result` | 商家管理员、平台管理员；JSON `version` | `status`、`resolvedAt`、`version` | 新索赔状态 | `COMMON-1005`、`CLAIM-1001` | 幂等/写审计/`claim_record` |

## 15. 审计日志

## 16. 第二轮契约补充

- 创建租户的 `initialAdmin`、创建初始管理员和创建租户用户请求必须包含 `temporaryPassword`。
- `temporaryPassword` 长度至少12位，并要求大小写字母、数字和特殊字符；后端使用 BCrypt 保存，不出现在响应、日志和审计详情中。
- 复称费用上涨是正常 HTTP 200 分支，返回 `feeChangeType=INCREASE` 和 `nextStatus=PENDING_PRICE_CONFIRMATION`。
- `WAREHOUSE-1003` 仅表示待确认价格状态下禁止重复复称，不表示正常涨价分支。

| 编号/用途 | 方法 URL | 允许角色/请求头 | 参数/请求体 | 成功响应 | 业务错误 | 幂等/审计/数据表 | 测试重点 |
|---|---|---|---|---|---|---|---|
| AUDIT-001 查询审计日志 | `GET /api/v1/audit-logs` | 平台管理员；租户管理员只能本租户 | `operatorUserId`、`resourceType`、`resourceId`、时间范围、分页 | 审计日志分页 | `COMMON-1004`、`COMMON-1006` | 只读/不得修改日志/`audit_log` |
