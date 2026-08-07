SET NAMES utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE shipflow_qa;

UPDATE api_test_case
SET assertions = JSON_ARRAY(
    JSON_OBJECT(
        'source', 'header',
        'path', 'X-Trace-Id',
        'operator', 'exists',
        'expected', NULL
    ),
    JSON_OBJECT(
        'source', 'body',
        'path', NULL,
        'operator', 'not_contains',
        'expected', 'passwordHash'
    )
)
WHERE case_no = 'AUTH-LOGIN-004';

SELECT case_no, assertions
FROM api_test_case
WHERE case_no = 'AUTH-LOGIN-004';
