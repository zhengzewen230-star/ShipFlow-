# 第二模块接口自动化测试用例范围追溯

状态：仅静态范围对齐；未连接数据库、未执行 SQL、未写入 `shipflow_qa`，也未修改或提交 Git。

## 1. 边界与业务流程

第二模块冻结的被测业务接口共 20 个：平台管理员创建、查询、更新和变更租户状态；租户管理员在其 `tenant_id` 范围内管理店铺、用户、用户角色、租户角色和角色权限。写操作的业务规则包括事务性、`Idempotency-Key` 重放/冲突、乐观锁 `version` 以及状态变更后的实时身份重载；跨租户、已删除或不存在的资源不得越权访问。

认证、CSRF 获取、登录、身份切换、运行时资源创建/提取及 API 恢复动作是测试前置步骤，不能计为这 20 个业务接口，也不应写成固定 QA 数据。当前 68 个场景本身没有 `/api/v1/auth/*` 方法加路径组合，只引用运行时 `ACCESS_TOKEN` 等占位符；因此没有认证/CSRF/登录场景可作为业务用例导入。

冻结的 20 个接口以“HTTP 方法 + 路由模板”计数；`{..._B_ID}` 是同一路由模板上的租户 B 测试数据，不是新业务接口。

## 2. 冻结接口清单

| 冻结编号 | 被测业务接口 | 参与方 |
|---|---|---|
| F01 | `POST /api/v1/platform/tenants` | 平台管理员 |
| F02 | `GET /api/v1/platform/tenants` | 平台管理员 |
| F03 | `GET /api/v1/platform/tenants/{tenantId}` | 平台管理员 |
| F04 | `PUT /api/v1/platform/tenants/{tenantId}` | 平台管理员 |
| F05 | `POST /api/v1/platform/tenants/{tenantId}/status` | 平台管理员 |
| F06 | `POST /api/v1/stores` | 租户管理员 |
| F07 | `GET /api/v1/stores` | 租户管理员 |
| F08 | `GET /api/v1/stores/{storeId}` | 租户管理员 |
| F09 | `PUT /api/v1/stores/{storeId}` | 租户管理员 |
| F10 | `POST /api/v1/stores/{storeId}/status` | 租户管理员 |
| F11 | `POST /api/v1/users` | 租户管理员 |
| F12 | `GET /api/v1/users` | 租户管理员 |
| F13 | `GET /api/v1/users/{userId}` | 租户管理员 |
| F14 | `PUT /api/v1/users/{userId}` | 租户管理员 |
| F15 | `POST /api/v1/users/{userId}/status` | 租户管理员 |
| F16 | `PUT /api/v1/users/{userId}/roles` | 租户管理员 |
| F17 | `GET /api/v1/roles` | 租户管理员 |
| F18 | `GET /api/v1/roles/{roleId}` | 租户管理员 |
| F19 | `GET /api/v1/permissions` | 租户管理员 |
| F20 | `PUT /api/v1/roles/{roleId}/permissions` | 租户管理员 |

## 3. 68 个场景逐条映射

分类说明：`NORMAL` 为正常流程；`ACCESS` 为未认证或权限不足；`CROSS_TENANT` 为租户隔离；`IDEMPOTENCY` 为同键重放/请求体冲突；`VERSION` 为乐观锁冲突；`VALIDATION` 为参数、唯一性、外键或作用域校验；`DISABLED` 为停用状态；`CONCURRENCY` 为并发同键。`CONTRACT_MISMATCH` 不属于冻结接口且不是认证/CSRF/登录前置。

