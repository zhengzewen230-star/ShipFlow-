from datetime import datetime

import pytest

from common.placeholder_resolver import PlaceholderResolver
from common.scenario_context import ScenarioContext
from executors.request_executor import (
    RequestExecutionError,
    RequestExecutor,
)
from models.api_test_case import ApiTestCase


class FakeHttpClient:
    def __init__(self):
        self.clear_cookie_count = 0
        self.request_calls = []
        self.response = object()

    def clear_cookies(self):
        self.clear_cookie_count += 1

    def request(self, method, path, **kwargs):
        self.request_calls.append(
            {
                "method": method,
                "path": path,
                "kwargs": kwargs,
            }
        )

        return self.response


def build_test_case(
    *,
    request_path="/api/v1/auth/login",
    headers_template=None,
    cookie_template=None,
    request_body_template=None,
):
    current_time = datetime(2026, 1, 1)

    return ApiTestCase(
        id=1,
        case_no="AUTH-TEST-001",
        module="AUTH_LOGIN",
        title="RequestExecutor测试",
        test_type="NORMAL",
        priority="P0",
        precondition="无",
        http_method="POST",
        request_path=request_path,
        headers_template=headers_template or {},
        cookie_template=cookie_template or {},
        request_body_template=request_body_template,
        expected_status=200,
        expected_error_code=None,
        assertions=[],
        data_dependency="无",
        enabled=True,
        setup_steps=[],
        extractors=[],
        teardown_steps=[],
        tags=["framework"],
        execution_order=1,
        automation_status="READY",
        environment_scope="QA",
        created_at=current_time,
        updated_at=current_time,
    )


def test_execute_resolves_request_templates():
    http_client = FakeHttpClient()

    executor = RequestExecutor(
        http_client=http_client,
        placeholder_resolver=PlaceholderResolver(),
    )

    context = ScenarioContext()
    context.set("XSRF_TOKEN", "xsrf-value")
    context.set("VALID_PASSWORD", "password-value")

    test_case = build_test_case(
        headers_template={
            "Content-Type": "application/json",
            "X-XSRF-TOKEN": "${XSRF_TOKEN}",
        },
        cookie_template={
            "XSRF-TOKEN": "${XSRF_TOKEN}",
        },
        request_body_template=(
            '{"password":"${VALID_PASSWORD}"}'
        ),
    )

    response = executor.execute(
        test_case,
        context,
    )

    assert response is http_client.response
    assert http_client.clear_cookie_count == 1

    request_call = http_client.request_calls[0]

    assert request_call["method"] == "POST"
    assert (
        request_call["path"]
        == "/api/v1/auth/login"
    )

    assert request_call["kwargs"]["headers"] == {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": "xsrf-value",
    }

    assert request_call["kwargs"]["cookies"] == {
        "XSRF-TOKEN": "xsrf-value",
    }

    assert request_call["kwargs"]["data"] == (
        '{"password":"password-value"}'
    )


def test_execute_preserves_malformed_json():
    http_client = FakeHttpClient()

    executor = RequestExecutor(
        http_client,
        PlaceholderResolver(),
    )

    test_case = build_test_case(
        headers_template={
            "Content-Type": "application/json",
        },
        request_body_template='{"tenantCode":',
    )

    executor.execute(
        test_case,
        ScenarioContext(),
    )

    request_call = http_client.request_calls[0]

    assert request_call["kwargs"]["data"] == (
        '{"tenantCode":'
    )

    assert "json" not in request_call["kwargs"]


def test_execute_without_body_does_not_send_data():
    http_client = FakeHttpClient()

    executor = RequestExecutor(
        http_client,
        PlaceholderResolver(),
    )

    test_case = build_test_case(
        request_body_template=None,
    )

    executor.execute(
        test_case,
        ScenarioContext(),
    )

    request_kwargs = (
        http_client.request_calls[0]["kwargs"]
    )

    assert "data" not in request_kwargs
    assert "json" not in request_kwargs


def test_execute_does_not_modify_test_case():
    http_client = FakeHttpClient()

    executor = RequestExecutor(
        http_client,
        PlaceholderResolver(),
    )

    test_case = build_test_case(
        headers_template={
            "Authorization":
                "Bearer ${ACCESS_TOKEN}",
        },
    )

    context = ScenarioContext()
    context.set(
        "ACCESS_TOKEN",
        "access-value",
    )

    executor.execute(test_case, context)

    assert test_case.headers_template == {
        "Authorization":
            "Bearer ${ACCESS_TOKEN}",
    }


def test_structured_header_value_is_rejected():
    http_client = FakeHttpClient()

    executor = RequestExecutor(
        http_client,
        PlaceholderResolver(),
    )

    test_case = build_test_case(
        headers_template={
            "X-Test-Items": "${ITEMS}",
        },
    )

    context = ScenarioContext()
    context.set("ITEMS", [1, 2, 3])

    with pytest.raises(
        RequestExecutionError,
        match="X-Test-Items",
    ):
        executor.execute(
            test_case,
            context,
        )