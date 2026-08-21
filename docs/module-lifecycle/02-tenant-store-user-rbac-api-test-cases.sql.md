# 第二模块 QA 用例 SQL 草案

> 本文件只是设计草案，不是可执行脚本。本阶段未连接数据库、未执行 SQL、未写入 `shipflow_qa`、未修改 `shipflow`，也没有对任何业务库执行迁移、删除、更新或插入。

## 1. 使用边界

- 目标表契约：`shipflow_qa.api_test_case`，字段和 JSON 执行契约参照 `database/qa/001_create_api_test_case.sql`、`002_seed_auth_api_test_cases.sql`、`003_upgrade_api_test_case_execution_contract.sql`。
- 草案中的 `<...>` 全部是占位符，不能直接替换后盲目执行。
- 密码只能使用运行时安全环境变量引用，例如 `${SHIPFLOW_PLATFORM_TEST_PASSWORD}`；不得把密码写入 `request_body_template`、SQL、日志或文档。
- `ACCESS_TOKEN`、`XSRF_TOKEN`、`REFRESH_COOKIE` 只允许作为运行时 context 名称，不允许填入真实值。
- 所有 `<UUID>`、`<IDEMPOTENCY_KEY>`、租户编码、店铺编码、用户名和请求体摘要必须由运行时生成。

## 2. 草案模板（仅示意）

下面是字段形状示意，不含真实值，也不应直接执行。正式生成器必须对 JSON 做参数化转义，并使用当前 QA 表的完整列清单。

```sql
-- 伪代码示意：禁止直接执行
INSERT INTO api_test_case (
    case_no, module, title, test_type, priority, precondition,
    http_method, request_path, headers_template, cookie_template,
    request_body_template, expected_status, expected_error_code,
    assertions, data_dependency, enabled, setup_steps, extractors,
    teardown_steps, tags, execution_order, automation_status,
    environment_scope
) VALUES (
    '<CASE_NO>',
    '<tenant|store|user|rbac>',
    '<TITLE>',
    '<positive|negative|security|concurrency>',
    '<PRECONDITION>',
    '<GET|POST|PUT>',
    '<PATH_WITH_${RUNTIME_ID}>',
    '{"Authorization":"Bearer ${ACCESS_TOKEN}","X-XSRF-TOKEN":"${XSRF_TOKEN}","Idempotency-Key":"${IDEMPOTENCY_KEY}"}',
    '{"XSRF-TOKEN":"${XSRF_TOKEN}"}',
    '<JSON_TEMPLATE_WITH_${UUID}>',
    <EXPECTED_STATUS>,
    '<EXPECTED_ERROR_OR_NULL>',
    '[{"source":"json","path":"$.success","operator":"eq","expected":true}]',
    '<RUNTIME_RESOURCE_GRAPH>',
    0,
    '[...setup actions...]',
    '[...extractors...]',
    '[...API teardown actions...]',
    '["phase2","<module>"]',
    <ORDER>,
    'DESIGN_ONLY',
    '<local|ci>'
);
```

本段是历史草案建议，现已失效。正式记录使用 `automation_status='BLOCKED'`：66 条用例已导入 `shipflow_qa.api_test_case`，并已完成中文最终化。不得把尚未完成真实 HTTP 与数据库双断言的用例标记为 `READY`。

## 3. 用例编号和生成映射

主设计文档预计 68 个用例：`TENANT-001..017`（17）、`STORE-001..017`（17）、`USER-001..020`（20）、`RBAC-001..014`（14）。建议 SQL 草案生成时一行对应一个编号，保持 `execution_order` 分组：

- 100-199：租户 SETUP、正常、校验、幂等、版本和停用。
- 200-299：店铺正常、权限、跨租户、幂等、版本和状态。
- 300-399：用户正常、权限、跨租户、幂等、版本、角色和停用。
- 400-499：RBAC 正常、权限、跨租户、版本、停用和外键。
- 900-999：运行时 teardown/清理审计，仅在框架支持时生成；不创建固定资源。

## 4. 可数据库驱动的草案字段策略

适合以 `api_test_case` 固定保存契约的字段：case 编号、接口、HTTP 方法、路径模板、预期状态码、错误码、响应 envelope 断言、权限标签、执行顺序和环境范围。

适合保存为占位符的字段：`${TENANT_A_ID}`、`${STORE_A_ID}`、`${USER_A_ID}`、`${ROLE_A_ID}`、`${VERSION}`、`${UUID}`、`${IDEMPOTENCY_KEY}`、`${ACCESS_TOKEN}`、`${XSRF_TOKEN}`。这些值必须由 setup/extractor/context 产生，不能由 SQL 预填固定值。

建议的 setup/extractor 逻辑名称（当前执行器尚不支持，仅作为设计）：`create_tenant_via_api`、`login_profile`、`create_store_via_api`、`create_user_via_api`、`list_roles_and_save_role`、`create_runtime_idempotency_key`、`snapshot_permissions`、`snapshot_user_roles`、`disable_via_api`、`restore_via_api`。

## 5. 必须运行时动态生成的用例

- `TENANT-001/013/014/017`：租户、幂等键冲突和并发请求。
- `STORE-001/008/009/012/013/014/015/017`：店铺 ID、双租户资源、状态和版本。
- `USER-001/006/009/010/013/014/015/016/017/018/019/020`：用户、角色关系、状态和跨租户归属。
- `RBAC-001/002/004/007/008/010/011/012/013/014`：角色、权限快照、版本和停用状态。

这些用例不能依赖上一轮资源 ID，也不能用固定 SQL 写入业务表。若 QA 只允许数据库驱动读取 `api_test_case`，资源准备必须由测试 fixture API 完成。

## 6. 不能安全写入固定 QA 数据的用例

以下用例即便有 `api_test_case` 行，也不能安全地预写固定资源关系：

1. 停用平台租户、共享管理员、共享用户或共享系统角色的场景。
2. 修改 `PLATFORM_ADMIN` 权限或平台作用域角色权限的场景。
3. 双租户跨租户资源和动态外键校验场景。
4. 相同幂等键并发、版本竞争、同请求体/冲突请求体重放场景。
5. 依赖旧 Access Token 验证用户、角色、租户停用实时拒绝的场景。
6. 需要保存真实密码、Token、Cookie、HMAC、私钥或完整连接信息的任何场景。

上述场景只能在运行时创建隔离资源，并通过业务 API 恢复或登记待人工处理。当前没有通用删除 API，因此不设计自动 DELETE 或 SQL teardown。

## 7. 尚未批准的执行项

- 不执行 `USE`、`INSERT`、`UPDATE`、`DELETE`、迁移或任何数据库连接。
- 历史 `DESIGN_ONLY` 草案不再作为执行来源；当前 66 条 `BLOCKED` 用例已导入 `shipflow_qa.api_test_case`。
- 不修改 Java、Python、Jenkinsfile、OpenAPI 或数据库脚本。
- 不提交、不推送 Git。

真实执行前仍需冻结停用后 401/403/422 规则、幂等重放状态码、更新接口是否实际支持幂等键、角色权限允许集合和仅通过 API 的清理授权范围。业务库应为 `shipflow_http_test`；`shipflow_qa` 只保存用例定义。
