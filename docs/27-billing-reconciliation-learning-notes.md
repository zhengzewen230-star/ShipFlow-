# 账单导入与费用对账学习笔记

## 业务流程

财务人员以 `multipart/form-data` 提交内存中的统一 CSV；第一版不访问本地文件系统、对象存储或物流商接口。CSV 表头固定为 `provider_bill_detail_no,tracking_no,billed_amount,currency,fee_type`。系统计算 SHA-256，并以 `tenant_id + provider_id + file_hash` 返回已存在批次，避免同一文件重复导入。

每条有效账单行以物流商和运单号匹配 `warehouse_outbound_record`，再读取订单的 `current_fee`。匹配成功时保存 `MATCHED` 明细并自动创建唯一对账记录：差额为零使用 `AUTO_CLOSED`；非零差额使用 `PENDING_CONFIRMATION`。文档明确第一版没有小额阈值，因此没有编造自动关闭金额规则。

## 不变量

- `system_amount` 只读取 `shipment_order.current_fee`，不更新订单、报价快照、费用调整、仓库复称或索赔历史。
- CSV 内重复明细号、已导入的明细号和无法解析的行只计入批次失败统计；可保存且未匹配订单的行写为 `ERROR`，不会生成对账。
- 所有批次、明细、对账查询都以 JWT `tenant_id` 过滤；跨租户和不存在统一为 `COMMON-1006/404`。
- 批次只从 `PROCESSING` 终结为 `SUCCESS`、`PARTIAL_SUCCESS` 或 `FAILED`；对账差异只允许 `PENDING_CONFIRMATION → CONFIRMED`，使用 `version` 乐观锁。
- 财务权限分离：导入要求 `finance:bill-import`，对账查询和确认要求 `finance:reconcile`；成功写操作写入 `audit_log`。

## 给项目负责人的问题

当前真实表结构不能持久化“缺失明细号/完全无法解析”的原始行，因为 `bill_detail.provider_bill_detail_no` 非空且全局唯一。第一版将这些行计入批次失败统计；若需要逐行回显完整原始错误，后续应单独设计导入错误表或原始文件存储策略。
