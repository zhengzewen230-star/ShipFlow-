-- ShipFlow QA execution contract upgrade; only shipflow_qa is changed.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE shipflow_qa;

DELIMITER $$
DROP PROCEDURE IF EXISTS upgrade_api_test_case_execution_contract$$
CREATE PROCEDURE upgrade_api_test_case_execution_contract()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='api_test_case' AND COLUMN_NAME='setup_steps') THEN
        ALTER TABLE api_test_case ADD COLUMN setup_steps JSON NULL COMMENT 'Structured setup actions';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='api_test_case' AND COLUMN_NAME='extractors') THEN
        ALTER TABLE api_test_case ADD COLUMN extractors JSON NULL COMMENT 'Structured response extractors';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='api_test_case' AND COLUMN_NAME='teardown_steps') THEN
        ALTER TABLE api_test_case ADD COLUMN teardown_steps JSON NULL COMMENT 'Structured teardown actions';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='api_test_case' AND COLUMN_NAME='tags') THEN
        ALTER TABLE api_test_case ADD COLUMN tags JSON NULL COMMENT 'Executor tags';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='api_test_case' AND COLUMN_NAME='execution_order') THEN
        ALTER TABLE api_test_case ADD COLUMN execution_order INT NULL COMMENT 'Stable execution order';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='api_test_case' AND COLUMN_NAME='automation_status') THEN
        ALTER TABLE api_test_case ADD COLUMN automation_status VARCHAR(32) NULL COMMENT 'READY, BLOCKED or DEFERRED';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='api_test_case' AND COLUMN_NAME='environment_scope') THEN
        ALTER TABLE api_test_case ADD COLUMN environment_scope VARCHAR(32) NULL COMMENT 'LOCAL, QA, INTEGRATION or PRODUCTION';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='api_test_case' AND INDEX_NAME='idx_api_test_case_execution') THEN
        ALTER TABLE api_test_case ADD INDEX idx_api_test_case_execution (enabled, automation_status, environment_scope, execution_order);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='api_test_case' AND CONSTRAINT_NAME='chk_api_test_case_automation_status') THEN
        ALTER TABLE api_test_case ADD CONSTRAINT chk_api_test_case_automation_status CHECK (automation_status IN ('READY','BLOCKED','DEFERRED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='api_test_case' AND CONSTRAINT_NAME='chk_api_test_case_environment_scope') THEN
        ALTER TABLE api_test_case ADD CONSTRAINT chk_api_test_case_environment_scope CHECK (environment_scope IN ('LOCAL','QA','INTEGRATION','PRODUCTION'));
    END IF;
END$$
CALL upgrade_api_test_case_execution_contract()$$
DROP PROCEDURE upgrade_api_test_case_execution_contract$$
DELIMITER ;

UPDATE api_test_case
SET cookie_template = CAST(REPLACE(CAST(cookie_template AS CHAR), CONCAT('SHIPFLOW_','REFRESH_TOKEN'), 'REFRESH_TOKEN') AS JSON)
WHERE case_no LIKE 'AUTH-%';

UPDATE api_test_case
SET assertions = CASE case_no
        WHEN 'AUTH-CSRF-001' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','is_empty','expected',NULL),JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','cookie','path','XSRF-TOKEN','operator','exists','expected',NULL))
        WHEN 'AUTH-CSRF-002' THEN JSON_ARRAY(JSON_OBJECT('source','cookie','path','XSRF-TOKEN','operator','exists','expected',NULL))
        WHEN 'AUTH-CSRF-003' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','Cache-Control','operator','contains','expected','no-store'))
        WHEN 'AUTH-CSRF-004' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','Secure'),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','SameSite=Strict'),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','Path=/api/v1/auth'),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','Max-Age='),JSON_OBJECT('source','header','path','Set-Cookie','operator','not_contains','expected','HttpOnly'),JSON_OBJECT('source','header','path','Set-Cookie','operator','not_contains','expected','Domain='))
        WHEN 'AUTH-CSRF-005' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','password'),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','passwordHash'),JSON_OBJECT('source','header','path','Set-Cookie','operator','not_contains','expected','REFRESH_TOKEN'))
        WHEN 'AUTH-LOGIN-001' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','Cache-Control','operator','contains','expected','no-store'),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','REFRESH_TOKEN'),JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','refreshToken'),JSON_OBJECT('source','json','path','$.success','operator','eq','expected',JSON_EXTRACT('true','$')),JSON_OBJECT('source','json','path','$.data.accessToken','operator','not_empty','expected',NULL),JSON_OBJECT('source','json','path','$.data.tokenType','operator','eq','expected','Bearer'),JSON_OBJECT('source','json','path','$.data.expiresIn','operator','eq','expected',900))
        WHEN 'AUTH-LOGIN-002' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','Cache-Control','operator','contains','expected','no-store'),JSON_OBJECT('source','jwt_claim','path','scope','token_from','$.data.accessToken','operator','eq','expected','PLATFORM'),JSON_OBJECT('source','jwt_claim','path','tenant_id','token_from','$.data.accessToken','operator','not_exists','expected',NULL),JSON_OBJECT('source','json','path','$.success','operator','eq','expected',JSON_EXTRACT('true','$')),JSON_OBJECT('source','json','path','$.data.accessToken','operator','not_empty','expected',NULL))
        WHEN 'AUTH-LOGIN-003' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','password'))
        WHEN 'AUTH-LOGIN-004' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','password'))
        WHEN 'AUTH-LOGIN-005' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','password'))
        WHEN 'AUTH-LOGIN-006' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','password'))
        WHEN 'AUTH-LOGIN-007' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','password'))
        WHEN 'AUTH-LOGIN-008' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','password'),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','passwordHash'))
        WHEN 'AUTH-LOGIN-009' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','password'),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','passwordHash'))
        WHEN 'AUTH-LOGIN-010' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','password'),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','passwordHash'))
        WHEN 'AUTH-LOGIN-011' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','password'),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','passwordHash'))
        WHEN 'AUTH-LOGIN-012' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','password'),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','passwordHash'))
        WHEN 'AUTH-LOGIN-013' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','password'),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','passwordHash'))
        WHEN 'AUTH-LOGIN-014' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','password'),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','passwordHash'))
        WHEN 'AUTH-LOGIN-018' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','password'),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','passwordHash'))
        WHEN 'AUTH-LOGIN-015' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','accessToken'))
        WHEN 'AUTH-LOGIN-016' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','accessToken'))
        WHEN 'AUTH-LOGIN-017' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','X-Trace-Id','operator','exists','expected',NULL),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','accessToken'))
        WHEN 'AUTH-ME-001' THEN JSON_ARRAY(JSON_OBJECT('source','json','path','$.data.userId','operator','not_empty','expected',NULL),JSON_OBJECT('source','json','path','$.data.permissions','operator','exists','expected',NULL))
        WHEN 'AUTH-ME-002' THEN JSON_ARRAY(JSON_OBJECT('source','jwt_claim','path','scope','token_from','${PLATFORM_ACCESS_TOKEN}','operator','eq','expected','PLATFORM'),JSON_OBJECT('source','jwt_claim','path','tenant_id','token_from','${PLATFORM_ACCESS_TOKEN}','operator','not_exists','expected',NULL))
        WHEN 'AUTH-ME-003' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','WWW-Authenticate','operator','contains','expected','Bearer'))
        WHEN 'AUTH-ME-004' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','WWW-Authenticate','operator','contains','expected','Bearer'))
        WHEN 'AUTH-ME-005' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','WWW-Authenticate','operator','contains','expected','Bearer'))
        WHEN 'AUTH-ME-006' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','WWW-Authenticate','operator','contains','expected','Bearer'))
        WHEN 'AUTH-ME-007' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','WWW-Authenticate','operator','contains','expected','Bearer'))
        WHEN 'AUTH-ME-008' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','username'))
        WHEN 'AUTH-ME-009' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','password'),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','passwordHash'),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','refreshToken'))
        WHEN 'AUTH-REFRESH-001' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','Cache-Control','operator','contains','expected','no-store'),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','REFRESH_TOKEN'),JSON_OBJECT('source','cookie','path','REFRESH_TOKEN','operator','ne','expected','${REFRESH_COOKIE}'),JSON_OBJECT('source','json','path','$.success','operator','eq','expected',JSON_EXTRACT('true','$')),JSON_OBJECT('source','json','path','$.data.expiresIn','operator','eq','expected',900))
        WHEN 'AUTH-REFRESH-002' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','accessToken'))
        WHEN 'AUTH-REFRESH-003' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','tokenHash'))
        WHEN 'AUTH-REFRESH-004' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','tokenHash'))
        WHEN 'AUTH-REFRESH-005' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','accessToken'))
        WHEN 'AUTH-REFRESH-006' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','accessToken'))
        WHEN 'AUTH-REFRESH-007' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','accessToken'))
        WHEN 'AUTH-REFRESH-008' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','accessToken'))
        WHEN 'AUTH-REFRESH-009' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','accessToken'))
        WHEN 'AUTH-REFRESH-010' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','accessToken'))
        WHEN 'AUTH-REFRESH-011' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','accessToken'))
        WHEN 'AUTH-REFRESH-012' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','Cache-Control','operator','contains','expected','no-store'),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','HttpOnly'),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','Secure'),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','SameSite=Strict'),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','Path=/api/v1/auth'),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','Max-Age='),JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','refreshToken'))
        WHEN 'AUTH-LOGOUT-001' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','Cache-Control','operator','contains','expected','no-store'),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','REFRESH_TOKEN='),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','XSRF-TOKEN='),JSON_OBJECT('source','json','path','$.success','operator','eq','expected',JSON_EXTRACT('true','$')))
        WHEN 'AUTH-LOGOUT-002' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','Cache-Control','operator','contains','expected','no-store'),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','REFRESH_TOKEN='),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','XSRF-TOKEN='),JSON_OBJECT('source','json','path','$.success','operator','eq','expected',JSON_EXTRACT('true','$')))
        WHEN 'AUTH-LOGOUT-003' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','Cache-Control','operator','contains','expected','no-store'),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','REFRESH_TOKEN='),JSON_OBJECT('source','header','path','Set-Cookie','operator','contains','expected','XSRF-TOKEN='))
        WHEN 'AUTH-LOGOUT-004' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','Cache-Control','operator','contains','expected','no-store'))
        WHEN 'AUTH-LOGOUT-005' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','family'))
        WHEN 'AUTH-LOGOUT-006' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','family'))
        WHEN 'AUTH-LOGOUT-007' THEN JSON_ARRAY(JSON_OBJECT('source','body','path',NULL,'operator','not_contains','expected','family'))
        WHEN 'AUTH-LOGOUT-008' THEN JSON_ARRAY(JSON_OBJECT('source','header','path','Cache-Control','operator','contains','expected','no-store'),JSON_OBJECT('source','body','path',NULL,'operator','contains','expected','FOLLOW_UP_AUTH-1002'))
    ELSE JSON_ARRAY()
