import pytest

from common.json_path import JsonPathResolver
from common.scenario_context import ScenarioContext
from executors.action_executor import (
    ActionExecutionError,
    ActionExecutor,
)
from executors.extractor_executor import ExtractorExecutor


class FakeResponse:
    def __init__(
        self,
        *,
        status_code,
        json_body=None,
        cookies=None,
        headers=None,
    ):
        self.status_code = status_code
        self.json_body = json_body
        self.cookies = cookies or {}
        self.headers = headers or {}

    def json(self):
        return self.json_body


class FakeAuthClient:
    def __init__(self):
        self.csrf_responses = []
        self.login_response = None
        self.logout_response = None

        self.login_calls = []
        self.logout_calls = []

    def get_csrf_token(self):
        return self.csrf_responses.pop(0)

    def login(
        self,
        tenant_code,
        username,
        password,
        xsrf_token,
    ):
        self.login_calls.append(
            {
                "tenant_code": tenant_code,
                "username": username,
                "password": password,
                "xsrf_token": xsrf_token,
            }
        )

        return self.login_response

    def logout(
        self,
        xsrf_token,
        refresh_token,
    ):
        self.logout_calls.append(
            {
                "xsrf_token": xsrf_token,
                "refresh_token": refresh_token,
            }
        )

        return self.logout_response


@pytest.fixture
def credential_profiles():
    return {
        "TENANT_ADMIN": {
            "tenant_code": "TENANT_DEMO_001",
            "username": "merchant_admin_001",
            "password_env": "TENANT_PASSWORD",
        },
        "PLATFORM_ADMIN": {
            "tenant_code": None,
            "username": "platform_admin",
            "password_env": "PLATFORM_PASSWORD",
        },
    }


def build_action_executor(
    *,
    auth_client,
    credential_profiles,
    environment=None,
):
    extractor_executor = ExtractorExecutor(
        json_path_resolver=JsonPathResolver()
    )

    return ActionExecutor(
        auth_client=auth_client,
        extractor_executor=extractor_executor,
        credential_profiles=credential_profiles,
        environment=environment or {},
    )


def test_get_csrf_saves_token(
    credential_profiles,
):
    auth_client = FakeAuthClient()

    auth_client.csrf_responses.append(
        FakeResponse(
            status_code=204,
            cookies={
                "XSRF-TOKEN": "xsrf-value",
            },
        )
    )

    executor = build_action_executor(
        auth_client=auth_client,
        credential_profiles=credential_profiles,
    )

    context = ScenarioContext()

    responses = executor.execute(
        steps=[
            {
                "action": "get_csrf",
                "save": {
                    "cookie.XSRF-TOKEN":
                        "XSRF_TOKEN",
                },
            }
        ],
        scenario_context=context,
    )

    assert len(responses) == 1
    assert responses[0].status_code == 204

    assert (
        context["XSRF_TOKEN"]
        == "xsrf-value"
    )


def test_login_uses_tenant_profile_and_saves_tokens(
    credential_profiles,
):
    auth_client = FakeAuthClient()

    auth_client.login_response = FakeResponse(
        status_code=200,
        json_body={
            "data": {
                "accessToken": "access-value",
            }
        },
        cookies={
            "REFRESH_TOKEN": "refresh-value",
        },
    )

    executor = build_action_executor(
        auth_client=auth_client,
        credential_profiles=credential_profiles,
        environment={
            "TENANT_PASSWORD": "tenant-password",
        },
    )

    context = ScenarioContext()
    context.set("XSRF_TOKEN", "xsrf-value")

    executor.execute(
        steps=[
            {
                "action": "login",
                "credential_profile": "TENANT_ADMIN",
                "save": {
                    "json.$.data.accessToken":
                        "ACCESS_TOKEN",
                    "cookie.REFRESH_TOKEN":
                        "REFRESH_COOKIE",
                },
            }
        ],
        scenario_context=context,
    )

    assert auth_client.login_calls == [
        {
            "tenant_code": "TENANT_DEMO_001",
            "username": "merchant_admin_001",
            "password": "tenant-password",
            "xsrf_token": "xsrf-value",
        }
    ]

    assert (
        context["ACCESS_TOKEN"]
        == "access-value"
    )

    assert (
        context["REFRESH_COOKIE"]
        == "refresh-value"
    )


def test_platform_login_uses_null_tenant_code(
    credential_profiles,
):
    auth_client = FakeAuthClient()

    auth_client.login_response = FakeResponse(
        status_code=200,
        json_body={
            "data": {
                "accessToken": "platform-token",
            }
        },
    )

    executor = build_action_executor(
        auth_client=auth_client,
        credential_profiles=credential_profiles,
        environment={
            "PLATFORM_PASSWORD":
                "platform-password",
        },
    )

    context = ScenarioContext()
    context.set("XSRF_TOKEN", "xsrf-value")

    executor.execute(
        steps=[
            {
                "action": "login",
                "credential_profile":
                    "PLATFORM_ADMIN",
                "save": {
                    "json.$.data.accessToken":
                        "PLATFORM_ACCESS_TOKEN",
                },
            }
        ],
        scenario_context=context,
    )

    login_call = auth_client.login_calls[0]

    assert login_call["tenant_code"] is None
    assert (
        login_call["username"]
        == "platform_admin"
    )

    assert (
        context["PLATFORM_ACCESS_TOKEN"]
        == "platform-token"
    )


