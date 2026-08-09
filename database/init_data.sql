SET NAMES utf8mb4;

-- 所有密码均为测试环境固定 BCrypt 示例值，不得用于生产环境。
SET @test_bcrypt = '$2a$10$SAdx34roAZNYsuH0jyCjeu0D3iF/nBikNAQfFNDpt.hErC8HJG/l.';

INSERT INTO tenant (id, tenant_code, tenant_name, status)
VALUES
    (1, 'TENANT_DEMO_001', '演示商家一', 'ACTIVE'),
    (2, 'TENANT_DEMO_002', '演示商家二', 'ACTIVE');

INSERT INTO sys_permission (id, permission_code, permission_name, description)
VALUES
    (1, 'tenant:create', '创建租户', '平台管理员创建租户'),
    (2, 'user:manage', '管理用户', '管理本作用域用户'),
    (3, 'order:create', '创建物流订单', '创建物流订单'),
    (4, 'order:operate', '操作物流订单', '执行仓库和履约操作'),
    (5, 'warehouse:measure', '仓库复称', '记录仓库实际重量和尺寸'),
    (6, 'warehouse:outbound', '仓库出库', '执行仓库出库'),
    (7, 'finance:bill-import', '导入账单', '导入统一CSV账单'),
    (8, 'finance:reconcile', '费用对账', '处理费用对账'),
    (9, 'tracking:callback', '处理轨迹回调', '处理物流商轨迹回调'),
    (10, 'audit:read', '查看审计日志', '查看审计日志');

INSERT INTO sys_user (id, tenant_id, username, display_name, password_hash, status)
VALUES
    (1, NULL, 'platform_admin', '平台管理员', @test_bcrypt, 'ACTIVE'),
    (2, 1, 'merchant_admin_001', '商家管理员一', @test_bcrypt, 'ACTIVE'),
    (3, 2, 'merchant_admin_002', '商家管理员二', @test_bcrypt, 'ACTIVE'),
    (4, 1, 'merchant_operator_001', '商家操作员一', @test_bcrypt, 'ACTIVE'),
    (5, 1, 'warehouse_operator_001', '仓库人员一', @test_bcrypt, 'ACTIVE'),
    (6, 1, 'finance_operator_001', '财务人员一', @test_bcrypt, 'ACTIVE'),
    (7, 2, 'merchant_operator_002', '商家操作员二', @test_bcrypt, 'ACTIVE'),
    (8, 2, 'warehouse_operator_002', '仓库人员二', @test_bcrypt, 'ACTIVE'),
    (9, 2, 'finance_operator_002', '财务人员二', @test_bcrypt, 'ACTIVE'),
    (10, NULL, 'mock_logistics_callback', 'Mock物流回调系统账号', @test_bcrypt, 'ACTIVE');

INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_scope, status)
VALUES
    (1, NULL, 'PLATFORM_ADMIN', '平台管理员', 'PLATFORM', 'ACTIVE'),
    (2, 1, 'MERCHANT_ADMIN', '商家管理员', 'TENANT', 'ACTIVE'),
    (3, 2, 'MERCHANT_ADMIN', '商家管理员', 'TENANT', 'ACTIVE'),
    (4, 1, 'MERCHANT_OPERATOR', '商家操作员', 'TENANT', 'ACTIVE'),
    (5, 1, 'WAREHOUSE_OPERATOR', '仓库人员', 'TENANT', 'ACTIVE'),
    (6, 1, 'FINANCE_OPERATOR', '财务人员', 'TENANT', 'ACTIVE'),
    (7, 2, 'MERCHANT_OPERATOR', '商家操作员', 'TENANT', 'ACTIVE'),
    (8, 2, 'WAREHOUSE_OPERATOR', '仓库人员', 'TENANT', 'ACTIVE'),
    (9, 2, 'FINANCE_OPERATOR', '财务人员', 'TENANT', 'ACTIVE'),
    (10, NULL, 'MOCK_LOGISTICS_SYSTEM', 'Mock物流系统账号', 'PLATFORM', 'ACTIVE');

INSERT INTO sys_user_role (id, tenant_id, user_id, role_id)
VALUES
    (1, NULL, 1, 1),
    (2, 1, 2, 2),
    (3, 2, 3, 3),
    (4, 1, 4, 4),
    (5, 1, 5, 5),
    (6, 1, 6, 6),
    (7, 2, 7, 7),
    (8, 2, 8, 8),
    (9, 2, 9, 9),
    (10, NULL, 10, 10);

INSERT INTO sys_role_permission (role_id, permission_id)
VALUES
    (1, 1), (1, 2), (1, 10),
    (2, 2), (2, 3), (2, 4), (2, 10),
    (3, 2), (3, 3), (3, 4), (3, 10),
    (4, 3), (4, 4),
    (5, 4), (5, 5), (5, 6),
    (6, 7), (6, 8), (6, 10),
    (7, 3), (7, 4),
    (8, 4), (8, 5), (8, 6),
    (9, 7), (9, 8), (9, 10),
    (10, 9);