| 场景 | 方法 + 路径 | 冻结归属 | 分类 | 处理 |
|---|---|---|---|---|
| TENANT-001 | POST `/api/v1/platform/tenants` | F01 | NORMAL | 候选 |
| TENANT-002 | GET `/api/v1/platform/tenants` | F02 | NORMAL | 候选 |
| TENANT-003 | GET `/api/v1/platform/tenants/${TENANT_A_ID}` | F03 | NORMAL | 候选 |
| TENANT-004 | PUT `/api/v1/platform/tenants/${TENANT_A_ID}` | F04 | NORMAL | 候选 |
| TENANT-005 | POST `/api/v1/platform/tenants/${TENANT_A_ID}/status` | F05 | NORMAL | 候选 |
| TENANT-006 | POST `/api/v1/platform/tenants` | F01 | ACCESS | 候选 |
| TENANT-007 | GET `/api/v1/platform/tenants` | F02 | ACCESS | 候选 |
| TENANT-008 | POST `/api/v1/platform/tenants` | F01 | ACCESS | 候选 |
| TENANT-009 | GET `/api/v1/platform/tenants` | F02 | ACCESS | 候选 |
| TENANT-010 | GET `/api/v1/platform/tenants/${TENANT_A_ID}` | F03 | ACCESS | 候选 |
| TENANT-011 | POST `/api/v1/platform/tenants` | F01 | VALIDATION | 候选 |
| TENANT-012 | POST `/api/v1/platform/tenants` | F01 | VALIDATION | 候选 |
| TENANT-013 | POST `/api/v1/platform/tenants` | F01 | IDEMPOTENCY | 候选 |
| TENANT-014 | POST `/api/v1/platform/tenants` | F01 | IDEMPOTENCY | 候选 |
| TENANT-015 | PUT `/api/v1/platform/tenants/${TENANT_A_ID}` | F04 | VERSION | 候选 |
| TENANT-016 | PUT `/api/v1/platform/tenants/${TENANT_A_ID}` | F04 | DISABLED | 候选，须冻结停用契约 |
| TENANT-017 | POST `/api/v1/platform/tenants` | F01 | CONCURRENCY | 候选，须隔离并发断言 |
| STORE-001 | POST `/api/v1/stores` | F06 | NORMAL | 候选 |
| STORE-002 | GET `/api/v1/stores` | F07 | NORMAL | 候选 |
| STORE-003 | GET `/api/v1/stores/${STORE_A_ID}` | F08 | NORMAL | 候选 |
| STORE-004 | PUT `/api/v1/stores/${STORE_A_ID}` | F09 | NORMAL | 候选 |
| STORE-005 | POST `/api/v1/stores/${STORE_A_ID}/status` | F10 | NORMAL | 候选 |
| STORE-006 | POST `/api/v1/stores` | F06 | ACCESS | 候选 |
| STORE-007 | GET `/api/v1/stores` | F07 | ACCESS | 候选 |
| STORE-008 | GET `/api/v1/stores/${STORE_B_ID}` | F08 | CROSS_TENANT | 候选 |
| STORE-009 | PUT `/api/v1/stores/${STORE_B_ID}` | F09 | CROSS_TENANT | 候选 |
| STORE-010 | POST `/api/v1/stores` | F06 | VALIDATION | 候选 |
| STORE-011 | POST `/api/v1/stores` | F06 | VALIDATION | 候选 |
| STORE-012 | POST `/api/v1/stores` | F06 | IDEMPOTENCY | 候选 |
| STORE-013 | POST `/api/v1/stores` | F06 | IDEMPOTENCY | 候选 |
| STORE-014 | PUT `/api/v1/stores/${STORE_A_ID}` | F09 | VERSION | 候选 |
| STORE-015 | PUT `/api/v1/stores/${STORE_A_ID}` | F09 | DISABLED | 候选，须冻结停用契约 |
| STORE-016 | GET `/api/v1/stores/${STORE_A_ID}` | F08 | DISABLED | 候选，须冻结停用读取契约 |
| STORE-017 | POST `/api/v1/stores` | F06 | VALIDATION | 候选 |
| USER-001 | POST `/api/v1/users` | F11 | NORMAL | 候选 |
| USER-002 | GET `/api/v1/users` | F12 | NORMAL | 候选 |
| USER-003 | GET `/api/v1/users/${USER_A_ID}` | F13 | NORMAL | 候选 |
| USER-004 | PUT `/api/v1/users/${USER_A_ID}` | F14 | NORMAL | 候选 |
| USER-005 | POST `/api/v1/users/${USER_A_ID}/status` | F15 | NORMAL | 候选 |
| USER-006 | PUT `/api/v1/users/${USER_A_ID}/roles` | F16 | NORMAL | 候选 |
| USER-007 | POST `/api/v1/users` | F11 | ACCESS | 候选 |
| USER-008 | GET `/api/v1/users` | F12 | ACCESS | 候选 |
| USER-009 | GET `/api/v1/users/${USER_B_ID}` | F13 | CROSS_TENANT | 候选 |
| USER-010 | PUT `/api/v1/users/${USER_B_ID}` | F14 | CROSS_TENANT | 候选 |
| USER-011 | POST `/api/v1/users` | F11 | VALIDATION | 候选 |
| USER-012 | POST `/api/v1/users` | F11 | VALIDATION | 候选 |
| USER-013 | POST `/api/v1/users` | F11 | IDEMPOTENCY | 候选 |
| USER-014 | POST `/api/v1/users` | F11 | IDEMPOTENCY | 候选 |
| USER-015 | PUT `/api/v1/users/${USER_A_ID}` | F14 | VERSION | 候选 |
| USER-016 | PUT `/api/v1/users/${USER_A_ID}/roles` | F16 | VALIDATION | 候选 |
| USER-017 | PUT `/api/v1/users/${USER_A_ID}/roles` | F16 | VALIDATION | 候选 |
| USER-018 | GET `/api/v1/users/me` | — | CONTRACT_MISMATCH | 移至认证/当前用户模块，或负责人将其补充为冻结接口 |
| USER-019 | GET `/api/v1/users/me` | — | CONTRACT_MISMATCH | 移至认证/当前用户模块，或负责人将其补充为冻结接口 |
| USER-020 | POST `/api/v1/users` | F11 | VALIDATION | 候选 |
| RBAC-001 | GET `/api/v1/roles` | F17 | NORMAL | 候选 |
| RBAC-002 | GET `/api/v1/roles/${ROLE_A_ID}` | F18 | NORMAL | 候选 |
| RBAC-003 | GET `/api/v1/permissions` | F19 | NORMAL | 候选 |
| RBAC-004 | PUT `/api/v1/roles/${ROLE_A_ID}/permissions` | F20 | NORMAL | 候选 |
| RBAC-005 | GET `/api/v1/roles` | F17 | ACCESS | 候选 |
| RBAC-006 | GET `/api/v1/roles` | F17 | ACCESS | 候选 |
| RBAC-007 | GET `/api/v1/roles/${ROLE_B_ID}` | F18 | CROSS_TENANT | 候选 |
| RBAC-008 | PUT `/api/v1/roles/${ROLE_B_ID}/permissions` | F20 | CROSS_TENANT | 候选 |
| RBAC-009 | PUT `/api/v1/roles/${ROLE_A_ID}/permissions` | F20 | ACCESS | 候选 |
| RBAC-010 | PUT `/api/v1/roles/${ROLE_A_ID}/permissions` | F20 | VALIDATION | 候选 |
| RBAC-011 | PUT `/api/v1/roles/${ROLE_A_ID}/permissions` | F20 | VERSION | 候选 |
| RBAC-012 | GET `/api/v1/roles/${ROLE_A_ID}` | F18 | DISABLED | 候选，须冻结停用读取契约 |
| RBAC-013 | PUT `/api/v1/roles/${ROLE_A_ID}/permissions` | F20 | DISABLED | 候选，须冻结停用契约 |
| RBAC-014 | PUT `/api/v1/roles/${ROLE_A_ID}/permissions` | F20 | VALIDATION | 候选 |

