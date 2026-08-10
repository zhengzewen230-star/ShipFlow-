-- 第一、第二模块 API 自动化用例中文最终化升级。
-- 约束：仅更新 shipflow_qa.api_test_case；不含 DDL、DELETE、迁移或业务表操作。
-- 本文件可重复执行：更新由 case_no 定位，且仅处理既有模块记录。
-- 执行账户应仅具有 SELECT、INSERT、UPDATE 权限；客户端必须在失败时回滚。

START TRANSACTION;

-- 第一模块保持已验证的认证请求模板和执行合同不变；仅为纯占位符的数据依赖补齐中文说明。
-- 第二模块将英文草稿元数据升级为中文最终测试设计；运行状态继续保持 BLOCKED。
UPDATE shipflow_qa.api_test_case
SET
    data_dependency = CONCAT('运行时变量：', data_dependency),
    updated_at = UTC_TIMESTAMP(3)
WHERE case_no LIKE 'AUTH-%'
  AND data_dependency REGEXP '^[[:ascii:]]*$'
  AND data_dependency NOT LIKE '运行时变量：%';

UPDATE shipflow_qa.api_test_case
SET
    title = CASE case_no
        WHEN 'TENANT-001' THEN '平台管理员创建租户成功'
        WHEN 'TENANT-002' THEN '平台管理员分页查询租户成功'
        WHEN 'TENANT-003' THEN '平台管理员查询租户详情成功'
        WHEN 'TENANT-004' THEN '平台管理员更新租户名称成功'
        WHEN 'TENANT-005' THEN '平台管理员变更租户状态成功'
        WHEN 'TENANT-006' THEN '未认证创建租户被拒绝'
        WHEN 'TENANT-007' THEN '未认证查询租户列表被拒绝'
        WHEN 'TENANT-008' THEN '租户管理员创建平台租户被拒绝'
        WHEN 'TENANT-009' THEN '租户管理员查询平台租户列表被拒绝'
        WHEN 'TENANT-010' THEN '租户管理员查询平台租户详情被拒绝'
        WHEN 'TENANT-011' THEN '创建租户请求参数非法'
        WHEN 'TENANT-012' THEN '创建重复租户编码被拒绝'
        WHEN 'TENANT-013' THEN '相同幂等键重放租户创建成功'
        WHEN 'TENANT-014' THEN '相同幂等键请求体不一致创建冲突'
        WHEN 'TENANT-015' THEN '过期版本更新租户发生版本冲突'
        WHEN 'TENANT-016' THEN '已停用租户禁止更新'
        WHEN 'TENANT-017' THEN '并发相同幂等键仅创建一个租户'
        WHEN 'STORE-001' THEN '租户管理员创建本租户店铺成功'
        WHEN 'STORE-002' THEN '租户管理员分页查询本租户店铺成功'
        WHEN 'STORE-003' THEN '租户管理员查询本租户店铺详情成功'
        WHEN 'STORE-004' THEN '租户管理员更新本租户店铺成功'
        WHEN 'STORE-005' THEN '租户管理员停用本租户店铺成功'
        WHEN 'STORE-006' THEN '未认证创建店铺被拒绝'
        WHEN 'STORE-007' THEN '无店铺读取权限查询店铺被拒绝'
        WHEN 'STORE-008' THEN '租户管理员查询其他租户店铺被拒绝'
        WHEN 'STORE-009' THEN '租户管理员更新其他租户店铺被拒绝'
        WHEN 'STORE-010' THEN '创建店铺请求参数非法'
        WHEN 'STORE-011' THEN '创建重复店铺编码被拒绝'
        WHEN 'STORE-012' THEN '相同幂等键重放店铺创建成功'
        WHEN 'STORE-013' THEN '相同幂等键请求体不一致创建冲突'
        WHEN 'STORE-014' THEN '过期版本更新店铺发生版本冲突'
        WHEN 'STORE-015' THEN '已停用店铺禁止更新'
        WHEN 'STORE-016' THEN '已停用店铺查询可见性合同'
        WHEN 'STORE-017' THEN '店铺租户归属和外键校验'
        WHEN 'USER-001' THEN '租户管理员创建本租户用户成功'
        WHEN 'USER-002' THEN '租户管理员分页查询本租户用户成功'
        WHEN 'USER-003' THEN '租户管理员查询本租户用户详情成功'
        WHEN 'USER-004' THEN '租户管理员更新本租户用户成功'
        WHEN 'USER-005' THEN '租户管理员停用本租户用户成功'
        WHEN 'USER-006' THEN '租户管理员为本租户用户分配角色成功'
        WHEN 'USER-007' THEN '未认证创建用户被拒绝'
        WHEN 'USER-008' THEN '无用户管理权限创建用户被拒绝'
        WHEN 'USER-009' THEN '租户管理员查询其他租户用户被拒绝'
        WHEN 'USER-010' THEN '租户管理员更新其他租户用户被拒绝'
        WHEN 'USER-011' THEN '创建用户请求参数非法'
        WHEN 'USER-012' THEN '创建重复用户名被拒绝'
        WHEN 'USER-013' THEN '相同幂等键重放用户创建成功'
        WHEN 'USER-014' THEN '相同幂等键请求体不一致创建冲突'
        WHEN 'USER-015' THEN '过期版本更新用户发生版本冲突'
        WHEN 'USER-016' THEN '未知或跨租户角色分配被拒绝'
        WHEN 'USER-017' THEN '平台角色作用域越权被拒绝'
        WHEN 'USER-020' THEN '用户租户外键校验失败'
        WHEN 'RBAC-001' THEN '租户管理员查询本租户角色列表成功'
        WHEN 'RBAC-002' THEN '租户管理员查询本租户角色详情成功'
        WHEN 'RBAC-003' THEN '租户管理员查询权限列表成功'
        WHEN 'RBAC-004' THEN '租户管理员替换本租户角色权限成功'
        WHEN 'RBAC-005' THEN '未认证查询角色列表被拒绝'
        WHEN 'RBAC-006' THEN '无角色读取权限查询角色被拒绝'
        WHEN 'RBAC-007' THEN '租户管理员查询其他租户角色被拒绝'
        WHEN 'RBAC-008' THEN '租户管理员更新其他租户角色被拒绝'
        WHEN 'RBAC-009' THEN '无角色管理权限替换角色权限被拒绝'
        WHEN 'RBAC-010' THEN '不存在权限外键校验失败'
        WHEN 'RBAC-011' THEN '过期版本替换角色权限发生版本冲突'
        WHEN 'RBAC-012' THEN '已停用角色查询可见性合同'
        WHEN 'RBAC-013' THEN '已停用角色替换权限被拒绝'
        WHEN 'RBAC-014' THEN '角色作用域和租户外键校验失败'
        ELSE title
    END,
    precondition = CASE
        WHEN case_no IN ('TENANT-006', 'TENANT-007', 'STORE-006', 'USER-007', 'RBAC-005')
            THEN '动态前置：隔离 HTTP 环境已启动；请求不携带 Authorization、Cookie 或 CSRF 凭据；记录响应错误码和追踪标识。'
        WHEN case_no IN ('TENANT-008', 'TENANT-009', 'TENANT-010')
            THEN '动态前置：已创建平台管理员和租户 A 管理员身份；租户 A 管理员仅拥有本租户 RBAC 权限；目标平台租户由平台管理员创建。'
        WHEN case_no IN ('STORE-007', 'USER-008', 'RBAC-006')
            THEN '动态前置：已创建租户 A 低权限身份及租户 A；该身份不具有本接口所需权限码；请求资源归属租户 A。'
        WHEN case_no IN ('STORE-008', 'STORE-009', 'USER-009', 'USER-010', 'USER-016', 'USER-017', 'RBAC-007', 'RBAC-008')
            THEN '动态前置：已创建隔离的租户 A、租户 B 及各自管理员；请求者为租户 A 管理员，目标资源归属租户 B；两租户均使用本次运行唯一前缀。'
        WHEN case_no IN ('TENANT-013', 'TENANT-014', 'TENANT-017', 'STORE-012', 'STORE-013', 'STORE-016', 'USER-013', 'USER-014', 'RBAC-011', 'RBAC-012')
            THEN '动态前置：已准备可写身份和唯一测试前缀；同一幂等键及两份可区分请求体由运行时上下文生成；请求均带 X-Request-Id 与 Idempotency-Key。'
        WHEN case_no IN ('TENANT-015', 'TENANT-016', 'STORE-014', 'STORE-015', 'USER-015', 'RBAC-013')
            THEN '动态前置：已创建目标资源并读取当前 version；运行时构造过期版本或停用状态；请求身份仅操作所属租户资源。'
        WHEN case_no IN ('TENANT-011', 'TENANT-012', 'STORE-010', 'STORE-011', 'USER-011', 'USER-012', 'RBAC-010', 'RBAC-020')
            THEN '动态前置：已创建所需隔离租户和管理身份；运行时生成非法字段、重复业务键或不存在权限标识；不得复用其他测试资源。'
        WHEN case_no LIKE 'TENANT-%'
            THEN '动态前置：平台管理员身份、隔离 HTTP 环境和唯一运行前缀已就绪；目标租户由本用例运行时创建或定位。'
        WHEN case_no LIKE 'STORE-%'
            THEN '动态前置：租户 A 管理员、租户 A、隔离 HTTP 环境和唯一运行前缀已就绪；店铺资源归属租户 A。'
        WHEN case_no LIKE 'USER-%'
            THEN '动态前置：租户 A 管理员、租户 A、最小角色和唯一运行前缀已就绪；用户资源归属租户 A。'
        WHEN case_no LIKE 'RBAC-%'
            THEN '动态前置：租户 A 管理员、租户 A、角色和权限资源及唯一运行前缀已就绪；角色权限均归属租户 A。'
        ELSE precondition
    END,
    test_type = CASE
        WHEN case_no IN ('TENANT-013', 'TENANT-014', 'TENANT-015', 'TENANT-016', 'TENANT-017', 'STORE-012', 'STORE-013', 'STORE-014', 'STORE-015', 'STORE-016', 'USER-013', 'USER-014', 'USER-015', 'RBAC-011', 'RBAC-012', 'RBAC-013') THEN 'STATE_FLOW'
        WHEN case_no IN ('STORE-008', 'STORE-009', 'USER-009', 'USER-010', 'USER-016', 'USER-017', 'RBAC-007', 'RBAC-008') THEN 'SECURITY'
        ELSE test_type
    END,
    priority = CASE
        WHEN case_no IN ('TENANT-008', 'TENANT-009', 'TENANT-010', 'TENANT-013', 'TENANT-014', 'TENANT-017', 'STORE-008', 'STORE-009', 'USER-009', 'USER-010', 'USER-016', 'USER-017', 'RBAC-007', 'RBAC-008', 'RBAC-009') THEN 'P0'
        ELSE 'P1'
    END,
    assertions = CASE
        WHEN case_no IN ('TENANT-006', 'TENANT-007', 'STORE-006', 'USER-007', 'RBAC-005')
            THEN JSON_ARRAY(JSON_OBJECT('source', 'header', 'path', 'X-Trace-Id', 'operator', 'exists', 'expected', NULL), JSON_OBJECT('source', 'json', 'path', '$.success', 'operator', 'eq', 'expected', FALSE), JSON_OBJECT('source', 'json', 'path', '$.code', 'operator', 'eq', 'expected', expected_error_code))
        WHEN case_no IN ('TENANT-008', 'TENANT-009', 'TENANT-010', 'STORE-007', 'STORE-008', 'STORE-009', 'USER-008', 'USER-009', 'USER-010', 'USER-016', 'USER-017', 'RBAC-006', 'RBAC-007', 'RBAC-008', 'RBAC-009')
            THEN JSON_ARRAY(JSON_OBJECT('source', 'header', 'path', 'X-Trace-Id', 'operator', 'exists', 'expected', NULL), JSON_OBJECT('source', 'json', 'path', '$.success', 'operator', 'eq', 'expected', FALSE), JSON_OBJECT('source', 'json', 'path', '$.code', 'operator', 'eq', 'expected', expected_error_code), JSON_OBJECT('source', 'database', 'path', 'tenant_scope', 'operator', 'unchanged', 'expected', TRUE))
        WHEN case_no IN ('TENANT-013', 'TENANT-017', 'STORE-012', 'USER-013')
            THEN JSON_ARRAY(JSON_OBJECT('source', 'header', 'path', 'X-Trace-Id', 'operator', 'exists', 'expected', NULL), JSON_OBJECT('source', 'json', 'path', '$.success', 'operator', 'eq', 'expected', TRUE), JSON_OBJECT('source', 'database', 'path', 'idempotency_effect', 'operator', 'single_effect', 'expected', TRUE))
        WHEN case_no IN ('TENANT-014', 'STORE-013', 'USER-014', 'RBAC-012', 'TENANT-015', 'STORE-014', 'USER-015', 'RBAC-013')
            THEN JSON_ARRAY(JSON_OBJECT('source', 'header', 'path', 'X-Trace-Id', 'operator', 'exists', 'expected', NULL), JSON_OBJECT('source', 'json', 'path', '$.success', 'operator', 'eq', 'expected', FALSE), JSON_OBJECT('source', 'json', 'path', '$.code', 'operator', 'eq', 'expected', expected_error_code), JSON_OBJECT('source', 'database', 'path', 'version_no', 'operator', 'unchanged', 'expected', TRUE))
        ELSE JSON_ARRAY(JSON_OBJECT('source', 'header', 'path', 'X-Trace-Id', 'operator', 'exists', 'expected', NULL), JSON_OBJECT('source', 'json', 'path', '$.success', 'operator', 'eq', 'expected', expected_status BETWEEN 200 AND 299), JSON_OBJECT('source', 'json', 'path', '$.code', 'operator', 'eq', 'expected', expected_error_code))
    END,
    data_dependency = CASE
        WHEN case_no LIKE 'TENANT-%' THEN '运行时动态创建或定位租户；仅断言租户管理范围内的数据变化、版本和幂等副作用；清理由后端隔离环境的测试夹具逆序完成。'
        WHEN case_no LIKE 'STORE-%' THEN '运行时动态创建租户及店铺；仅断言店铺 tenant_id 隔离、状态、版本和幂等副作用；清理由夹具逆序完成。'
        WHEN case_no LIKE 'USER-%' THEN '运行时动态创建租户、用户和最小角色；仅断言用户 tenant_id、角色关系、版本和幂等副作用；清理由夹具逆序完成。'
        WHEN case_no LIKE 'RBAC-%' THEN '运行时动态创建租户、角色和权限关系；仅断言角色 tenant_id、授权关系、版本和幂等副作用；清理由夹具逆序完成。'
        ELSE data_dependency
    END,
    tags = JSON_ARRAY('第二模块', 'BLOCKED', '动态前置条件', '隔离数据', '数据库断言'),
    automation_status = 'BLOCKED',
    updated_at = UTC_TIMESTAMP(3)