END
WHERE case_no LIKE 'AUTH-%';

UPDATE api_test_case
SET setup_steps = CASE case_no
        WHEN 'AUTH-CSRF-001' THEN JSON_ARRAY()
        WHEN 'AUTH-CSRF-002' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-CSRF-003' THEN JSON_ARRAY()
        WHEN 'AUTH-CSRF-004' THEN JSON_ARRAY()
        WHEN 'AUTH-CSRF-005' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-001' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-002' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-003' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-004' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-005' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-006' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-007' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-008' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-009' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-010' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-011' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-012' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-013' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-014' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-018' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-015' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-016' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGIN-017' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN_A')),JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN_B'),'ensure_different_from','XSRF_TOKEN_A'))
        WHEN 'AUTH-ME-001' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')))
        WHEN 'AUTH-ME-002' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','credential_profile','PLATFORM_ADMIN','save',JSON_OBJECT('json.$.data.accessToken','PLATFORM_ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')))
        WHEN 'AUTH-ME-003' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-004' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')),JSON_OBJECT('action','create_token_fixture','fixture_type','TAMPERED_ACCESS_TOKEN','save_as','TAMPERED_ACCESS_TOKEN','input','ACCESS_TOKEN'))
        WHEN 'AUTH-ME-005' THEN JSON_ARRAY(JSON_OBJECT('action','create_token_fixture','fixture_type','EXPIRED_ACCESS_TOKEN','save_as','EXPIRED_ACCESS_TOKEN'))
        WHEN 'AUTH-ME-006' THEN JSON_ARRAY(JSON_OBJECT('action','create_token_fixture','fixture_type','WRONG_ISSUER_ACCESS_TOKEN','save_as','WRONG_ISSUER_ACCESS_TOKEN'))
        WHEN 'AUTH-ME-007' THEN JSON_ARRAY(JSON_OBJECT('action','create_token_fixture','fixture_type','WRONG_AUDIENCE_ACCESS_TOKEN','save_as','WRONG_AUDIENCE_ACCESS_TOKEN'))
        WHEN 'AUTH-ME-008' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')),JSON_OBJECT('action','create_token_fixture','fixture_type','CROSS_TENANT_ACCESS_TOKEN','save_as','CROSS_TENANT_ACCESS_TOKEN','input','ACCESS_TOKEN'))
        WHEN 'AUTH-ME-009' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')))
        WHEN 'AUTH-REFRESH-001' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')))
        WHEN 'AUTH-REFRESH-002' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')),JSON_OBJECT('action','create_token_fixture','fixture_type','ROTATED_REFRESH_TOKEN','save_as','OLD_REFRESH_COOKIE','input','REFRESH_COOKIE'))
        WHEN 'AUTH-REFRESH-003' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-REFRESH-004' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','create_token_fixture','fixture_type','INVALID_REFRESH_TOKEN','save_as','INVALID_REFRESH_COOKIE'))
        WHEN 'AUTH-REFRESH-005' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','create_token_fixture','fixture_type','EXPIRED_REFRESH_TOKEN','save_as','EXPIRED_REFRESH_COOKIE'))
        WHEN 'AUTH-REFRESH-006' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')))
        WHEN 'AUTH-REFRESH-007' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')))
        WHEN 'AUTH-REFRESH-008' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')),JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN_A')),JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN_B'),'ensure_different_from','XSRF_TOKEN_A'))
        WHEN 'AUTH-REFRESH-009' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','create_token_fixture','fixture_type','ROTATED_REFRESH_TOKEN','save_as','ROTATED_REFRESH_COOKIE'))
        WHEN 'AUTH-REFRESH-010' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','create_token_fixture','fixture_type','DISABLED_USER_REFRESH_CONTEXT','save_as','REFRESH_COOKIE'))
        WHEN 'AUTH-REFRESH-011' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','create_token_fixture','fixture_type','DISABLED_TENANT_OR_ROLE_REFRESH_CONTEXT','save_as','REFRESH_COOKIE'))
        WHEN 'AUTH-REFRESH-012' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')))
        WHEN 'AUTH-LOGOUT-001' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')))
        WHEN 'AUTH-LOGOUT-002' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')))
        WHEN 'AUTH-LOGOUT-003' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','create_token_fixture','fixture_type','NON_ACTIVE_REFRESH_TOKEN','save_as','NON_ACTIVE_REFRESH_COOKIE'))
        WHEN 'AUTH-LOGOUT-004' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')),JSON_OBJECT('action','create_token_fixture','fixture_type','REVOKED_REFRESH_TOKEN','save_as','REVOKED_REFRESH_COOKIE','input','REFRESH_COOKIE'))
        WHEN 'AUTH-LOGOUT-005' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')))
        WHEN 'AUTH-LOGOUT-006' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')))
        WHEN 'AUTH-LOGOUT-007' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')),JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN_A')),JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN_B'),'ensure_different_from','XSRF_TOKEN_A'))
        WHEN 'AUTH-LOGOUT-008' THEN JSON_ARRAY(JSON_OBJECT('action','get_csrf','save',JSON_OBJECT('cookie.XSRF-TOKEN','XSRF_TOKEN')),JSON_OBJECT('action','login','save',JSON_OBJECT('json.$.data.accessToken','ACCESS_TOKEN','cookie.REFRESH_TOKEN','REFRESH_COOKIE')))
    ELSE JSON_ARRAY()