## 4. 方法加路径组合及归属（对“28 个”声明的核验）

对当前 `006` 草案逐条去重后，实际得到 **27** 个方法加路径组合，而非需求中提到的 28 个。下表完整列出这 27 个组合；不存在可诚实列出的第 28 个组合。差异来源待负责人确认，不能通过虚构路由或拆分同一路由的不同请求体来补足。

| 方法 + 路径组合 | 场景 | 归属 |
|---|---|---|
| POST `/api/v1/platform/tenants` | TENANT-001, 006, 008, 011-014, 017 | F01 |
| GET `/api/v1/platform/tenants` | TENANT-002, 007, 009 | F02 |
| GET `/api/v1/platform/tenants/${TENANT_A_ID}` | TENANT-003, 010 | F03 |
| PUT `/api/v1/platform/tenants/${TENANT_A_ID}` | TENANT-004, 015, 016 | F04 |
| POST `/api/v1/platform/tenants/${TENANT_A_ID}/status` | TENANT-005 | F05 |
| POST `/api/v1/stores` | STORE-001, 006, 010-013, 017 | F06 |
| GET `/api/v1/stores` | STORE-002, 007 | F07 |
| GET `/api/v1/stores/${STORE_A_ID}` | STORE-003, 016 | F08 |
| GET `/api/v1/stores/${STORE_B_ID}` | STORE-008 | F08；B 为同一路由模板的隔离数据 |
| PUT `/api/v1/stores/${STORE_A_ID}` | STORE-004, 014, 015 | F09 |
| PUT `/api/v1/stores/${STORE_B_ID}` | STORE-009 | F09；B 为同一路由模板的隔离数据 |
| POST `/api/v1/stores/${STORE_A_ID}/status` | STORE-005 | F10 |
| POST `/api/v1/users` | USER-001, 007, 011-014, 020 | F11 |
| GET `/api/v1/users` | USER-002, 008 | F12 |
| GET `/api/v1/users/${USER_A_ID}` | USER-003 | F13 |
| GET `/api/v1/users/${USER_B_ID}` | USER-009 | F13；B 为同一路由模板的隔离数据 |
| GET `/api/v1/users/me` | USER-018, 019 | CONTRACT_MISMATCH；不是认证/CSRF/登录前置，也不在 F01-F20 |
| PUT `/api/v1/users/${USER_A_ID}` | USER-004, 015 | F14 |
| PUT `/api/v1/users/${USER_B_ID}` | USER-010 | F14；B 为同一路由模板的隔离数据 |
| POST `/api/v1/users/${USER_A_ID}/status` | USER-005 | F15 |
| PUT `/api/v1/users/${USER_A_ID}/roles` | USER-006, 016, 017 | F16 |
| GET `/api/v1/roles` | RBAC-001, 005, 006 | F17 |
| GET `/api/v1/roles/${ROLE_A_ID}` | RBAC-002, 012 | F18 |
| GET `/api/v1/roles/${ROLE_B_ID}` | RBAC-007 | F18；B 为同一路由模板的隔离数据 |
| GET `/api/v1/permissions` | RBAC-003 | F19 |
| PUT `/api/v1/roles/${ROLE_A_ID}/permissions` | RBAC-004, 009-011, 013, 014 | F20 |
| PUT `/api/v1/roles/${ROLE_B_ID}/permissions` | RBAC-008 | F20；B 为同一路由模板的隔离数据 |

