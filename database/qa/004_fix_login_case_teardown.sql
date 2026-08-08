SET NAMES utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE shipflow_qa;

UPDATE api_test_case
SET teardown_steps = JSON_ARRAY(
    JSON_OBJECT(
        'action',
        'logout',
        'context',
        JSON_OBJECT(
            'xsrf_token',
            'XSRF_TOKEN',
            'refresh_cookie',
            'REFRESH_COOKIE'
        )
    )
)
WHERE case_no IN (
    'AUTH-LOGIN-001',
    'AUTH-LOGIN-002'
)
  AND enabled = 1
  AND automation_status = 'READY';

SELECT
    case_no,
    teardown_steps
FROM api_test_case
WHERE case_no IN (
    'AUTH-LOGIN-001',
    'AUTH-LOGIN-002'
)
ORDER BY case_no;
