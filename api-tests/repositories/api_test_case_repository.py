import json
import os
from typing import Any

import pymysql
from pymysql.cursors import DictCursor

from models.api_test_case import ApiTestCase


class ApiTestCaseRepository:
    def __init__(self, database_config: dict[str, Any]):
        password_env = database_config["password_env"]
        password = os.getenv(password_env)

        if not password:
            raise RuntimeError(
                f"缺少数据库密码环境变量：{password_env}"
            )

        self.connection_config = {
            "host": database_config["host"],
            "port": int(database_config["port"]),
            "user": database_config["username"],
            "password": password,
            "database": database_config["database"],
            "charset": database_config.get("charset", "utf8mb4"),
            "connect_timeout": int(
                database_config.get("connect_timeout", 5)
            ),
            "cursorclass": DictCursor,
            "autocommit": True,
        }

    def find_ready_cases(
        self,
        environment_scope: str,
        module: str | None = None,
    ) -> list[ApiTestCase]:
        sql = """
            SELECT
                id,
                case_no,
                module,
                title,
                test_type,
                priority,
                precondition,
                http_method,
                request_path,
                headers_template,
                cookie_template,
                request_body_template,
                expected_status,
                expected_error_code,
                assertions,
                data_dependency,
                enabled,
                created_at,
                updated_at,
                setup_steps,
                extractors,
                teardown_steps,
                tags,
                execution_order,
                automation_status,
                environment_scope
            FROM api_test_case
            WHERE enabled = 1
              AND automation_status = 'READY'
              AND environment_scope = %s
        """

        parameters: list[Any] = [environment_scope]

        if module is not None:
            sql += " AND module = %s"
            parameters.append(module)

        sql += " ORDER BY execution_order, case_no"

        with pymysql.connect(**self.connection_config) as connection:
            with connection.cursor() as cursor:
                cursor.execute(sql, tuple(parameters))
                rows = cursor.fetchall()

        return [self._row_to_case(row) for row in rows]

    def find_module2_frozen_cases(self) -> list[ApiTestCase]:
        """按冻结 case_no 清单读取第二模块，拒绝序号范围造成的静默漏选。"""
        from common.module2_case_catalog import MODULE2_FROZEN_CASE_NOS

        placeholders = ", ".join(["%s"] * len(MODULE2_FROZEN_CASE_NOS))
        sql = f"""
            SELECT id, case_no, module, title, test_type, priority, precondition,
                   http_method, request_path, headers_template, cookie_template,
                   request_body_template, expected_status, expected_error_code,
                   assertions, data_dependency, enabled, created_at, updated_at,
                   setup_steps, extractors, teardown_steps, tags, execution_order,
                   automation_status, environment_scope
            FROM api_test_case
            WHERE case_no IN ({placeholders})
            ORDER BY execution_order, case_no
        """
        with pymysql.connect(**self.connection_config) as connection:
            with connection.cursor() as cursor:
                cursor.execute(sql, MODULE2_FROZEN_CASE_NOS)
                rows = cursor.fetchall()
        found = {row["case_no"] for row in rows}
        missing = sorted(set(MODULE2_FROZEN_CASE_NOS) - found)
        if missing:
            raise RuntimeError("第二模块冻结用例缺失：" + ", ".join(missing))
        if len(rows) != len(MODULE2_FROZEN_CASE_NOS):
            raise RuntimeError("第二模块冻结用例数量不是 66")
        return [self._row_to_case(row) for row in rows]

    @staticmethod
    def _row_to_case(row: dict[str, Any]) -> ApiTestCase:
        case_data = dict(row)

        case_data["headers_template"] = (
            ApiTestCaseRepository._decode_json_object(
                case_data["headers_template"],
                "headers_template",
            )
        )

        case_data["cookie_template"] = (
            ApiTestCaseRepository._decode_json_object(
                case_data["cookie_template"],
                "cookie_template",
            )
        )

        for field_name in (
            "assertions",
            "setup_steps",
            "extractors",
            "teardown_steps",
            "tags",
        ):
            case_data[field_name] = (
                ApiTestCaseRepository._decode_json_array(
                    case_data[field_name],
                    field_name,
                )
            )

        case_data["enabled"] = bool(case_data["enabled"])

        return ApiTestCase(**case_data)

    @staticmethod
    def _decode_json_object(
        value: Any,
        field_name: str,
    ) -> dict[str, Any]:
        decoded_value = (
            json.loads(value)
            if isinstance(value, str)
            else value
        )

        if not isinstance(decoded_value, dict):
            raise ValueError(
                f"{field_name} 必须是JSON对象"
            )

        return decoded_value

    @staticmethod
    def _decode_json_array(
        value: Any,
        field_name: str,
    ) -> list[Any]:
        decoded_value = (
            json.loads(value)
            if isinstance(value, str)
            else value
        )

        if not isinstance(decoded_value, list):
            raise ValueError(
                f"{field_name} 必须是JSON数组"
            )

        return decoded_value
