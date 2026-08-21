# RBAC 全链路审计

审计日期：2026-08-14（Asia/Shanghai）  
范围：数据库迁移与 Mapper、后端安全规则和 Controller、OpenAPI、Vue 路由与侧边栏。

## 结论

当前实现只有 `PLATFORM` / `TENANT` 两类 scope，且大量租户接口只要求 `scope:TENANT`。这不能实现“每个角色只负责自己内容”的业务边界：任何已认证租户角色都可访问报价、订单、轨迹、异常读取和运营概览接口；前端也据此展示相应菜单。

授权判定使用 `CurrentCallerService` 在每次 JWT 请求时从数据库重新加载有效角色和权限，这一机制正确；问题在于权限字典、角色绑定、`SecurityConfig`、OpenAPI 和前端导航没有使用同一份细粒度矩阵。

## 已发现问题

| 编号 | 证据 | 风险 | 建议修复 |
| --- | --- | --- | --- |
| R1 | `SecurityConfig` 对 `/api/v1/quotes/**`、`/api/v1/orders/**`、`/api/v1/operations/**` 仅要求 `scope:TENANT` | 财务、仓库、客服等可创建报价、创建/修改/提交/取消订单 | 新增报价、订单读取/创建/管理权限，按 HTTP 方法和路径授权 |
| R2 | 轨迹查询和异常/索赔读取仅要求 `scope:TENANT` | 无关租户角色可读取运输或异常信息 | 新增 `tracking:read`、`exception:read`，分别保护查询接口 |
| R3 | 仓库写接口要求 `warehouse:manage`，但初始字典只有 `warehouse:measure`、`warehouse:outbound` | 仓库操作员可能获得 403；初始数据与安全规则不一致 | 在新增迁移中补齐 `warehouse:manage`，并只授予仓库角色及明确需要该能力的租户管理员 |
| R4 | `V006` 把 `logistics:read` 授予所有 active tenant roles | 财务、仓库、客服可读取正式报价依赖的渠道目录，超出职责 | 不再向所有租户角色批量授予；迁移中按明确角色矩阵重建该绑定 |
| R5 | 前端 `/app/quotes`、`/app/orders`、`/app/tracking`、`/app/exceptions` 仅使用 `scope:TENANT` | 菜单与页面范围大于职责范围，且与后端粗粒度授权一致 | 路由和 Sidebar 改用与后端相同的细粒度 permission code |
| R6 | `CurrentUserResponse` 只返回 scope 与 permissions，不返回角色码 | 前端不能可靠显示角色名称，但这本身不应成为放行依据 | 保持权限驱动；若 UI 必须显示角色名称，单独扩展只读 roles 字段并更新 OpenAPI |
| R7 | 物流服务商/承运商仅通过 HMAC 回调的系统身份存在，没有浏览器 scope/菜单契约 | 若前端假设其可登录，将形成无授权实现 | 保持 server-to-server；独立控制台需先新增身份模型、OpenAPI 和安全规则 |

## 目标角色矩阵

| 角色 | 应有能力 |
| --- | --- |
| 平台超级管理员 | 租户、平台渠道、物流服务商、价格规则、平台审计 |
| 租户管理员 | 本租户用户、角色、店铺、订单、报价、基础资料 |
| 商户业务人员 | 发起/查看报价、创建/管理订单、查看轨迹、处理异常、查看账单 |
| 财务人员 | 查看账单与对账、确认差异；不得创建报价或执行仓库作业 |
| 仓库操作员 | 入库、复称、出库及必要订单/轨迹读取；不得管理用户、角色、报价或账单 |
| 客服/异常专员 | 订单/轨迹读取、异常分派和索赔处理；不得管理租户、店铺、价格规则或仓库作业 |
| 物流服务商/承运商 | 当前仅 HMAC 轨迹和账单数据回传，不提供浏览器控制台 |
| 访客 | 仅公开预估线索和入驻申请 |

## 最小修复包

1. 新增 `V011__align_role_permission_matrix.sql`，补齐权限字典并按上述角色重建角色—权限绑定。该迁移必须幂等，且不能手工改写业务数据。
2. 修改 `SecurityConfig`：按路径与方法应用新的报价、订单、轨迹、异常、账单读取与操作权限；保留 `scope:TENANT` 作为租户隔离的第一层。
3. 更新 OpenAPI：每个受影响 operation 的描述和 `403` 语义写明所需 permission code。
4. 更新前端路由、侧边栏、工作台入口和按钮控制，复用同一权限码；前端隐藏不能替代后端拒绝。
5. 补充 MockMvc：每种角色至少验证一个允许操作、一个越权 403；补充前端权限导航测试。

## 数据库只读核验（在已配置临时环境变量的终端执行）

以下 SQL 不输出密码、Token、Cookie 或 HMAC 密钥；先确认目标库不是生产库：