INSERT INTO merchant_store (id, tenant_id, store_code, store_name, platform_code, platform_account, status)
VALUES
    (1, 1, 'STORE_JP_001', '商家一日本店', 'MARKETPLACE_A', 'merchant-one-jp', 'ACTIVE'),
    (2, 1, 'STORE_US_001', '商家一美国店', 'MARKETPLACE_A', 'merchant-one-us', 'ACTIVE'),
    (3, 2, 'STORE_JP_001', '商家二日本店', 'MARKETPLACE_B', 'merchant-two-jp', 'ACTIVE'),
    (4, 2, 'STORE_EU_001', '商家二欧洲店', 'MARKETPLACE_B', 'merchant-two-eu', 'ACTIVE');

INSERT INTO logistics_provider (id, provider_code, provider_name, status)
VALUES
    (1, 'PROVIDER_ALPHA', 'Alpha国际物流', 'ACTIVE'),
    (2, 'PROVIDER_BETA', 'Beta跨境物流', 'ACTIVE');

INSERT INTO logistics_channel (id, provider_id, channel_code, channel_name, service_area, status)
VALUES
    (1, 1, 'ALPHA_JP_STANDARD', 'Alpha日本标准渠道', '中国-日本', 'ACTIVE'),
    (2, 1, 'ALPHA_US_STANDARD', 'Alpha美国标准渠道', '中国-美国', 'ACTIVE'),
    (3, 2, 'BETA_EU_STANDARD', 'Beta欧洲标准渠道', '中国-欧洲', 'ACTIVE'),
    (4, 2, 'BETA_US_AIR', 'Beta美国空运渠道', '中国-美国', 'ACTIVE');

INSERT INTO logistics_channel_service_country (id, channel_id, country_code)
VALUES
    (1, 1, 'JP'),
    (2, 2, 'US'),
    (3, 3, 'DE'),
    (4, 3, 'FR'),
    (5, 3, 'NL'),
    (6, 4, 'US'),
    (7, 4, 'GB');

INSERT INTO price_rule (id, channel_id, version_no, rule_name, currency, volume_divisor, rounding_mode, rounding_increment, status, effective_from)
VALUES
    (1, 1, 1, '日本标准渠道规则V1', 'CNY', 6000.000, 'CEILING', 0.500, 'PUBLISHED', '2026-01-01 00:00:00.000'),
    (2, 1, 2, '日本标准渠道规则V2', 'CNY', 5000.000, 'CEILING', 0.500, 'DRAFT', '2026-08-01 00:00:00.000'),
    (3, 2, 1, '美国标准渠道规则V1', 'CNY', 6000.000, 'CEILING', 1.000, 'PUBLISHED', '2026-01-01 00:00:00.000'),
    (4, 3, 1, '欧洲标准渠道规则V1', 'CNY', 5000.000, 'CEILING', 0.500, 'PUBLISHED', '2026-01-01 00:00:00.000'),
    (5, 4, 1, '美国空运渠道规则V1', 'CNY', 5000.000, 'CEILING', 0.500, 'PUBLISHED', '2026-01-01 00:00:00.000');

INSERT INTO price_rule_tier (id, price_rule_id, tier_no, min_weight, max_weight, billing_mode, first_weight, first_fee, additional_weight, additional_fee, tier_fee)
VALUES
    (1, 1, 1, 0.000, 1.000, 'FIRST_CONTINUE', 0.500, 18.00, 0.500, 6.00, NULL),
    (2, 1, 2, 1.000, NULL, 'FIRST_CONTINUE', 0.500, 18.00, 0.500, 6.00, NULL),
    (3, 2, 1, 0.000, 2.000, 'FIXED', NULL, NULL, NULL, NULL, 30.00),
    (4, 2, 2, 2.000, NULL, 'FIXED', NULL, NULL, NULL, NULL, 45.00),
    (5, 3, 1, 0.000, 1.000, 'FIRST_CONTINUE', 1.000, 25.00, 1.000, 8.00, NULL),
    (6, 3, 2, 1.000, NULL, 'FIRST_CONTINUE', 1.000, 25.00, 1.000, 8.00, NULL),
    (7, 4, 1, 0.000, 2.000, 'FIXED', NULL, NULL, NULL, NULL, 36.00),
    (8, 4, 2, 2.000, NULL, 'FIXED', NULL, NULL, NULL, NULL, 58.00),
    (9, 5, 1, 0.000, 1.000, 'FIRST_CONTINUE', 0.500, 22.00, 0.500, 7.00, NULL),
    (10, 5, 2, 1.000, NULL, 'FIRST_CONTINUE', 0.500, 22.00, 0.500, 7.00, NULL);
