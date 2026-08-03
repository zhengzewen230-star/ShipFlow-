USE shipflow;

-- 本脚本只读验证，不包含密码，不修改表结构和业务数据。

SELECT 'database_charset' AS check_name, default_character_set_name AS actual_charset, default_collation_name AS actual_collation,
       CASE WHEN default_character_set_name = 'utf8mb4' AND default_collation_name = 'utf8mb4_0900_ai_ci' THEN 'PASS' ELSE 'FAIL' END AS result
FROM information_schema.schemata WHERE schema_name = DATABASE();

SELECT 'table_count' AS check_name, COUNT(*) AS actual_value, 30 AS expected_value,
       CASE WHEN COUNT(*) = 30 THEN 'PASS' ELSE 'FAIL' END AS result
FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';

SELECT 'table_collation' AS check_name, COUNT(*) AS total_tables,
       COALESCE(SUM(table_collation <> 'utf8mb4_0900_ai_ci'), 0) AS nonstandard_tables,
       CASE WHEN COUNT(*) = 30 AND COALESCE(SUM(table_collation <> 'utf8mb4_0900_ai_ci'), 0) = 0 THEN 'PASS' ELSE 'FAIL' END AS result
FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';

SELECT 'user_count' AS check_name, COUNT(*) AS actual_value, 10 AS expected_value,
       CASE WHEN COUNT(*) = 10 THEN 'PASS' ELSE 'FAIL' END AS result FROM sys_user;
SELECT 'role_count' AS check_name, COUNT(*) AS actual_value, 10 AS expected_value,
       CASE WHEN COUNT(*) = 10 THEN 'PASS' ELSE 'FAIL' END AS result FROM sys_role;
SELECT 'permission_count' AS check_name, COUNT(*) AS actual_value, 10 AS expected_value,
       CASE WHEN COUNT(*) = 10 THEN 'PASS' ELSE 'FAIL' END AS result FROM sys_permission;
SELECT 'store_count' AS check_name, COUNT(*) AS actual_value, 4 AS expected_value,
       CASE WHEN COUNT(*) = 4 THEN 'PASS' ELSE 'FAIL' END AS result FROM merchant_store;
SELECT 'provider_count' AS check_name, COUNT(*) AS actual_value, 2 AS expected_value,
       CASE WHEN COUNT(*) = 2 THEN 'PASS' ELSE 'FAIL' END AS result FROM logistics_provider;
SELECT 'channel_count' AS check_name, COUNT(*) AS actual_value, 4 AS expected_value,
       CASE WHEN COUNT(*) = 4 THEN 'PASS' ELSE 'FAIL' END AS result FROM logistics_channel;
SELECT 'service_country_count' AS check_name, COUNT(*) AS actual_value, 7 AS expected_value,
       CASE WHEN COUNT(*) = 7 THEN 'PASS' ELSE 'FAIL' END AS result FROM logistics_channel_service_country;
SELECT 'price_rule_count' AS check_name, COUNT(*) AS actual_value, 5 AS expected_value,
       CASE WHEN COUNT(*) = 5 THEN 'PASS' ELSE 'FAIL' END AS result FROM price_rule;
SELECT 'price_rule_tier_count' AS check_name, COUNT(*) AS actual_value, 10 AS expected_value,
       CASE WHEN COUNT(*) = 10 THEN 'PASS' ELSE 'FAIL' END AS result FROM price_rule_tier;

SELECT 'active_channel_without_price_rule' AS check_name, COUNT(*) AS actual_value, 0 AS expected_value,
       CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result
FROM logistics_channel c
WHERE c.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM price_rule r WHERE r.channel_id = c.id AND r.status IN ('DRAFT', 'PUBLISHED'));

SELECT 'finance_role_permissions' AS check_name, COUNT(*) AS actual_value, 6 AS expected_value,
       CASE WHEN COUNT(*) = 6 THEN 'PASS' ELSE 'FAIL' END AS result
FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code = 'FINANCE_OPERATOR'
  AND p.permission_code IN ('finance:bill-import', 'finance:reconcile', 'audit:read');