def test_login_fails_when_password_env_missing(
    credential_profiles,
):
    auth_client = FakeAuthClient()

    executor = build_action_executor(
        auth_client=auth_client,
        credential_profiles=credential_profiles,
        environment={},
    )

    context = ScenarioContext()
    context.set("XSRF_TOKEN", "xsrf-value")

    with pytest.raises(
        ActionExecutionError,
        match="TENANT_PASSWORD",
    ):
        executor.execute(
            steps=[
                {
                    "action": "login",
                    "credential_profile":
                        "TENANT_ADMIN",
                }
            ],
            scenario_context=context,
        )


def test_create_tampered_access_token(
    credential_profiles,
):
    auth_client = FakeAuthClient()

    executor = build_action_executor(
        auth_client=auth_client,
        credential_profiles=credential_profiles,
    )

    context = ScenarioContext()

    original_token = "header.payload.signature"

    context.set(
        "ACCESS_TOKEN",
        original_token,
    )

    executor.execute(
        steps=[
            {
                "action":
                    "create_token_fixture",
                "fixture_type":
                    "TAMPERED_ACCESS_TOKEN",
                "input": "ACCESS_TOKEN",
                "save_as":
                    "TAMPERED_ACCESS_TOKEN",
            }
        ],
        scenario_context=context,
    )

    tampered_token = context.require(
        "TAMPERED_ACCESS_TOKEN"
    )

    assert tampered_token != original_token

    assert (
        tampered_token.split(".")[:2]
        == original_token.split(".")[:2]
    )


def test_create_invalid_refresh_token(
    credential_profiles,
):
    auth_client = FakeAuthClient()

    executor = build_action_executor(
        auth_client=auth_client,
        credential_profiles=credential_profiles,
    )

    context = ScenarioContext()

    executor.execute(
        steps=[
            {
                "action":
                    "create_token_fixture",
                "fixture_type":
                    "INVALID_REFRESH_TOKEN",
                "save_as":
                    "INVALID_REFRESH_COOKIE",
            }
        ],
        scenario_context=context,
    )

    invalid_token = context.require(
        "INVALID_REFRESH_COOKIE"
    )

    assert isinstance(invalid_token, str)
    assert invalid_token.startswith("invalid-")
    assert len(invalid_token) > 30


def test_logout_uses_context_tokens(
    credential_profiles,
):
    auth_client = FakeAuthClient()

    auth_client.logout_response = FakeResponse(
        status_code=200,
        json_body={
            "success": True,
        },
    )

    executor = build_action_executor(
        auth_client=auth_client,
        credential_profiles=credential_profiles,
    )

    context = ScenarioContext()
    context.set("XSRF_TOKEN", "xsrf-value")
    context.set(
        "REFRESH_COOKIE",
        "refresh-value",
    )

    executor.execute(
        steps=[
            {
                "action": "logout",
                "context": {
                    "xsrf_token":
                        "XSRF_TOKEN",
                    "refresh_cookie":
                        "REFRESH_COOKIE",
                },
            }
        ],
        scenario_context=context,
    )

    assert auth_client.logout_calls == [
        {
            "xsrf_token": "xsrf-value",
            "refresh_token": "refresh-value",
        }
    ]


def test_unsupported_action_raises_error(
    credential_profiles,
):
    executor = build_action_executor(
        auth_client=FakeAuthClient(),
        credential_profiles=credential_profiles,
    )

    with pytest.raises(
        ActionExecutionError,
        match="database_setup",
    ):
        executor.execute(
            steps=[
                {
                    "action": "database_setup",
                }
            ],
            scenario_context=ScenarioContext(),
        )


def test_get_csrf_can_require_new_value(
    credential_profiles,
):
    auth_client = FakeAuthClient()

    auth_client.csrf_responses.append(
        FakeResponse(
            status_code=204,
            cookies={
                "XSRF-TOKEN": "new-xsrf",
            },
        )
    )

    executor = build_action_executor(
        auth_client=auth_client,
        credential_profiles=credential_profiles,
    )

    context = ScenarioContext()
    context.set(
        "XSRF_TOKEN_A",
        "old-xsrf",
    )

    executor.execute(
        steps=[
            {
                "action": "get_csrf",
                "save": {
                    "cookie.XSRF-TOKEN":
                        "XSRF_TOKEN_B",
                },
                "ensure_different_from":
                    "XSRF_TOKEN_A",
            }
        ],
        scenario_context=context,
    )

    assert (
        context["XSRF_TOKEN_B"]
        == "new-xsrf"
    )


def test_get_csrf_rejects_same_value(
    credential_profiles,
):
    auth_client = FakeAuthClient()

    auth_client.csrf_responses.append(
        FakeResponse(
            status_code=204,
            cookies={
                "XSRF-TOKEN": "same-xsrf",
            },
        )
    )

    executor = build_action_executor(
        auth_client=auth_client,
        credential_profiles=credential_profiles,
    )

    context = ScenarioContext()
    context.set(
        "XSRF_TOKEN_A",
        "same-xsrf",
    )

    with pytest.raises(
        ActionExecutionError,
        match="不应与XSRF_TOKEN_A相同",
    ):
        executor.execute(
            steps=[
                {
                    "action": "get_csrf",
                    "save": {
                        "cookie.XSRF-TOKEN":
                            "XSRF_TOKEN_B",
                    },
                    "ensure_different_from":
                        "XSRF_TOKEN_A",
                }
            ],
            scenario_context=context,
        )