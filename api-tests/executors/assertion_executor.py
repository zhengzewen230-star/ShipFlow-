import base64
import json
from typing import Any

from common.json_path import (
    JsonPathError,
    JsonPathResolver,
)
from common.placeholder_resolver import PlaceholderResolver
from common.scenario_context import ScenarioContext
from models.api_test_case import ApiTestCase


class AssertionExecutionError(ValueError):
    pass


MISSING = object()


class AssertionExecutor:
    SUPPORTED_SOURCES = {
        "status_code",
        "json",
        "header",
        "cookie",
        "body",
        "jwt_claim",
    }

    SUPPORTED_OPERATORS = {
        "eq",
        "ne",
        "exists",
        "not_exists",
        "not_empty",
        "contains",
        "not_contains",
        "is_empty",
    }

    def __init__(
        self,
        json_path_resolver: JsonPathResolver,
        placeholder_resolver: PlaceholderResolver,
    ):
        self.json_path_resolver = json_path_resolver
        self.placeholder_resolver = placeholder_resolver

    def execute(
        self,
        response,
        test_case: ApiTestCase,
        scenario_context: ScenarioContext,
    ) -> None:
        self._assert_status_code(
            response=response,
            test_case=test_case,
        )

        self._assert_error_code(
            response=response,
            test_case=test_case,
        )

        for assertion_index, assertion in enumerate(
            test_case.assertions,
            start=1,
        ):
            resolved_assertion = (
                self.placeholder_resolver.resolve(
                    assertion,
                    scenario_context,
                )
            )

            self._validate_assertion(
                resolved_assertion
            )

            actual_value = self._get_actual_value(
                response=response,
                assertion=resolved_assertion,
            )

            assertion_passed = self._compare(
                actual=actual_value,
                expected=resolved_assertion["expected"],
                operator=resolved_assertion["operator"],
            )

            if not assertion_passed:
                raise AssertionError(
                    self._build_failure_message(
                        test_case=test_case,
                        assertion_index=assertion_index,
                        assertion=resolved_assertion,
                    )
                )

    @staticmethod
    def _assert_status_code(
        response,
        test_case: ApiTestCase,
    ) -> None:
        if (
            response.status_code
            != test_case.expected_status
        ):
            raise AssertionError(
                f"用例{test_case.case_no}状态码断言失败："
                f"期望{test_case.expected_status}，"
                f"实际{response.status_code}"
            )

    def _assert_error_code(
        self,
        response,
        test_case: ApiTestCase,
    ) -> None:
        if test_case.expected_error_code is None:
            return

        try:
            response_json = response.json()

            actual_error_code = (
                self.json_path_resolver.resolve(
                    response_json,
                    "$.error.code",
                )
            )
        except (ValueError, JsonPathError) as exception:
            raise AssertionError(
                f"用例{test_case.case_no}"
                "未返回预期的业务错误结构"
            ) from exception

        if (
            actual_error_code
            != test_case.expected_error_code
        ):
            raise AssertionError(
                f"用例{test_case.case_no}"
                "业务错误码断言失败："
                f"期望{test_case.expected_error_code}，"
                f"实际{actual_error_code}"
            )

    def _get_actual_value(
        self,
        response,
        assertion: dict[str, Any],
    ) -> Any:
        source = assertion["source"]
        path = assertion["path"]

        if source == "status_code":
            return response.status_code

        if source == "body":
            return response.text

        if source == "header":
            value = response.headers.get(path)

            return MISSING if value is None else value

        if source == "cookie":
            value = response.cookies.get(path)

            return MISSING if value is None else value

        if source == "json":
            return self._get_json_value(
                response=response,
                path=path,
            )

        if source == "jwt_claim":
            return self._get_jwt_claim(
                response=response,
                assertion=assertion,
            )

        raise AssertionExecutionError(
            f"不支持的断言来源：{source}"
        )

    def _get_json_value(
        self,
        response,
        path: str,
    ) -> Any:
        try:
            response_json = response.json()
        except ValueError as exception:
            raise AssertionExecutionError(
                "响应体不是合法JSON"
            ) from exception

        try:
            return self.json_path_resolver.resolve(
                response_json,
                path,
            )
        except JsonPathError:
            return MISSING

    def _get_jwt_claim(
        self,
        response,
        assertion: dict[str, Any],
    ) -> Any:
        token_from = assertion.get("token_from")

        if not isinstance(token_from, str):
            raise AssertionExecutionError(
                "jwt_claim断言缺少token_from"
            )

        if token_from.startswith("$"):
            token_value = self._get_json_value(
                response=response,
                path=token_from,
            )
        else:
            token_value = token_from

        if token_value is MISSING:
            return MISSING

        if not isinstance(token_value, str):
            raise AssertionExecutionError(
                "JWT Token必须是字符串"
            )

        claims = self._decode_jwt_payload(
            token_value
        )

        claim_name = assertion["path"]

        if claim_name not in claims:
            return MISSING

        return claims[claim_name]

    @staticmethod
    def _decode_jwt_payload(
        token_value: str,
    ) -> dict[str, Any]:
        token_parts = token_value.split(".")

        if len(token_parts) != 3:
            raise AssertionExecutionError(
                "JWT格式不正确"
            )

        encoded_payload = token_parts[1]
        padding = "=" * (
            -len(encoded_payload) % 4
        )

        try:
            payload_bytes = base64.urlsafe_b64decode(
                encoded_payload + padding
            )

            payload = json.loads(
                payload_bytes.decode("utf-8")
            )
        except (
            ValueError,
            UnicodeDecodeError,
            json.JSONDecodeError,
        ) as exception:
            raise AssertionExecutionError(
                "JWT Payload无法解析"
            ) from exception

        if not isinstance(payload, dict):
            raise AssertionExecutionError(
                "JWT Payload必须是JSON对象"
            )

        return payload

    @classmethod
    def _validate_assertion(
        cls,
        assertion: Any,
    ) -> None:
        if not isinstance(assertion, dict):
            raise AssertionExecutionError(
                "assertion必须是字典"
            )

        required_fields = {
            "source",
            "path",
            "operator",
            "expected",
        }

        missing_fields = (
            required_fields - assertion.keys()
        )

        if missing_fields:
            raise AssertionExecutionError(
                "assertion缺少字段："
                + ", ".join(sorted(missing_fields))
            )

        if assertion["source"] not in (
            cls.SUPPORTED_SOURCES
        ):
            raise AssertionExecutionError(
                "不支持的断言来源："
                f"{assertion['source']}"
            )

        if assertion["operator"] not in (
            cls.SUPPORTED_OPERATORS
        ):
            raise AssertionExecutionError(
                "不支持的断言操作符："
                f"{assertion['operator']}"
            )

        if (
            assertion["source"] == "jwt_claim"
            and "token_from" not in assertion
        ):
            raise AssertionExecutionError(
                "jwt_claim断言缺少token_from"
            )

    @staticmethod
    def _compare(
        actual: Any,
        expected: Any,
        operator: str,
    ) -> bool:
        if operator == "exists":
            return actual is not MISSING

        if operator == "not_exists":
            return actual is MISSING

        if actual is MISSING:
            return False

        if operator == "eq":
            return actual == expected

        if operator == "ne":
            return actual != expected

        if operator == "not_empty":
            return not AssertionExecutor._is_empty(
                actual
            )

        if operator == "is_empty":
            return AssertionExecutor._is_empty(
                actual
            )

        if operator == "contains":
            return AssertionExecutor._contains(
                actual,
                expected,
            )

        if operator == "not_contains":
            return not AssertionExecutor._contains(
                actual,
                expected,
            )

        raise AssertionExecutionError(
            f"不支持的断言操作符：{operator}"
        )

    @staticmethod
    def _is_empty(value: Any) -> bool:
        return value is None or value in (
            "",
            [],
            {},
            (),
        )

    @staticmethod
    def _contains(
        actual: Any,
        expected: Any,
    ) -> bool:
        try:
            return expected in actual
        except TypeError:
            return False

    @staticmethod
    def _build_failure_message(
        test_case: ApiTestCase,
        assertion_index: int,
        assertion: dict[str, Any],
    ) -> str:
        return (
            f"用例{test_case.case_no}"
            f"第{assertion_index}条断言失败："
            f"source={assertion['source']}，"
            f"path={assertion['path']}，"
            f"operator={assertion['operator']}"
        )