END,
    extractors = CASE case_no
        WHEN 'AUTH-CSRF-001' THEN JSON_ARRAY(JSON_OBJECT('source','cookie','path','XSRF-TOKEN','save_as','XSRF_TOKEN'))
        WHEN 'AUTH-CSRF-002' THEN JSON_ARRAY(JSON_OBJECT('source','cookie','path','XSRF-TOKEN','save_as','XSRF_TOKEN'))
        WHEN 'AUTH-CSRF-003' THEN JSON_ARRAY()
        WHEN 'AUTH-CSRF-004' THEN JSON_ARRAY()
        WHEN 'AUTH-CSRF-005' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-001' THEN JSON_ARRAY(JSON_OBJECT('source','json','path','$.data.accessToken','save_as','ACCESS_TOKEN'),JSON_OBJECT('source','cookie','path','REFRESH_TOKEN','save_as','REFRESH_COOKIE'))
        WHEN 'AUTH-LOGIN-002' THEN JSON_ARRAY(JSON_OBJECT('source','json','path','$.data.accessToken','save_as','ACCESS_TOKEN'),JSON_OBJECT('source','cookie','path','REFRESH_TOKEN','save_as','REFRESH_COOKIE'))
        WHEN 'AUTH-LOGIN-003' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-004' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-005' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-006' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-007' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-008' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-009' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-010' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-011' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-012' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-013' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-014' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-018' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-015' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-016' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-017' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-001' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-002' THEN JSON_ARRAY(JSON_OBJECT('source','json','path','$.data.accessToken','save_as','PLATFORM_ACCESS_TOKEN'),JSON_OBJECT('source','cookie','path','REFRESH_TOKEN','save_as','REFRESH_COOKIE'))
        WHEN 'AUTH-ME-003' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-004' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-005' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-006' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-007' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-008' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-009' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-001' THEN JSON_ARRAY(JSON_OBJECT('source','json','path','$.data.accessToken','save_as','NEW_ACCESS_TOKEN'),JSON_OBJECT('source','cookie','path','REFRESH_TOKEN','save_as','NEW_REFRESH_COOKIE'))
        WHEN 'AUTH-REFRESH-002' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-003' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-004' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-005' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-006' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-007' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-008' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-009' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-010' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-011' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-012' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-001' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-002' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-003' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-004' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-005' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-006' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-007' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-008' THEN JSON_ARRAY()
    ELSE JSON_ARRAY()
