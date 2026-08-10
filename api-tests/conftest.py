import os

import pytest

from clients.auth_client import AuthClient
from clients.http_client import HttpClient
from clients.user_client import UserClient
from common.config_loader import settings
from common.scenario_context import ScenarioContext
from repositories.api_test_case_repository import ApiTestCaseRepository
import secrets

from common.json_path import JsonPathResolver
from common.placeholder_resolver import PlaceholderResolver
from executors.action_executor import ActionExecutor
from executors.assertion_executor import AssertionExecutor
from executors.extractor_executor import ExtractorExecutor
from executors.request_executor import RequestExecutor
from executors.scenario_executor import ScenarioExecutor
from common.environment import (
    missing_environment_variables,
    skip_if_missing_environment,
)

@pytest.fixture
def http_client():
    clients=HttpClient(
        base_url=settings["base_url"],
        timeout=settings["timeout"],
        verify_ssl=settings["verify_ssl"],
    )
    yield clients
    clients.close()

@pytest.fixture
def auth_client(http_client):
    return AuthClient(http_client)

@pytest.fixture
def user_client(http_client):
    return UserClient(http_client)

@pytest.fixture
def token_context():
    context = ScenarioContext()
    yield context
    context.clear()

@pytest.fixture
def authenticated_context(auth_client,token_context):
    password_env = settings["auth"]["password_env"]
    skip_if_missing_environment(
        [password_env],
        reason="authentication environment is not configured",
    )
    password = os.getenv(password_env)
    csrf_response=auth_client.get_csrf_token()
    if csrf_response.status_code != 204:
        pytest.fail("CSRF not valid"
        f"状态码：{csrf_response.status_code}")
    xsrf_token=csrf_response.cookies.get("XSRF-TOKEN")
    if not xsrf_token:
        pytest.fail("XSRF-TOKEN not set")

    login_response = auth_client.login(tenant_code=settings["auth"]["tenant_code"],
                                       username=settings["auth"]["username"],
                                       password=password,
                                       xsrf_token=xsrf_token)
    assert login_response.status_code == 200
    login_data = login_response.json()

    refresh_token = login_response.cookies.get("REFRESH_TOKEN")

    if not refresh_token:
        pytest.fail("Refresh-Token not set")
    token_context.xsrf_token=xsrf_token
    token_context.access_token=login_data["data"]["accessToken"]
    token_context.refresh_token=refresh_token
    return token_context


@pytest.fixture(scope="session")
def api_test_case_repository():
    if os.getenv("SHIPFLOW_RUN_QA_READONLY") != "1":
        pytest.skip("Set SHIPFLOW_RUN_QA_READONLY=1 to run QA repository checks")
    skip_if_missing_environment(
        [settings["qa_database"]["password_env"]],
        reason="QA database environment is not configured",
    )
    database_config=settings["qa_database"]
    return ApiTestCaseRepository(database_config=database_config)


#--------------------------
def required_environment_variable(
    variable_name: str,
) -> str:
    value = os.getenv(variable_name)

    if not value:
        pytest.fail(
            f"缺少环境变量：{variable_name}"
        )

    return value


@pytest.fixture(scope="session")
def json_path_resolver():
    return JsonPathResolver()


@pytest.fixture(scope="session")
def placeholder_resolver():
    return PlaceholderResolver()


@pytest.fixture
def extractor_executor(
    json_path_resolver,
):
    return ExtractorExecutor(
        json_path_resolver=json_path_resolver
    )


@pytest.fixture
def request_executor(
    http_client,
    placeholder_resolver,
):
    return RequestExecutor(
        http_client=http_client,
        placeholder_resolver=placeholder_resolver,
    )


@pytest.fixture
def assertion_executor(
    json_path_resolver,
    placeholder_resolver,
):
    return AssertionExecutor(
        json_path_resolver=json_path_resolver,
        placeholder_resolver=placeholder_resolver,
    )


@pytest.fixture
def action_executor(
    auth_client,
    extractor_executor,
):
    return ActionExecutor(
        auth_client=auth_client,
        extractor_executor=extractor_executor,
        credential_profiles=settings[
            "credential_profiles"
        ],
    )


@pytest.fixture
def scenario_executor(
    action_executor,
    request_executor,
    extractor_executor,
    assertion_executor,
):
    return ScenarioExecutor(
        action_executor=action_executor,
        request_executor=request_executor,
        extractor_executor=extractor_executor,
        assertion_executor=assertion_executor,
    )


@pytest.fixture
def database_scenario_context():
    profiles = settings[
        "credential_profiles"
    ]

    tenant_profile = profiles["TENANT_ADMIN"]
    platform_profile = profiles["PLATFORM_ADMIN"]

    skip_if_missing_environment(
        [
            tenant_profile["password_env"],
            platform_profile["password_env"],
        ],
        reason="authentication environment is not configured",
    )

    tenant_password = (
        required_environment_variable(
            tenant_profile["password_env"]
        )
    )

    platform_password = (
        required_environment_variable(
            platform_profile["password_env"]
        )
    )

    # 现有数据库契约中平台和租户登录
    # 都使用 ${VALID_PASSWORD}。
    if tenant_password != platform_password:
        pytest.fail(
            "当前数据库测试契约要求"
            "租户管理员和平台管理员"
            "使用相同的本地测试密码"
        )

    unique_suffix = secrets.token_hex(8)

    context = ScenarioContext()

    context.update_variables(
        {
            "TENANT_CODE_A":
                tenant_profile["tenant_code"],

            "TENANT_ADMIN_USERNAME":
                tenant_profile["username"],

            "PLATFORM_ADMIN_USERNAME":
                platform_profile["username"],

            "VALID_PASSWORD":
                tenant_password,

            "INVALID_PASSWORD":
                "invalid-" + secrets.token_urlsafe(24),

            "WRONG_TENANT_CODE":
                "UNKNOWN_TENANT_" + unique_suffix,

            "WRONG_USERNAME":
                "unknown_user_" + unique_suffix,
        }
    )

    yield context

    context.clear()


def pytest_generate_tests(metafunc):
    parameter_name = "database_api_test_case"

    if parameter_name not in metafunc.fixturenames:
        return

    if os.getenv("SHIPFLOW_RUN_QA_READONLY") != "1":
        metafunc.parametrize(
            parameter_name,
            [pytest.param(
                None,
                marks=pytest.mark.skip(
                    reason="QA repository execution is disabled by default",
                ),
            )],
        )
        return

    qa_config = settings["qa_database"]
    required_names = [qa_config["password_env"]]
    missing = missing_environment_variables(required_names)
    if missing:
        metafunc.parametrize(
            parameter_name,
            [pytest.param(
                None,
                marks=pytest.mark.skip(
                    reason=(
                        "QA database environment is not configured: "
                        + ", ".join(missing)
                    )
                ),
            )],
        )
        return

    execution_config = settings[
        "test_execution"
    ]

    repository = ApiTestCaseRepository(
        database_config=qa_config
    )

    test_cases = repository.find_ready_cases(
        environment_scope=execution_config[
            "environment_scope"
        ],
        module=execution_config.get("module"),
    )

    if not test_cases:
        raise pytest.UsageError(
            "没有查询到可执行的READY测试用例"
        )

    metafunc.parametrize(
        parameter_name,
        test_cases,
        ids=[
            test_case.case_no
            for test_case in test_cases
        ],
    )
