# 第三功能模块生命周期：访客预估线索与平台跟进

## 1. 业务边界

访客在公开页面提交运输需求后，系统只创建 `guest_estimate_lead` 平台线索。它不是正式报价、物流订单、店铺或租户，也不会触发租户创建。平台运营人员在后续沟通中确认需求；正式报价仍需商户审核、账号激活、登录和租户侧店铺/渠道配置。

```text
访客 POST /public/estimate-requests
  -> guest_estimate_lead（RECEIVED）
  -> 平台管理员 GET /platform/guest-estimate-leads
  -> CONTACTING -> QUALIFIED -> CLOSED
```

`RECEIVED` 可转为 `CONTACTING` 或 `CLOSED`；`CONTACTING` 可转为 `QUALIFIED` 或 `CLOSED`；`QUALIFIED` 仅可转为 `CLOSED`。已关闭线索不能重新打开。状态更新使用 `version` 乐观锁，冲突返回 `COMMON-1005 / 409`。

## 2. 接口与权限

| 接口 | operationId | 权限边界 |
|---|---|---|
| `POST /api/v1/public/estimate-requests` | `createGuestEstimateRequest` | 匿名但 CSRF 和幂等键保护 |
| `GET /api/v1/platform/guest-estimate-leads` | `listGuestEstimateLeads` | `scope:PLATFORM` 和 `tenant:manage` |
| `GET /api/v1/platform/guest-estimate-leads/{leadId}` | `getGuestEstimateLead` | 同上 |
| `PATCH /api/v1/platform/guest-estimate-leads/{leadId}/status` | `updateGuestEstimateLeadStatus` | 同上，CSRF、`X-Request-Id`、版本号 |

租户用户、仓库、财务、客服、物流商等 `TENANT` scope 身份即使知道 URL 也会由后端返回 `403 / COMMON-1004`。前端菜单仅改善可用性，不承担授权职责。

## 3. 数据模型与查询

`guest_estimate_lead` 为平台级匿名线索表，不包含 `tenant_id`。V012 新增跟进备注、处理人、处理时间、版本、更新时间、软删除字段和状态/时间索引。所有平台查询固定 `deleted=0`，显式列名查询，不使用 `SELECT *`；列表支持状态、关键字、提交时间范围和分页。时间范围接收带时区 ISO-8601 值并在应用层统一换算为 UTC。

状态更新同时写入 `audit_log`，资源类型为 `GUEST_ESTIMATE_LEAD`，`tenant_id` 为 `NULL`，表示平台级操作。

## 4. 前端路由与菜单

平台管理员满足 `PLATFORM + tenant:manage` 时可见并访问 `/app/guest-estimate-leads`。页面显示加载、空数据、无权限与错误状态；列表中的手机号默认脱敏，详情在授权的运营页面展示完整联系信息。租户菜单不包含该入口。

## 5. 测试与验收边界

已补充服务层状态转换、UTC 时间筛选、审计写入，以及 MockMvc 的平台成功、租户拒绝和状态更新测试；前端已有导航权限测试覆盖平台可见、租户不可见。真实 MySQL 集成测试、迁移执行和 HTTP 验收不在本轮执行，必须在隔离验收库完成后再进行。