END,
    teardown_steps = CASE case_no
        WHEN 'AUTH-CSRF-001' THEN JSON_ARRAY()
        WHEN 'AUTH-CSRF-002' THEN JSON_ARRAY()
        WHEN 'AUTH-CSRF-003' THEN JSON_ARRAY()
        WHEN 'AUTH-CSRF-004' THEN JSON_ARRAY()
        WHEN 'AUTH-CSRF-005' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-001' THEN JSON_ARRAY(JSON_OBJECT('action','logout','save',JSON_OBJECT()))
        WHEN 'AUTH-LOGIN-002' THEN JSON_ARRAY(JSON_OBJECT('action','logout','save',JSON_OBJECT()))
        WHEN 'AUTH-LOGIN-003' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-004' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-005' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-006' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-007' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-008' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-009' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-010' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-011' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-012' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-013' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-014' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-018' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-015' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-016' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGIN-017' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-001' THEN JSON_ARRAY(JSON_OBJECT('action','logout','save',JSON_OBJECT()))
        WHEN 'AUTH-ME-002' THEN JSON_ARRAY(JSON_OBJECT('action','logout','save',JSON_OBJECT()))
        WHEN 'AUTH-ME-003' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-004' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-005' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-006' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-007' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-008' THEN JSON_ARRAY()
        WHEN 'AUTH-ME-009' THEN JSON_ARRAY(JSON_OBJECT('action','logout','save',JSON_OBJECT()))
        WHEN 'AUTH-REFRESH-001' THEN JSON_ARRAY(JSON_OBJECT('action','logout','save',JSON_OBJECT()))
        WHEN 'AUTH-REFRESH-002' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-003' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-004' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-005' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-006' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-007' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-008' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-009' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-010' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-011' THEN JSON_ARRAY()
        WHEN 'AUTH-REFRESH-012' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-001' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-002' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-003' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-004' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-005' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-006' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-007' THEN JSON_ARRAY()
        WHEN 'AUTH-LOGOUT-008' THEN JSON_ARRAY()
    ELSE JSON_ARRAY()
