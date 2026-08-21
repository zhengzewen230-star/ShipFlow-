# 顺丰国际件接入学习笔记

## 业务流程

租户仓库操作员在订单完成复称并进入 `READY_FOR_OUTBOUND` 后，ShipFlow 按租户读取地址、商品和渠道数据，使用幂等请求号创建顺丰供应商订单。供应商状态依次覆盖创建、面单、清关资料、查询和取消；数据库分别保存 `provider_order`、`shipment_label` 和 `customs_document` 的非敏感状态与引用。

配置不完整、地址不在沙箱白名单、订单状态不满足、跨租户访问、重复处理中、供应商传输失败或供应商业务失败时，后端拒绝外部调用或将供应商订单标记为 `FAILED`。有效沙箱配置只解除配置缺失造成的 fail-closed，不删除 URL 白名单、沙箱开关、凭据校验、超时和重试边界。

## 迁移与 Schema

- 目标库只读门禁确认是 `shipflow`、MySQL `8.0.46`；不是 `shipflow_test` 或 `shipflow_qa`。
- 现有库没有 `flyway_schema_history`，但已有业务 Schema，因此以现有结构的 V012 建立 Flyway baseline，再执行 V013、V014、V015。
- Flyway 结果：baseline `12`，执行迁移数 `3`，history 中 `013`、`014`、`015` 均为成功。
- Schema 断言确认 `provider_order`、`shipment_label`、`customs_document`、V014 两个交接字段、V015 `invoice_reference`、租户列、幂等唯一索引和状态检查约束均存在。
- 迁移前备份了空的 `warehouse_outbound_record` 表到仓库外临时目录；该表当时为 0 行。

## 真实联调

订单 31 通过真实 HTTP 状态流准备为 `READY_FOR_OUTBOUND`，版本从 0 变为 3。独立 8081 后端使用当前 `DB_*` 和 `SF_OPEN_*` 环境，临时 JWT 仅用于测试租户 1 的现有用户权限；未修改用户密码、权限关系或生产配置。

五个顺丰接口均真实返回 HTTP 200 且接口成功断言通过：

| 操作 | service code | 数据库断言 |
| --- | --- | --- |
| 创建订单 | `COM_RECE_IUOP_CREATE_ORDER` | `provider_order=CREATED` |
| 获取面单 | `COM_RECE_IUOP_PRINT_ORDER` | `provider_order=LABEL_READY`、`shipment_label=READY` |
| 上传清关资料 | `COM_RECE_IUOP_UPLOAD_CERTIFY` | `customs_document=UPLOADED` |
| 查询订单 | `COM_RECE_IUOP_QUERY_ORDER` | `provider_order` 保持 `LABEL_READY` |
| 取消订单 | `COM_RECE_IUOP_CANCEL_ORDER` | `provider_order=CANCELLED` |

沙箱响应没有返回外部订单号、运单号、面单引用或清关引用，数据库中的相应 presence 断言均为 0。此次只能确认真实请求链路和状态落库成功，不能声称获得可追踪运单或可下载面单。

## 测试证据

- SF 定向 Maven 测试：12 tests，全部通过。
- 真实 Flyway 迁移：V013/V014/V015 成功。
- 真实 MySQL Schema、Flyway history、租户/状态/引用字段断言：通过。
- 真实 HTTP 五接口及数据库双断言：通过。

## 待负责人确认

1. 顺丰沙箱成功响应为何未返回外部订单号、运单号、面单引用和清关引用；需要官方响应样例确认字段映射或沙箱账号能力。
2. 当前订单 31 的沙箱测试结果是否需要人工清理，以及测试件保留期限和取消责任人是谁。
3. 是否允许把“供应商成功但关键引用为空”收紧为业务失败，避免前端把空引用误认为可下载的面单或可追踪运单。
