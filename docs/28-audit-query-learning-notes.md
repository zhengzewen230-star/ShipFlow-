# 审计日志与运营查询学习笔记

审计模块只读取 `audit_log`，不提供创建、更新或删除接口。租户查询以 JWT `tenant_id` 强制过滤；平台查询必须显式传入存在的 `tenantId`，同样使用该值过滤日志，避免平台查询成为无范围扫描。

支持按资源类型、资源 ID、动作、操作人和 UTC 时间范围分页。详情查询使用 `tenant_id + id`，因此跨租户与不存在均返回 `COMMON-1006/404`。

审计响应不返回 `reason` 与 `requestId`。`detail` JSON 递归处理 `password`、`token`、`refreshToken`、`jwt`、`hmac`、`secret`、`credential`、`authorization` 和地址/联系方式键；疑似 Bearer/JWT 文本也替换为 `[REDACTED]`。这样查询接口不会重新暴露认证凭据或完整敏感地址。

权限采用已有 `audit:read`：租户入口要求 `scope:TENANT`，平台入口要求 `scope:PLATFORM`。后续如需要运营跨租户汇总，应另设经审批的聚合指标接口，而不是放宽原始审计日志访问范围。