END,
    tags = JSON_ARRAY('auth', module, test_type,
                     CASE case_no
        WHEN 'AUTH-CSRF-001' THEN 'READY'
        WHEN 'AUTH-CSRF-002' THEN 'READY'
        WHEN 'AUTH-CSRF-003' THEN 'READY'
        WHEN 'AUTH-CSRF-004' THEN 'DEFERRED'
        WHEN 'AUTH-CSRF-005' THEN 'READY'
        WHEN 'AUTH-LOGIN-001' THEN 'READY'
        WHEN 'AUTH-LOGIN-002' THEN 'READY'
        WHEN 'AUTH-LOGIN-003' THEN 'READY'
        WHEN 'AUTH-LOGIN-004' THEN 'READY'
        WHEN 'AUTH-LOGIN-005' THEN 'READY'
        WHEN 'AUTH-LOGIN-006' THEN 'READY'
        WHEN 'AUTH-LOGIN-007' THEN 'READY'
        WHEN 'AUTH-LOGIN-008' THEN 'READY'
        WHEN 'AUTH-LOGIN-009' THEN 'READY'
        WHEN 'AUTH-LOGIN-010' THEN 'READY'
        WHEN 'AUTH-LOGIN-011' THEN 'BLOCKED'
        WHEN 'AUTH-LOGIN-012' THEN 'BLOCKED'
        WHEN 'AUTH-LOGIN-013' THEN 'BLOCKED'
        WHEN 'AUTH-LOGIN-014' THEN 'BLOCKED'
        WHEN 'AUTH-LOGIN-018' THEN 'BLOCKED'
        WHEN 'AUTH-LOGIN-015' THEN 'READY'
        WHEN 'AUTH-LOGIN-016' THEN 'READY'
        WHEN 'AUTH-LOGIN-017' THEN 'READY'
        WHEN 'AUTH-ME-001' THEN 'READY'
        WHEN 'AUTH-ME-002' THEN 'READY'
        WHEN 'AUTH-ME-003' THEN 'READY'
        WHEN 'AUTH-ME-004' THEN 'READY'
        WHEN 'AUTH-ME-005' THEN 'DEFERRED'
        WHEN 'AUTH-ME-006' THEN 'DEFERRED'
        WHEN 'AUTH-ME-007' THEN 'DEFERRED'
        WHEN 'AUTH-ME-008' THEN 'BLOCKED'
        WHEN 'AUTH-ME-009' THEN 'READY'
        WHEN 'AUTH-REFRESH-001' THEN 'BLOCKED'
        WHEN 'AUTH-REFRESH-002' THEN 'BLOCKED'
        WHEN 'AUTH-REFRESH-003' THEN 'READY'
        WHEN 'AUTH-REFRESH-004' THEN 'READY'
        WHEN 'AUTH-REFRESH-005' THEN 'BLOCKED'
        WHEN 'AUTH-REFRESH-006' THEN 'READY'
        WHEN 'AUTH-REFRESH-007' THEN 'READY'
        WHEN 'AUTH-REFRESH-008' THEN 'READY'
        WHEN 'AUTH-REFRESH-009' THEN 'BLOCKED'
        WHEN 'AUTH-REFRESH-010' THEN 'BLOCKED'
        WHEN 'AUTH-REFRESH-011' THEN 'BLOCKED'
        WHEN 'AUTH-REFRESH-012' THEN 'DEFERRED'
        WHEN 'AUTH-LOGOUT-001' THEN 'BLOCKED'
        WHEN 'AUTH-LOGOUT-002' THEN 'READY'
        WHEN 'AUTH-LOGOUT-003' THEN 'BLOCKED'
        WHEN 'AUTH-LOGOUT-004' THEN 'BLOCKED'
        WHEN 'AUTH-LOGOUT-005' THEN 'READY'
        WHEN 'AUTH-LOGOUT-006' THEN 'READY'
        WHEN 'AUTH-LOGOUT-007' THEN 'READY'
        WHEN 'AUTH-LOGOUT-008' THEN 'BLOCKED'
                         ELSE 'BLOCKED'
                     END),
    execution_order = CASE case_no
        WHEN 'AUTH-CSRF-001' THEN 1001
        WHEN 'AUTH-CSRF-002' THEN 1002
        WHEN 'AUTH-CSRF-003' THEN 1003
        WHEN 'AUTH-CSRF-004' THEN 1004
        WHEN 'AUTH-CSRF-005' THEN 1005
        WHEN 'AUTH-LOGIN-001' THEN 1006
        WHEN 'AUTH-LOGIN-002' THEN 1007
        WHEN 'AUTH-LOGIN-003' THEN 1008
        WHEN 'AUTH-LOGIN-004' THEN 1009
        WHEN 'AUTH-LOGIN-005' THEN 1010
        WHEN 'AUTH-LOGIN-006' THEN 1011
        WHEN 'AUTH-LOGIN-007' THEN 1012
        WHEN 'AUTH-LOGIN-008' THEN 1013
        WHEN 'AUTH-LOGIN-009' THEN 1014
        WHEN 'AUTH-LOGIN-010' THEN 1015
        WHEN 'AUTH-LOGIN-011' THEN 1016
        WHEN 'AUTH-LOGIN-012' THEN 1017
        WHEN 'AUTH-LOGIN-013' THEN 1018
        WHEN 'AUTH-LOGIN-014' THEN 1019
        WHEN 'AUTH-LOGIN-018' THEN 1020
        WHEN 'AUTH-LOGIN-015' THEN 1021
        WHEN 'AUTH-LOGIN-016' THEN 1022
        WHEN 'AUTH-LOGIN-017' THEN 1023
        WHEN 'AUTH-ME-001' THEN 1024
        WHEN 'AUTH-ME-002' THEN 1025
        WHEN 'AUTH-ME-003' THEN 1026
        WHEN 'AUTH-ME-004' THEN 1027
        WHEN 'AUTH-ME-005' THEN 1028
        WHEN 'AUTH-ME-006' THEN 1029
        WHEN 'AUTH-ME-007' THEN 1030
        WHEN 'AUTH-ME-008' THEN 1031
        WHEN 'AUTH-ME-009' THEN 1032
        WHEN 'AUTH-REFRESH-001' THEN 1033
        WHEN 'AUTH-REFRESH-002' THEN 1034
        WHEN 'AUTH-REFRESH-003' THEN 1035
        WHEN 'AUTH-REFRESH-004' THEN 1036
        WHEN 'AUTH-REFRESH-005' THEN 1037
        WHEN 'AUTH-REFRESH-006' THEN 1038
        WHEN 'AUTH-REFRESH-007' THEN 1039
        WHEN 'AUTH-REFRESH-008' THEN 1040
        WHEN 'AUTH-REFRESH-009' THEN 1041
        WHEN 'AUTH-REFRESH-010' THEN 1042
        WHEN 'AUTH-REFRESH-011' THEN 1043
        WHEN 'AUTH-REFRESH-012' THEN 1044
        WHEN 'AUTH-LOGOUT-001' THEN 1045
        WHEN 'AUTH-LOGOUT-002' THEN 1046
        WHEN 'AUTH-LOGOUT-003' THEN 1047
        WHEN 'AUTH-LOGOUT-004' THEN 1048
        WHEN 'AUTH-LOGOUT-005' THEN 1049
        WHEN 'AUTH-LOGOUT-006' THEN 1050
        WHEN 'AUTH-LOGOUT-007' THEN 1051
        WHEN 'AUTH-LOGOUT-008' THEN 1052
                         ELSE 9000
                     END,
    automation_status = CASE case_no
        WHEN 'AUTH-CSRF-001' THEN 'READY'
        WHEN 'AUTH-CSRF-002' THEN 'READY'
        WHEN 'AUTH-CSRF-003' THEN 'READY'
        WHEN 'AUTH-CSRF-004' THEN 'DEFERRED'
        WHEN 'AUTH-CSRF-005' THEN 'READY'
        WHEN 'AUTH-LOGIN-001' THEN 'READY'
        WHEN 'AUTH-LOGIN-002' THEN 'READY'
        WHEN 'AUTH-LOGIN-003' THEN 'READY'
        WHEN 'AUTH-LOGIN-004' THEN 'READY'
        WHEN 'AUTH-LOGIN-005' THEN 'READY'
        WHEN 'AUTH-LOGIN-006' THEN 'READY'
        WHEN 'AUTH-LOGIN-007' THEN 'READY'
        WHEN 'AUTH-LOGIN-008' THEN 'READY'
        WHEN 'AUTH-LOGIN-009' THEN 'READY'
        WHEN 'AUTH-LOGIN-010' THEN 'READY'
        WHEN 'AUTH-LOGIN-011' THEN 'BLOCKED'
        WHEN 'AUTH-LOGIN-012' THEN 'BLOCKED'
        WHEN 'AUTH-LOGIN-013' THEN 'BLOCKED'
        WHEN 'AUTH-LOGIN-014' THEN 'BLOCKED'
        WHEN 'AUTH-LOGIN-018' THEN 'BLOCKED'
        WHEN 'AUTH-LOGIN-015' THEN 'READY'
        WHEN 'AUTH-LOGIN-016' THEN 'READY'
        WHEN 'AUTH-LOGIN-017' THEN 'READY'
        WHEN 'AUTH-ME-001' THEN 'READY'
        WHEN 'AUTH-ME-002' THEN 'READY'
        WHEN 'AUTH-ME-003' THEN 'READY'
        WHEN 'AUTH-ME-004' THEN 'READY'
        WHEN 'AUTH-ME-005' THEN 'DEFERRED'
        WHEN 'AUTH-ME-006' THEN 'DEFERRED'
        WHEN 'AUTH-ME-007' THEN 'DEFERRED'
        WHEN 'AUTH-ME-008' THEN 'BLOCKED'
        WHEN 'AUTH-ME-009' THEN 'READY'
        WHEN 'AUTH-REFRESH-001' THEN 'BLOCKED'
        WHEN 'AUTH-REFRESH-002' THEN 'BLOCKED'
        WHEN 'AUTH-REFRESH-003' THEN 'READY'
        WHEN 'AUTH-REFRESH-004' THEN 'READY'
        WHEN 'AUTH-REFRESH-005' THEN 'BLOCKED'
        WHEN 'AUTH-REFRESH-006' THEN 'READY'
        WHEN 'AUTH-REFRESH-007' THEN 'READY'
        WHEN 'AUTH-REFRESH-008' THEN 'READY'
        WHEN 'AUTH-REFRESH-009' THEN 'BLOCKED'
        WHEN 'AUTH-REFRESH-010' THEN 'BLOCKED'
        WHEN 'AUTH-REFRESH-011' THEN 'BLOCKED'
        WHEN 'AUTH-REFRESH-012' THEN 'DEFERRED'
        WHEN 'AUTH-LOGOUT-001' THEN 'BLOCKED'
        WHEN 'AUTH-LOGOUT-002' THEN 'READY'
        WHEN 'AUTH-LOGOUT-003' THEN 'BLOCKED'
        WHEN 'AUTH-LOGOUT-004' THEN 'BLOCKED'
        WHEN 'AUTH-LOGOUT-005' THEN 'READY'
        WHEN 'AUTH-LOGOUT-006' THEN 'READY'
        WHEN 'AUTH-LOGOUT-007' THEN 'READY'
        WHEN 'AUTH-LOGOUT-008' THEN 'BLOCKED'
                         ELSE 'BLOCKED'
                     END,
    environment_scope = CASE case_no
        WHEN 'AUTH-CSRF-001' THEN 'QA'
        WHEN 'AUTH-CSRF-002' THEN 'QA'
        WHEN 'AUTH-CSRF-003' THEN 'QA'
        WHEN 'AUTH-CSRF-004' THEN 'PRODUCTION'
        WHEN 'AUTH-CSRF-005' THEN 'QA'
        WHEN 'AUTH-LOGIN-001' THEN 'QA'
        WHEN 'AUTH-LOGIN-002' THEN 'QA'
        WHEN 'AUTH-LOGIN-003' THEN 'QA'
        WHEN 'AUTH-LOGIN-004' THEN 'QA'
        WHEN 'AUTH-LOGIN-005' THEN 'QA'
        WHEN 'AUTH-LOGIN-006' THEN 'QA'
        WHEN 'AUTH-LOGIN-007' THEN 'QA'
        WHEN 'AUTH-LOGIN-008' THEN 'QA'
        WHEN 'AUTH-LOGIN-009' THEN 'QA'
        WHEN 'AUTH-LOGIN-010' THEN 'QA'
        WHEN 'AUTH-LOGIN-011' THEN 'QA'
        WHEN 'AUTH-LOGIN-012' THEN 'QA'
        WHEN 'AUTH-LOGIN-013' THEN 'QA'
        WHEN 'AUTH-LOGIN-014' THEN 'QA'
        WHEN 'AUTH-LOGIN-018' THEN 'QA'
        WHEN 'AUTH-LOGIN-015' THEN 'QA'
        WHEN 'AUTH-LOGIN-016' THEN 'QA'
        WHEN 'AUTH-LOGIN-017' THEN 'QA'
        WHEN 'AUTH-ME-001' THEN 'QA'
        WHEN 'AUTH-ME-002' THEN 'QA'
        WHEN 'AUTH-ME-003' THEN 'QA'
        WHEN 'AUTH-ME-004' THEN 'QA'
        WHEN 'AUTH-ME-005' THEN 'QA'
        WHEN 'AUTH-ME-006' THEN 'QA'
        WHEN 'AUTH-ME-007' THEN 'QA'
        WHEN 'AUTH-ME-008' THEN 'QA'
        WHEN 'AUTH-ME-009' THEN 'QA'
        WHEN 'AUTH-REFRESH-001' THEN 'QA'
        WHEN 'AUTH-REFRESH-002' THEN 'QA'
        WHEN 'AUTH-REFRESH-003' THEN 'QA'
        WHEN 'AUTH-REFRESH-004' THEN 'QA'
        WHEN 'AUTH-REFRESH-005' THEN 'QA'
        WHEN 'AUTH-REFRESH-006' THEN 'QA'
        WHEN 'AUTH-REFRESH-007' THEN 'QA'
        WHEN 'AUTH-REFRESH-008' THEN 'QA'
        WHEN 'AUTH-REFRESH-009' THEN 'QA'
        WHEN 'AUTH-REFRESH-010' THEN 'QA'
        WHEN 'AUTH-REFRESH-011' THEN 'QA'
        WHEN 'AUTH-REFRESH-012' THEN 'PRODUCTION'
        WHEN 'AUTH-LOGOUT-001' THEN 'QA'
        WHEN 'AUTH-LOGOUT-002' THEN 'QA'
        WHEN 'AUTH-LOGOUT-003' THEN 'QA'
        WHEN 'AUTH-LOGOUT-004' THEN 'QA'
        WHEN 'AUTH-LOGOUT-005' THEN 'QA'
        WHEN 'AUTH-LOGOUT-006' THEN 'QA'
        WHEN 'AUTH-LOGOUT-007' THEN 'QA'
        WHEN 'AUTH-LOGOUT-008' THEN 'QA'
                         ELSE 'QA'
                     END