`/api/v1/users/me` 的两项不能视为认证或 CSRF 前置：前置只负责建立身份上下文，而这两项直接把“停用用户/角色后的实时拒绝”作为被测断言。因此应从第二模块导入范围剔除，或通过负责人变更冻结清单后再纳入。

## 5. 每个冻结接口的场景覆盖计数

“访问”列合并未认证和无权限；“校验”列合并参数、唯一性、外键和作用域。空白均为 0。

| 冻结接口 | 正常 | 访问 | 跨租户 | 幂等 | 版本 | 校验 | 停用 | 并发 | 合计 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| F01 租户创建 | 1 | 2 | 0 | 2 | 0 | 2 | 0 | 1 | 8 |
| F02 租户列表 | 1 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| F03 租户详情 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| F04 租户更新 | 1 | 0 | 0 | 0 | 1 | 0 | 1 | 0 | 3 |
| F05 租户状态 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| F06 店铺创建 | 1 | 1 | 0 | 2 | 0 | 3 | 0 | 0 | 7 |
| F07 店铺列表 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| F08 店铺详情 | 1 | 0 | 1 | 0 | 0 | 0 | 1 | 0 | 3 |
| F09 店铺更新 | 1 | 0 | 1 | 0 | 1 | 0 | 1 | 0 | 4 |
| F10 店铺状态 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| F11 用户创建 | 1 | 1 | 0 | 2 | 0 | 3 | 0 | 0 | 7 |
| F12 用户列表 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| F13 用户详情 | 1 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 2 |
| F14 用户更新 | 1 | 0 | 1 | 0 | 1 | 0 | 0 | 0 | 3 |
| F15 用户状态 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| F16 用户角色替换 | 1 | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 3 |
| F17 角色列表 | 1 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| F18 角色详情 | 1 | 0 | 1 | 0 | 0 | 0 | 1 | 0 | 3 |
| F19 权限字典 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| F20 角色权限替换 | 1 | 1 | 1 | 0 | 1 | 2 | 1 | 0 | 7 |
| 合计（冻结范围） | 20 | 12 | 6 | 6 | 5 | 12 | 5 | 1 | 66 |

## 6. 导入决策

- 作用域对齐候选：66 条，全部映射至 F01-F20。
- `CONTRACT_MISMATCH`：2 条（USER-018、USER-019），应删除、移至认证/当前用户模块，或由负责人把 `GET /api/v1/users/me` 加入冻结接口后重新评审。
- 当前已导入数量：**66**。正式导入文件为 `database/qa/007_seed_module2_frozen_20_api_test_cases.sql`，随后由 `database/qa/008_upgrade_module1_module2_api_test_cases_cn_final.sql` 完成中文最终化。目标表仅为 `shipflow_qa.api_test_case`，USER-018/019 继续排除，66 条均保持 `BLOCKED`。
- 被阻塞或需删除数量：**66**（执行前置仍被阻塞；USER-018/019 已排除，不属于已导入范围）。
- 正式执行仍被隔离环境、运行时资源/身份、仅通过 API 的恢复能力、业务库只读断言账户、停用状态和并发合同阻塞。只有真实 HTTP 与数据库双断言验证完成后，才可按精确 case_no 另行批准状态提升。

## 7. 待负责人确认

1. `GET /api/v1/users/me` 是否应加入第二模块冻结接口；若否，USER-018/019 是否移交认证模块。
2. 停用租户、店铺、用户和角色后的每个接口状态码/错误码是否已冻结，尤其是读取与旧会话重载。
3. 66 条范围匹配场景何时具备隔离业务库、API-only 恢复和最小只读数据库断言账户，从而允许生成正式导入脚本。