WHERE module IN ('TENANT', 'STORE', 'USER', 'RBAC')
  AND case_no IN (
    'TENANT-001','TENANT-002','TENANT-003','TENANT-004','TENANT-005','TENANT-006','TENANT-007','TENANT-008','TENANT-009','TENANT-010','TENANT-011','TENANT-012','TENANT-013','TENANT-014','TENANT-015','TENANT-016','TENANT-017',
    'STORE-001','STORE-002','STORE-003','STORE-004','STORE-005','STORE-006','STORE-007','STORE-008','STORE-009','STORE-010','STORE-011','STORE-012','STORE-013','STORE-014','STORE-015','STORE-016','STORE-017',
    'USER-001','USER-002','USER-003','USER-004','USER-005','USER-006','USER-007','USER-008','USER-009','USER-010','USER-011','USER-012','USER-013','USER-014','USER-015','USER-016','USER-017','USER-020',
    'RBAC-001','RBAC-002','RBAC-003','RBAC-004','RBAC-005','RBAC-006','RBAC-007','RBAC-008','RBAC-009','RBAC-010','RBAC-011','RBAC-012','RBAC-013','RBAC-014'
  )
  AND (
      title REGEXP '^[[:ascii:]]*$'
      OR precondition REGEXP '^[[:ascii:]]*$'
      OR data_dependency REGEXP '^[[:ascii:]]*$'
  );

COMMIT;
