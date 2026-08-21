# 商家业务员工作台第三阶段 P3-06 验收总表

更新时间：2026-08-18（Asia/Shanghai）

## 阶段结果

| 子阶段 | 结果 | 说明 |
|---|---|---|
| P3-06a | PASS | 概览失败追踪编号、复制反馈和测试完成 |
| P3-06b | PASS | 工作台入口、真实列表路由和 URL 筛选回显完成；缺失后端契约明确标注 |
| P3-06c | PASS | 刷新、时间范围和 UTC/Asia/Shanghai 展示完成 |
| P3-06d | PASS | DataState、错误映射、Trace ID、重试、防重复提交和二次确认完成 |
| P3-06e | PASS（环境限制保留） | 权限、资源归属、账单/轨迹 scope、refresh 清理和审计收口完成；浏览器会话恢复、Cookie 面板和需破坏性制造的失败场景继续 BLOCKED |

## 不变安全规则

所有页面和接口继续使用 authentication、scope、角色/权限、`tenant_id`、`user_id`、`store_id`、active `sys_user_store_scope` 和资源归属校验。前端隐藏不能替代后端拒绝；跨租户或未授权资源统一使用不泄露存在性的 404 语义。数据库按 UTC 存储，页面按 Asia/Shanghai 展示。

## 证据状态

- 前端单元测试 65/65、构建、后端 JDK 21 定向测试 98/98 和 OpenAPI 106 operation 已在本轮复核。
- 401、网络失败、409/422/500、Cookie 面板属性等不能安全制造或读取的场景必须继续记录 BLOCKED。
- 任何未执行的数据库写入、迁移、生产调用和 Git 操作均不计入验收通过。

## 交付链接

- [P3-06e 安全、权限、审计与最终验收](06-merchant-operations-workbench-phase3-p306e-security-audit-acceptance.md)
- [P3-06d 统一企业级交互](06-merchant-operations-workbench-phase3-p306d-enterprise-interaction.md)
- [前端身份与导航权限矩阵](../../frontend/docs/identity-navigation-matrix.md)
- [RBAC 全链路审计](../../docs/34-rbac-access-audit.md)