WHERE case_no LIKE 'AUTH-%';

UPDATE api_test_case
SET setup_steps = JSON_SET(setup_steps, '$[1].credential_profile', 'TENANT_ADMIN')
WHERE case_no LIKE 'AUTH-%'
  AND JSON_SEARCH(setup_steps, 'one', 'login', NULL, '$[*].action') IS NOT NULL
  AND case_no <> 'AUTH-ME-002';

UPDATE api_test_case
SET teardown_steps = JSON_ARRAY(JSON_OBJECT(
        'action','logout',
        'context',JSON_OBJECT('xsrf_token','XSRF_TOKEN','refresh_cookie','REFRESH_COOKIE')
    ))
WHERE enabled=1
  AND automation_status='READY'
  AND JSON_SEARCH(setup_steps, 'one', 'login', NULL, '$[*].action') IS NOT NULL;

ALTER TABLE api_test_case
    MODIFY COLUMN setup_steps JSON NOT NULL COMMENT 'Structured setup actions',
    MODIFY COLUMN extractors JSON NOT NULL COMMENT 'Structured response extractors',
    MODIFY COLUMN teardown_steps JSON NOT NULL COMMENT 'Structured teardown actions',
    MODIFY COLUMN tags JSON NOT NULL COMMENT 'Executor tags',
    MODIFY COLUMN execution_order INT NOT NULL COMMENT 'Stable execution order',
    MODIFY COLUMN automation_status VARCHAR(32) NOT NULL COMMENT 'READY, BLOCKED or DEFERRED',
    MODIFY COLUMN environment_scope VARCHAR(32) NOT NULL COMMENT 'LOCAL, QA, INTEGRATION or PRODUCTION';

