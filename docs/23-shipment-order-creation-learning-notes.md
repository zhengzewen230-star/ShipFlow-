# 报价转物流订单学习笔记

- 订单创建是报价的一次性消费：先按 `tenant_id` 读取报价，再在同一事务内由数据库唯一键 `uk_order_quote` 最终裁决并发竞争。
- 订单不重新计算运费。金额、币种、计费重量、规则版本和完整 `fee_detail` 原样复制进 `shipment_quote_snapshot`，使后续计费和审计不依赖已变化的价格规则。
- 幂等键以租户和操作名隔离。同键完成请求返回首个订单；报价已被其他键消费时返回 `QUOTE-1004`。
- 所有时间使用 `Clock` 产生的 UTC `LocalDateTime`，API 返回 UTC offset。
