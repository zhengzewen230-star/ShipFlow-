# 店铺管理 OpenAPI 覆盖说明

## P4-01

| operationId | 方法 | 路径 | P4-01 变化 |
|---|---|---|---|
| `listStores` | GET | `/api/v1/stores` | 新增分页、编码、名称、平台、状态和白名单排序参数；成功响应改为分页对象 |
| `getStore` | GET | `/api/v1/stores/{storeId}` | P4-02 改为脱敏详情、历史订单数量和审计摘要响应 |
| `createStore` | POST | `/api/v1/stores` | 本阶段未修改 |
| `updateStore` | PUT | `/api/v1/stores/{storeId}` | P4-03 要求 `Idempotency-Key`，请求含版本号；返回版本冲突 409 |
| `changeStoreStatus` | POST | `/api/v1/stores/{storeId}/status` | P4-03 要求 `Idempotency-Key`，请求含版本号；返回版本冲突 409 |

OpenAPI 静态检查结果：`operationId` 共 106 个，唯一值 106，重复 0。`listStores` 的返回 schema 已与后端 `StorePage(page,pageSize,total,totalPages,items)` 对齐。

## P4-02

`getStore` 的成功响应已改为 `ApiSuccessStoreDetail`，包含 `platformAccountMasked`、`historicalOrderCount`、`auditSummary` 和 `unavailableFields`；未授权状态继续声明 404。详情响应不返回 `platformAccount` 原始字段。OpenAPI 总体 operationId 基线仍以项目既有 106/106 检查为准。

## P4-03

`updateStore` 与 `changeStoreStatus` 均声明并实际要求 `Idempotency-Key`；相同请求重放返回原资源，不同请求复用同一幂等键返回 409。版本号由数据库条件更新校验，过期版本返回 409。两类写操作均在写入前执行当前用户店铺可见性校验，并写入已有审计日志；没有放宽 `tenant_id` 或 `sys_user_store_scope`。

## P4-04（设计）

计划扩展 `getStore` 的默认配置摘要，并新增默认地址、店铺渠道列表和默认渠道设置接口。当前仅完成契约设计，OpenAPI 尚未修改；待业务确认字段和 DBA 授权后，再同步 operationId、schema、错误码和 401/403/404/409/422 响应。
## P4-05 契约补充（2026-08-19）

V020 后端资源契约已同步 OpenAPI：默认地址查询/更新、店铺渠道列表、默认渠道设置共 4 个 operation；当前静态 operationId 为 110，重复数为 0。写接口声明 `Idempotency-Key`、版本字段以及 401/403/404/409/422 响应。