SELECT 'mock_callback_permission' AS check_name, COUNT(*) AS actual_value, 1 AS expected_value,
       CASE WHEN COUNT(*) = 1 THEN 'PASS' ELSE 'FAIL' END AS result
FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code = 'MOCK_LOGISTICS_SYSTEM' AND p.permission_code = 'tracking:callback';

SELECT 'required_unique_indexes' AS check_name, COUNT(DISTINCT index_name) AS actual_value, 18 AS expected_value,
       CASE WHEN COUNT(DISTINCT index_name) = 18 THEN 'PASS' ELSE 'FAIL' END AS result
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND non_unique = 0
  AND index_name IN ('uk_user_scope_username','uk_role_scope_code','uk_price_rule_channel_version','uk_quote_tenant_no',
    'uk_order_quote','uk_order_tenant_idempotency','uk_package_order','uk_address_order_type','uk_adjustment_measurement',
    'uk_tracking_provider_no_event','uk_bill_batch_tenant_provider_hash','uk_bill_detail_tenant_provider_no',
    'uk_reconciliation_bill_detail','uk_outbound_order','uk_outbound_provider_tracking',
    'uk_api_idempotency_scope_operation_key','uk_refresh_token_hash','uk_refresh_previous_session');

SELECT 'support_tables_exist' AS check_name, COUNT(*) AS actual_value, 2 AS expected_value,
       CASE WHEN COUNT(*) = 2 THEN 'PASS' ELSE 'FAIL' END AS result
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_type = 'BASE TABLE'
  AND table_name IN ('api_idempotency_record', 'auth_refresh_session');

SELECT 'support_table_collation' AS check_name, COUNT(*) AS actual_value, 2 AS expected_value,
       CASE WHEN COUNT(*) = 2 AND SUM(table_collation = 'utf8mb4_0900_ai_ci') = 2 THEN 'PASS' ELSE 'FAIL' END AS result
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('api_idempotency_record', 'auth_refresh_session');

SELECT 'refresh_foreign_keys' AS check_name, COUNT(*) AS actual_value, 3 AS expected_value,
       CASE WHEN COUNT(*) = 3 THEN 'PASS' ELSE 'FAIL' END AS result
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name = 'auth_refresh_session'
  AND constraint_type = 'FOREIGN KEY'
  AND constraint_name IN ('fk_refresh_user', 'fk_refresh_tenant', 'fk_refresh_previous');

SELECT 'support_status_checks' AS check_name, COUNT(*) AS actual_value, 2 AS expected_value,
       CASE WHEN COUNT(*) = 2 THEN 'PASS' ELSE 'FAIL' END AS result
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_type = 'CHECK'
  AND ((table_name = 'api_idempotency_record' AND constraint_name = 'chk_api_idempotency_status')
    OR (table_name = 'auth_refresh_session' AND constraint_name = 'chk_refresh_status'));

SELECT 'approved_order_status_values' AS check_name, COUNT(*) AS actual_value,
       CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result
FROM shipment_order
WHERE current_status NOT IN ('DRAFT','PENDING_INBOUND','INBOUND','PENDING_PRICE_CONFIRMATION','READY_FOR_OUTBOUND',
    'OUTBOUND','IN_TRANSIT','DELIVERED','CANCELLED','RETURNED','LOST');

SELECT 'support_table_comments' AS check_name, COUNT(*) AS actual_value, 2 AS expected_value,
       CASE WHEN COUNT(*) = 2 THEN 'PASS' ELSE 'FAIL' END AS result
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND ((table_name = 'api_idempotency_record' AND table_comment = '通用API幂等记录')
    OR (table_name = 'auth_refresh_session' AND table_comment = 'Refresh Token会话摘要'));

