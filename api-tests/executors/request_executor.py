from typing import Any

from clients.http_client import HttpClient
from common.placeholder_resolver import PlaceholderResolver
from common.scenario_context import ScenarioContext
from models.api_test_case import ApiTestCase


class RequestExecutionError(ValueError):
    pass


class RequestExecutor:
    def __init__(
        self,
        http_client: HttpClient,
        placeholder_resolver: PlaceholderResolver,
    ):
        self.http_client = http_client
        self.placeholder_resolver = placeholder_resolver

    def execute(
        self,
        test_case: ApiTestCase,
        scenario_context: ScenarioContext,
    ):
        request_path = self.placeholder_resolver.resolve(
            test_case.request_path,
            scenario_context,
        )

        if not isinstance(request_path, str):
            raise RequestExecutionError(
                "解析后的请求路径必须是字符串"
            )

        resolved_headers = self.placeholder_resolver.resolve(
            test_case.headers_template,
            scenario_context,
        )

        resolved_cookies = self.placeholder_resolver.resolve(
            test_case.cookie_template,
            scenario_context,
        )

        request_headers = self._normalize_mapping(
            resolved_headers,
            "headers_template",
        )

        request_cookies = self._normalize_mapping(
            resolved_cookies,
            "cookie_template",
        )

        request_kwargs: dict[str, Any] = {
            "headers": request_headers,
            "cookies": request_cookies,
        }

        if test_case.request_body_template is not None:
            resolved_body = (
                self.placeholder_resolver.resolve(
                    test_case.request_body_template,
                    scenario_context,
                )
            )

            if isinstance(resolved_body, str):
                request_kwargs["data"] = resolved_body

            elif isinstance(resolved_body, (dict, list)):
                request_kwargs["json"] = resolved_body

            else:
                raise RequestExecutionError(
                    "请求体必须是字符串、字典、"
                    "列表或者None"
                )

        self.http_client.clear_cookies()

        return self.http_client.request(
            method=test_case.http_method,
            path=request_path,
            **request_kwargs,
        )

    @staticmethod
    def _normalize_mapping(
        value: Any,
        field_name: str,
    ) -> dict[str, str]:
        if not isinstance(value, dict):
            raise RequestExecutionError(
                f"{field_name}必须是字典"
            )

        normalized_value: dict[str, str] = {}

        for key, item_value in value.items():
            if item_value is None or isinstance(
                item_value,
                (dict, list, tuple, set),
            ):
                raise RequestExecutionError(
                    f"{field_name}中的值必须是标量："
                    f"{key}"
                )

            normalized_value[str(key)] = str(item_value)

        return normalized_value