SELECT COUNT(*) AS total_case_count, CASE WHEN COUNT(*)=52 THEN 'PASS' ELSE 'FAIL' END AS total_case_check
FROM api_test_case WHERE enabled=1 AND case_no LIKE 'AUTH-%';

SELECT COUNT(*) AS duplicate_case_no_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS duplicate_case_check
FROM (SELECT case_no FROM api_test_case WHERE enabled=1 AND case_no LIKE 'AUTH-%' GROUP BY case_no HAVING COUNT(*)>1) d;

SELECT automation_status, COUNT(*) AS case_count
FROM api_test_case WHERE enabled=1 AND case_no LIKE 'AUTH-%' GROUP BY automation_status ORDER BY automation_status;

SELECT COUNT(*) AS non_structured_assertion_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS assertion_array_check
FROM api_test_case t
WHERE enabled=1 AND case_no LIKE 'AUTH-%'
  AND (JSON_TYPE(assertions)<>'ARRAY' OR JSON_LENGTH(assertions)=0
       OR EXISTS (SELECT 1 FROM JSON_TABLE(t.assertions, '$[*]' COLUMNS (item JSON PATH '$')) x WHERE JSON_TYPE(x.item)<>'OBJECT'));

SELECT COUNT(*) AS assertion_required_key_missing_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS assertion_key_check
FROM api_test_case t
WHERE enabled=1 AND case_no LIKE 'AUTH-%'
  AND EXISTS (SELECT 1 FROM JSON_TABLE(t.assertions, '$[*]' COLUMNS (item JSON PATH '$')) x
              WHERE NOT JSON_CONTAINS_PATH(x.item, 'all', '$.source', '$.path', '$.operator', '$.expected'));

SELECT COUNT(*) AS invalid_assertion_source_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS assertion_source_check
FROM api_test_case t
WHERE enabled=1 AND case_no LIKE 'AUTH-%'
  AND EXISTS (SELECT 1 FROM JSON_TABLE(t.assertions, '$[*]' COLUMNS (source_value VARCHAR(32) PATH '$.source')) x
              WHERE x.source_value NOT IN ('status_code','json','header','cookie','body','jwt_claim'));

SELECT COUNT(*) AS invalid_assertion_operator_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS assertion_operator_check
FROM api_test_case t
WHERE enabled=1 AND case_no LIKE 'AUTH-%'
  AND EXISTS (SELECT 1 FROM JSON_TABLE(t.assertions, '$[*]' COLUMNS (operator_value VARCHAR(32) PATH '$.operator')) x
              WHERE x.operator_value NOT IN ('eq','ne','exists','not_exists','not_empty','contains','not_contains','is_empty'));

SELECT COUNT(*) AS invalid_extractor_source_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS extractor_source_check
FROM api_test_case t
WHERE enabled=1 AND case_no LIKE 'AUTH-%'
  AND EXISTS (SELECT 1 FROM JSON_TABLE(t.extractors, '$[*]' COLUMNS (source_value VARCHAR(32) PATH '$.source')) x
              WHERE x.source_value NOT IN ('json','cookie','header'));

SELECT COUNT(*) AS extractor_required_key_missing_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS extractor_key_check
FROM api_test_case t
WHERE enabled=1 AND case_no LIKE 'AUTH-%'
  AND EXISTS (SELECT 1 FROM JSON_TABLE(t.extractors, '$[*]' COLUMNS (item JSON PATH '$')) x
              WHERE NOT JSON_CONTAINS_PATH(x.item, 'all', '$.source', '$.path', '$.save_as'));

