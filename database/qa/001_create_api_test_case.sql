-- ShipFlow API 自动化测试用例库初始化脚本
-- 仅创建独立 QA 数据库，不引用或修改 shipflow、shipflow_test。
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS shipflow_qa
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE shipflow_qa;

CREATE TABLE IF NOT EXISTS api_test_case (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '测试用例主键',
    case_no VARCHAR(32) NOT NULL COMMENT '测试用例唯一编号',
    module VARCHAR(64) NOT NULL COMMENT '所属模块',
    title VARCHAR(255) NOT NULL COMMENT '测试用例标题',
    test_type VARCHAR(32) NOT NULL COMMENT '测试类型：NORMAL、EXCEPTION、SECURITY、BOUNDARY、STATE_FLOW',
    priority VARCHAR(8) NOT NULL COMMENT '优先级：P0、P1、P2、P3',
    precondition TEXT NOT NULL COMMENT '执行前置条件',
    http_method VARCHAR(16) NOT NULL COMMENT 'HTTP方法',
    request_path VARCHAR(255) NOT NULL COMMENT '请求路径',
    headers_template JSON NOT NULL COMMENT '请求头模板，不保存真实Token或密钥',
    cookie_template JSON NOT NULL COMMENT 'Cookie模板，不保存真实Refresh Token',
    request_body_template LONGTEXT NULL COMMENT '请求体模板，允许保存畸形JSON测试输入',
    expected_status INT NOT NULL COMMENT '预期HTTP状态码',
    expected_error_code VARCHAR(64) NULL COMMENT '预期业务错误码，成功时为空',
    assertions JSON NOT NULL COMMENT '自动化断言列表',
    data_dependency TEXT NOT NULL COMMENT '测试数据依赖及占位符说明',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0禁用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_test_case_no (case_no),
    KEY idx_api_test_case_module_enabled (module, enabled),
    KEY idx_api_test_case_path_method (request_path, http_method),
    KEY idx_api_test_case_type_priority (test_type, priority),
    CONSTRAINT chk_api_test_case_type CHECK (
        test_type IN ('NORMAL', 'EXCEPTION', 'SECURITY', 'BOUNDARY', 'STATE_FLOW')
    ),
    CONSTRAINT chk_api_test_case_priority CHECK (priority IN ('P0', 'P1', 'P2', 'P3')),
    CONSTRAINT chk_api_test_case_method CHECK (http_method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE')),
    CONSTRAINT chk_api_test_case_status CHECK (expected_status BETWEEN 100 AND 599),
    CONSTRAINT chk_api_test_case_enabled CHECK (enabled IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='ShipFlow API自动化测试用例';