WITH expected_comments (table_name, column_name, expected_comment) AS (
    SELECT 'api_idempotency_record', 'id', '幂等记录主键' UNION ALL
    SELECT 'api_idempotency_record', 'scope_tenant_id', '幂等作用域，0表示平台作用域' UNION ALL
    SELECT 'api_idempotency_record', 'operation_id', '接口操作标识' UNION ALL
    SELECT 'api_idempotency_record', 'idempotency_key', '请求幂等键' UNION ALL
    SELECT 'api_idempotency_record', 'http_method', 'HTTP方法' UNION ALL
    SELECT 'api_idempotency_record', 'request_path', '规范化请求路径，用于摘要和审计' UNION ALL
    SELECT 'api_idempotency_record', 'request_hash', '规范化请求摘要' UNION ALL
    SELECT 'api_idempotency_record', 'processing_status', '处理状态' UNION ALL
    SELECT 'api_idempotency_record', 'response_status', '原始响应HTTP状态码' UNION ALL
    SELECT 'api_idempotency_record', 'response_body', '脱敏后的响应体' UNION ALL
    SELECT 'api_idempotency_record', 'resource_type', '资源类型' UNION ALL
    SELECT 'api_idempotency_record', 'resource_id', '资源ID' UNION ALL
    SELECT 'api_idempotency_record', 'expires_at', '幂等记录过期时间，UTC' UNION ALL
    SELECT 'api_idempotency_record', 'created_at', '创建时间，UTC' UNION ALL
    SELECT 'api_idempotency_record', 'updated_at', '更新时间，UTC' UNION ALL
    SELECT 'auth_refresh_session', 'id', '刷新会话主键' UNION ALL
    SELECT 'auth_refresh_session', 'user_id', '用户ID' UNION ALL
    SELECT 'auth_refresh_session', 'tenant_id', '租户ID，平台用户可为空' UNION ALL
    SELECT 'auth_refresh_session', 'token_hash', 'Refresh Token摘要，不保存明文' UNION ALL
    SELECT 'auth_refresh_session', 'family_id', 'Refresh Token族ID' UNION ALL
    SELECT 'auth_refresh_session', 'previous_session_id', '前一刷新会话ID' UNION ALL
    SELECT 'auth_refresh_session', 'status', '会话状态' UNION ALL
    SELECT 'auth_refresh_session', 'expires_at', '过期时间，UTC' UNION ALL
    SELECT 'auth_refresh_session', 'revoked_at', '撤销时间，UTC' UNION ALL
    SELECT 'auth_refresh_session', 'created_at', '创建时间，UTC' UNION ALL
    SELECT 'auth_refresh_session', 'updated_at', '更新时间，UTC'
), comment_check AS (
    SELECT e.table_name, e.column_name, e.expected_comment, c.column_comment
    FROM expected_comments e
    LEFT JOIN information_schema.columns c
      ON c.table_schema = DATABASE()
     AND c.table_name = e.table_name
     AND c.column_name = e.column_name
)
SELECT 'support_column_comments' AS check_name,
       SUM(column_comment = expected_comment) AS actual_value,
       COUNT(*) AS expected_value,
       CASE WHEN COUNT(*) = 26 AND SUM(column_comment = expected_comment) = 26 THEN 'PASS' ELSE 'FAIL' END AS result
FROM comment_check;

SELECT 'support_comment_mojibake' AS check_name,
       COUNT(*) AS suspicious_comments,
       CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('api_idempotency_record', 'auth_refresh_session')
  AND column_comment <> ''
  AND (column_comment REGEXP '[ÃÂ鍚璇閿鍐绯鎴渄]' OR column_comment NOT REGEXP '[一-龥]');

SELECT 'key_comment_values' AS check_name,
       SUM(table_name = 'api_idempotency_record' AND column_name = 'request_path'
           AND column_comment = '规范化请求路径，用于摘要和审计')
       + SUM(table_name = 'auth_refresh_session' AND column_name = 'token_hash'
           AND column_comment = 'Refresh Token摘要，不保存明文') AS actual_value,
       2 AS expected_value,
       CASE WHEN SUM(table_name = 'api_idempotency_record' AND column_name = 'request_path'
                    AND column_comment = '规范化请求路径，用于摘要和审计')
              + SUM(table_name = 'auth_refresh_session' AND column_name = 'token_hash'
                    AND column_comment = 'Refresh Token摘要，不保存明文') = 2
            THEN 'PASS' ELSE 'FAIL' END AS result
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'api_idempotency_record' AND column_name = 'request_path')
    OR (table_name = 'auth_refresh_session' AND column_name = 'token_hash'));