SELECT COUNT(*) AS empty_assertion_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS empty_assertion_check
FROM api_test_case
WHERE enabled=1 AND case_no LIKE 'AUTH-%' AND JSON_LENGTH(assertions)=0;

SELECT COUNT(*) AS jwt_claim_missing_token_from_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS jwt_claim_token_source_check
FROM api_test_case t
WHERE enabled=1 AND case_no LIKE 'AUTH-%'
  AND EXISTS (SELECT 1 FROM JSON_TABLE(t.assertions, '$[*]' COLUMNS (item JSON PATH '$')) x
              WHERE JSON_UNQUOTE(JSON_EXTRACT(x.item, '$.source'))='jwt_claim'
                AND NOT JSON_CONTAINS_PATH(x.item, 'all', '$.token_from'));

SELECT COUNT(*) AS fixture_parameter_missing_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS fixture_parameter_check
FROM api_test_case t
WHERE enabled=1 AND case_no LIKE 'AUTH-%'
  AND EXISTS (SELECT 1 FROM JSON_TABLE(t.setup_steps, '$[*]' COLUMNS (
                    action_value VARCHAR(64) PATH '$.action',
                    fixture_type_value VARCHAR(64) PATH '$.fixture_type',
                    save_as_value VARCHAR(128) PATH '$.save_as'
                )) x
              WHERE x.action_value='create_token_fixture'
                AND (x.fixture_type_value IS NULL OR x.save_as_value IS NULL));

SELECT COUNT(*) AS login_credential_profile_missing_count,
       CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END
           AS login_credential_profile_check
FROM api_test_case t
WHERE enabled = 1
  AND case_no LIKE 'AUTH-%'
  AND EXISTS (
      SELECT 1
      FROM JSON_TABLE(
          t.setup_steps,
          '$[*]' COLUMNS (
              action_value VARCHAR(64) PATH '$.action',
              credential_profile_value VARCHAR(64) PATH '$.credential_profile'
          )
      ) x
      WHERE x.action_value = 'login'
        AND (
            x.credential_profile_value IS NULL
            OR x.credential_profile_value
               NOT IN ('TENANT_ADMIN', 'PLATFORM_ADMIN')
        )
  );
SELECT COUNT(*) AS ready_db_assertion_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS ready_db_assertion_check
FROM api_test_case WHERE enabled=1 AND automation_status='READY'
  AND CAST(assertions AS CHAR) LIKE '%DB_ASSERTION_REQUIRED%';

SELECT COUNT(*) AS ready_unsupported_setup_action_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS ready_setup_action_check
FROM api_test_case t
WHERE enabled=1 AND automation_status='READY'
  AND EXISTS (SELECT 1 FROM JSON_TABLE(t.setup_steps, '$[*]' COLUMNS (action_value VARCHAR(64) PATH '$.action')) x
              WHERE x.action_value NOT IN ('get_csrf','login','refresh','logout','create_token_fixture'));

SELECT case_no,
       data_dependency AS required_placeholder_list,
       CASE
         WHEN (CAST(headers_template AS CHAR) LIKE CONCAT('%',CHAR(36),'{%')
            OR CAST(cookie_template AS CHAR) LIKE CONCAT('%',CHAR(36),'{%')
            OR COALESCE(request_body_template,'') LIKE CONCAT('%',CHAR(36),'{%'))
          AND data_dependency IS NOT NULL
          AND data_dependency LIKE CONCAT('%',CHAR(36),'{%') THEN 'DECLARED'
         WHEN NOT (CAST(headers_template AS CHAR) LIKE CONCAT('%',CHAR(36),'{%')
                OR CAST(cookie_template AS CHAR) LIKE CONCAT('%',CHAR(36),'{%')
                OR COALESCE(request_body_template,'') LIKE CONCAT('%',CHAR(36),'{%')) THEN 'NONE'
         ELSE 'MISSING'
       END AS placeholder_source_check
FROM api_test_case
WHERE enabled=1 AND automation_status='READY'
ORDER BY execution_order;

SELECT COUNT(*) AS ready_undeclared_placeholder_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS ready_placeholder_check
FROM api_test_case
WHERE enabled=1 AND automation_status='READY'
  AND ((CAST(headers_template AS CHAR) LIKE CONCAT('%',CHAR(36),'{%')
     OR CAST(cookie_template AS CHAR) LIKE CONCAT('%',CHAR(36),'{%')
     OR COALESCE(request_body_template,'') LIKE CONCAT('%',CHAR(36),'{%'))
  AND (data_dependency IS NULL OR data_dependency NOT LIKE CONCAT('%',CHAR(36),'{%')));

SELECT COUNT(*) AS ready_login_session_without_teardown_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS ready_teardown_check
FROM api_test_case
WHERE enabled=1 AND automation_status='READY'
  AND JSON_SEARCH(setup_steps, 'one', 'login', NULL, '$[*].action') IS NOT NULL
  AND JSON_SEARCH(teardown_steps, 'one', 'logout', NULL, '$[*].action') IS NULL;

SELECT COUNT(*) AS automation_tag_status_mismatch_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS automation_tag_status_check
FROM api_test_case
WHERE enabled=1 AND case_no LIKE 'AUTH-%'
  AND JSON_CONTAINS(tags, JSON_QUOTE(automation_status), '$')=0;

SELECT COUNT(*) AS missing_execution_field_count, CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS execution_field_check
FROM api_test_case
WHERE enabled=1 AND case_no LIKE 'AUTH-%'
  AND (setup_steps IS NULL OR extractors IS NULL OR teardown_steps IS NULL OR tags IS NULL
    OR execution_order IS NULL OR automation_status IS NULL OR environment_scope IS NULL
    OR JSON_TYPE(setup_steps)<>'ARRAY' OR JSON_TYPE(extractors)<>'ARRAY'
    OR JSON_TYPE(teardown_steps)<>'ARRAY' OR JSON_TYPE(tags)<>'ARRAY');