```sql
SELECT DATABASE() AS database_name;

SELECT u.id AS user_id, u.tenant_id, u.username, r.role_code, r.role_scope,
       GROUP_CONCAT(DISTINCT p.permission_code ORDER BY p.permission_code SEPARATOR ', ') AS permissions
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id
LEFT JOIN sys_permission p ON p.id = rp.permission_id
WHERE u.status = 'ACTIVE' AND u.deleted = 0 AND r.status = 'ACTIVE' AND r.deleted = 0
GROUP BY u.id, u.tenant_id, u.username, r.role_code, r.role_scope
ORDER BY u.tenant_id, u.username, r.role_code;

SELECT role_scope, role_code, permission_code
FROM sys_role r
JOIN sys_role_permission rp ON rp.role_id = r.id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.status = 'ACTIVE' AND r.deleted = 0
ORDER BY role_scope, role_code, permission_code;

SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## 运行库核验结果

已于 2026-08-14 对 `shipflow` 执行只读查询；未执行任何写操作。

- 该库没有 `flyway_schema_history`，无法以 Flyway 记录确认 `V004` 至 `V010` 的安装顺序，只能依据实际表结构和绑定数据判断。
- 现有 active tenant role code 只有 `MERCHANT_ADMIN`、`MERCHANT_OPERATOR`、`FINANCE_OPERATOR`、`WAREHOUSE_OPERATOR`、`TEST_NO_PERMISSION`；没有客服/异常专员、物流服务商或承运商浏览器角色。
- `warehouse:manage`、`tracking:read`、`quote:create` 当前均不存在绑定；仓库接口会因现有 `SecurityConfig` 要求 `warehouse:manage` 而拒绝仓库操作员。
- 所有现有租户角色均拥有 `logistics:read`，包括财务、仓库和无权限测试角色。
- 两个 `MERCHANT_ADMIN` 角色实例（租户 13、14）错误拥有 `tenant:create`。这违反平台/租户隔离，必须在修复迁移中移除。
- 多个后续创建的 `MERCHANT_ADMIN` 仅有用户、角色、店铺和渠道读取权限，缺少订单相关权限；当前它们之所以仍可能调用报价和订单接口，是因为这些接口只检查 `scope:TENANT`。

后续 `V011` 必须以实际角色代码为目标进行幂等绑定和撤销，先清除 tenant role 中的所有平台权限，再建立细粒度职责矩阵。执行前应先备份受影响的 `sys_role_permission` 行并在事务内验证结果。
## 2026-08-14 RBAC repair outcome

- `V011__align_role_permission_matrix.sql` was applied to the authorized `shipflow` database after a role-permission backup outside the repository.
- Database readback confirmed zero tenant bindings to platform permissions or `tracking:callback`, zero duplicate role-permission bindings, and zero active tenants missing a standard tenant role.
- Tenant role permission replacement now uses a server-side tenant allowlist. A direct request cannot assign platform permissions or the HMAC callback permission even when the UI is bypassed.
- OpenAPI still declares `confirmPrice`, but no matching controller was found in the backend. This remains an explicit contract gap and must be implemented as a separate order-lifecycle module rather than treated as an RBAC exception.

## 2026-08-14 identity contract closure

- `GET /api/v1/users/me` now returns active role codes in addition to scope and real-time permissions. Role codes are display metadata; authorization continues to use permission codes loaded from the database on every authenticated request.
- Frontend sidebar entries are generated from one typed permission configuration shared by all existing browser roles. Platform, merchant administration, merchant operations, finance, warehouse, and customer-service responsibilities have focused navigation tests.
- An authenticated tenant user is redirected from the public quote page to formal quote creation only when `quote:create` is present. Other tenant roles return to `/app` instead of entering a predictable 403 route.
- Logistics-provider and carrier browser roles remain unsupported by the backend contract. The HMAC tracking callback remains server-to-server and has no frontend client.

## 2026-08-18 P3-06e merchant workbench security closure

- Billing batch/detail/reconciliation reads now carry the JWT `tenant_id` and caller `user_id` through Controller → Service → Mapper/XML. Merchant operators are restricted to active `sys_user_store_scope`; tenant administrators remain tenant-wide within their permissions. Missing or unauthorized resources use the existing uniform 404 boundary.
- Both browser tracking query paths re-check the shipment order's `tenant_id`, `store_id`, and caller store scope in the application service. URL identifiers cannot expand access.
- The frontend refresh path clears the in-memory session and stops further retries when refresh fails; the router receives a session-expired event and redirects to login. Browser Cookie-panel properties remain an environment BLOCKED item and are not claimed as verified.
- Existing write-operation audit chains remain append-only and record only redacted tenant/operator/resource/action/request/trace metadata and necessary business reason. Passwords, tokens, cookies, authorization headers, private keys, provider secrets, and evidence content are excluded.
- P3-06e acceptance must distinguish PASS, FAIL, BLOCKED, and not executed. A browser limitation is not evidence of a passed security scenario.
