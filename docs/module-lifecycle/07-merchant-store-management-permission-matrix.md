# 店铺管理权限矩阵

更新时间：2026-08-18（Asia/Shanghai）

| 角色/边界 | 列表/详情 | 创建 | 编辑 | 启停 | 店铺范围 |
|---|---:|---:|---:|---:|---|
| `MERCHANT_ADMIN` | `store:read` | `store:manage` | `store:manage` | `store:manage` | 本租户 |
| `MERCHANT_OPERATOR` | `store:read` | 否 | 否 | 否 | active `sys_user_store_scope` |
| `FINANCE_OPERATOR` | 按现有角色权限 | 否 | 否 | 否 | 当前 SQL 租户级分支，待业务确认 |
| `WAREHOUSE_OPERATOR` | 按现有角色权限 | 否 | 否 | 否 | 当前 SQL 租户级分支，待业务确认 |
| `CUSTOMER_SERVICE_OPERATOR` | 按现有角色权限 | 否 | 否 | 否 | 当前 SQL 租户级分支，待业务确认 |

所有请求继续要求认证、`scope:TENANT`、权限码，并由 Service/Mapper 重新校验 `tenant_id`、`user_id`、角色、店铺 scope 和资源归属。未授权详情不返回 403 资源存在信息，统一 404。

P4-03 编辑和启停的前端按钮仅对 `store:manage` 可见；后端仍以 `scope:TENANT + store:manage` 拦截，并在 Service 中再次执行店铺资源归属校验。版本冲突和幂等冲突不改变可见性边界。

## P4-04 配置权限设计

店铺默认地址和默认渠道配置沿用同一店铺资源边界：`MERCHANT_OPERATOR` 只读授权店铺，`MERCHANT_ADMIN` 可维护本租户店铺，平台管理员不通过租户店铺 API 直接写入。财务、仓库和客服角色沿用既有 `store:read` 能力，不新增配置写权限。所有配置接口必须再次校验 `tenant_id`、`user_id`、角色、`store_id`、active `sys_user_store_scope` 和资源归属；未授权资源统一 404。

P4-01 没有调整角色权限、scope 数据、`StoreMapper.xml` 的授权 SQL 或数据库内容。

P4-02 详情读取与列表使用同一 `store:read` 页面权限和后端可见性条件；历史订单数量和审计摘要只在店铺通过 `findByIdForUser` 后查询。详情中的平台账号只允许服务端掩码展示，审计摘要不包含请求体、认证头、Cookie、Token 或其他敏感字段。
