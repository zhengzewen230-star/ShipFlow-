# P3-02 统一工作台数据契约学习笔记

## 本子阶段完成内容

- 读取并复核 P3-01 任务清单、基线文档和学习笔记。
- 对照现有后端 DTO、Controller、Service、Mapper/XML、数据库字段、前端 service/页面和 OpenAPI，形成统一工作台数据契约设计。
- 明确订单、仓库、物流面单、轨迹、异常/索赔、费用确认、账单和对账的状态边界。
- 明确 `tenant_id`、`store_id`、角色、资源归属、UTC 存储、Asia/Shanghai 展示、分页/排序/筛选、金额、重量和错误响应规则。
- 确认报价即将过期和订单长时间未处理的默认阈值均为 24 小时，先按平台默认值实现，并保留后续配置化扩展点。
- 确认“待财务处理”保留为概览聚合指标，并拆分为待确认费用、账单导入错误、费用对账差异和待财务复核四项子项。

## 关键经验

- 旧 operations 和 warehouse overview 主要是租户级聚合，不能直接满足商家业务员的授权店铺隔离；统一工作台必须先确定后端可见店铺集合，再生成所有指标、待办、最近订单和风险。
- `LABEL_READY` 只能作为物流面单完成状态；`PENDING_LABEL`、`PENDING_OUTBOUND` 等是工作台派生作业状态，不能新增为第二套数据库状态。
- “今日”必须先按 `Asia/Shanghai` 自然日生成窗口，再转换为 UTC 查询；数据库的 `UTC_DATE()` 不能代表上海业务日。
- 账单重复判断必须同时保留文件内容 SHA-256 与 `Idempotency-Key` 请求摘要，文件名只用于展示。

## 验证与边界

- 新增设计文档：`docs/module-lifecycle/06-merchant-operations-workbench-phase3-p302-data-contract.md`。
- 文档关键字段和尾随空格检查通过。
- OpenAPI 静态检查通过：88 paths、105 operationIds、无重复；本轮未修改 OpenAPI。
- `git diff --check` 通过。
- 本轮未修改 Java、Vue/TypeScript、SQL、Flyway 或 OpenAPI，未连接数据库，未执行迁移，未调用顺丰接口，未提交或推送 Git。
- P3-02 后端统一快照接口、DTO、Mapper、测试和 OpenAPI 更新仍属于后续实现工作；在其完成并验证前，不应把 P3-03 前端页面接入未实现的接口。
- 本轮已确认上述两项业务规则，后续 P3-03 后端实现必须按本规则返回聚合值和子项，不得由前端自行合计。
