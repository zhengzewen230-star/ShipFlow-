import base64
import json
from types import SimpleNamespace

import pytest

from common.json_path import JsonPathResolver
from common.placeholder_resolver import PlaceholderResolver
from common.scenario_context import ScenarioContext
from executors.assertion_executor import (
    AssertionExecutionError,
    AssertionExecutor,
)


class FakeResponse:
    def __init__(
        self,
        *,
        status_code=200,
        json_body=None,
        text="",
        headers=None,
        cookies=None,
    ):
        self.status_code = status_code
        self.json_body = json_body
        self.text = text
        self.headers = headers or {}
        self.cookies = cookies or {}

    def json(self):
        if self.json_body is None:
            raise ValueError("No JSON")

        return self.json_body


def build_test_case(
    *,
    expected_status=200,
    expected_error_code=None,
    assertions=None,
):
    return SimpleNamespace(
        case_no="AUTH-ASSERT-001",
        expected_status=expected_status,
        expected_error_code=expected_error_code,
        assertions=assertions or [],
    )


@pytest.fixture
def assertion_executor():
    return AssertionExecutor(
        json_path_resolver=JsonPathResolver(),
        placeholder_resolver=PlaceholderResolver(),
    )


def create_test_jwt(claims):
    header = {
        "alg": "none",
        "typ": "JWT",
    }

    def encode(value):
        json_bytes = json.dumps(
            value
        ).encode("utf-8")

        return base64.urlsafe_b64encode(
            json_bytes
        ).decode("ascii").rstrip("=")

    return (
        f"{encode(header)}."
        f"{encode(claims)}."
        "test-signature"
    )


def test_status_json_header_cookie_and_body(
    assertion_executor,
):
    response = FakeResponse(
        status_code=200,
        json_body={
            "success": True,
            "data": {
                "accessToken": "token-value",
            },
        },
        text='{"success":true}',
        headers={
            "Cache-Control": "no-store",
            "X-Trace-Id": "trace-value",
        },
        cookies={
            "REFRESH_TOKEN": "refresh-value",
        },
    )

    test_case = build_test_case(
        assertions=[
            {
                "source": "json",
                "path": "$.success",
                "operator": "eq",
                "expected": True,
            },
            {
                "source": "json",
                "path": "$.data.accessToken",
                "operator": "not_empty",
                "expected": None,
            },
            {
                "source": "header",
                "path": "Cache-Control",
                "operator": "contains",
                "expected": "no-store",
            },
            {
                "source": "cookie",
                "path": "REFRESH_TOKEN",
                "operator": "exists",
                "expected": None,
            },
            {
                "source": "body",
                "path": None,
                "operator": "not_contains",
                "expected": "password",
            },
        ]
    )

    assertion_executor.execute(
        response=response,
        test_case=test_case,
        scenario_context=ScenarioContext(),
    )


def test_expected_error_code(
    assertion_executor,
):
    response = FakeResponse(
        status_code=401,
        json_body={
            "success": False,
            "error": {
                "code": "AUTH-1001",
            },
        },
    )

    test_case = build_test_case(
        expected_status=401,
        expected_error_code="AUTH-1001",
    )

    assertion_executor.execute(
        response=response,
        test_case=test_case,
        scenario_context=ScenarioContext(),
    )


def test_not_exists_json_path(
    assertion_executor,
):
    response = FakeResponse(
        json_body={
            "data": {
                "scope": "PLATFORM",
            }
        }
    )

    test_case = build_test_case(
        assertions=[
            {
                "source": "json",
                "path": "$.data.tenantId",
                "operator": "not_exists",
                "expected": None,
            }
        ]
    )

    assertion_executor.execute(
        response=response,
        test_case=test_case,
        scenario_context=ScenarioContext(),
    )


def test_jwt_claim_assertion(
    assertion_executor,
):
    token_value = create_test_jwt(
        {
            "scope": "PLATFORM",
        }
    )

    response = FakeResponse(
        json_body={
            "data": {
                "accessToken": token_value,
            }
        }
    )

    test_case = build_test_case(
        assertions=[
            {
                "source": "jwt_claim",
                "path": "scope",
                "token_from":
                    "$.data.accessToken",
                "operator": "eq",
                "expected": "PLATFORM",
            },
            {
                "source": "jwt_claim",
                "path": "tenant_id",
                "token_from":
                    "$.data.accessToken",
                "operator": "not_exists",
                "expected": None,
            },
        ]
    )

    assertion_executor.execute(
        response=response,
        test_case=test_case,
        scenario_context=ScenarioContext(),
    )


def test_expected_placeholder_is_resolved(
    assertion_executor,
):
    context = ScenarioContext()

    context.set(
        "OLD_REFRESH_COOKIE",
        "old-cookie-value",
    )

    response = FakeResponse(
        cookies={
            "REFRESH_TOKEN": "new-cookie-value",
        }
    )

    test_case = build_test_case(
        assertions=[
            {
                "source": "cookie",
                "path": "REFRESH_TOKEN",
                "operator": "ne",
                "expected":
                    "${OLD_REFRESH_COOKIE}",
            }
        ]
    )

    assertion_executor.execute(
        response=response,
        test_case=test_case,
        scenario_context=context,
    )


def test_assertion_failure_does_not_expose_secret(
    assertion_executor,
):
    context = ScenarioContext()

    context.set(
        "ACCESS_TOKEN",
        "secret-token-value",
    )

    response = FakeResponse(
        text="different-value"
    )

    test_case = build_test_case(
        assertions=[
            {
                "source": "body",
                "path": None,
                "operator": "contains",
                "expected": "${ACCESS_TOKEN}",
            }
        ]
    )

    with pytest.raises(
        AssertionError,
    ) as exception_info:
        assertion_executor.execute(
            response=response,
            test_case=test_case,
            scenario_context=context,
        )

    assert (
        "secret-token-value"
        not in str(exception_info.value)
    )


def test_invalid_operator_is_rejected(
    assertion_executor,
):
    response = FakeResponse(
        text="response"
    )

    test_case = build_test_case(
        assertions=[
            {
                "source": "body",
                "path": None,
                "operator": "greater_than",
                "expected": 1,
            }
        ]
    )

    with pytest.raises(
        AssertionExecutionError,
        match="greater_than",
    ):
        assertion_executor.execute(
            response=response,
            test_case=test_case,
            scenario_context=ScenarioContext(),
        )