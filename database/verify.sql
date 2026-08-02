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
