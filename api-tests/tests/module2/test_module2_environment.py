from __future__ import annotations

import pytest

from common.module2_environment import (
    require_isolated_module2_environment,
    require_platform_admin_credentials,
    skip_if_static_case_needs_unavailable_teardown,
)


def approved_environment():
    return {
        "SHIPFLOW_MODULE2_RUN": "1",
        "SHIPFLOW_MODULE2_ISOLATED_ENV": "1",
        "SHIPFLOW_MODULE2_BASE_URL": "https://module2-isolated.example.test",
        "SHIPFLOW_MODULE2_BUSINESS_DB_NAME": "shipflow_http_test",
    }


def test_isolated_environment_requires_explicit_approvals_and_url():
    environment = approved_environment()
    assert require_isolated_module2_environment(environment) == "https://module2-isolated.example.test"


def test_isolated_environment_skip_names_missing_variables_without_values():
    environment = approved_environment()
    del environment["SHIPFLOW_MODULE2_BASE_URL"]
    with pytest.raises(pytest.skip.Exception) as error:
        require_isolated_module2_environment(environment)
    assert "SHIPFLOW_MODULE2_BASE_URL" in str(error.value)
    assert "platform-token" not in str(error.value)


def test_platform_credential_reports_variable_name_only():
    with pytest.raises(pytest.skip.Exception) as error:
        require_platform_admin_credentials({})
    assert "SHIPFLOW_PLATFORM_TEST_PASSWORD" in str(error.value)


@pytest.mark.parametrize(
    ("username",),
    [
        ("platform_admin",),
        ("controlled_platform_admin",),
    ],
)
def test_platform_credential_uses_only_durable_platform_login(username):
    environment = approved_environment() | {"SHIPFLOW_PLATFORM_TEST_PASSWORD": "runtime-secret"}
    environment["SHIPFLOW_MODULE2_PLATFORM_USERNAME"] = username
    actual_username, password = require_platform_admin_credentials(environment)
    assert actual_username == username
    assert password == "runtime-secret"


def test_isolated_environment_requires_fixed_business_database_name():
    environment = approved_environment()
    environment["SHIPFLOW_MODULE2_BUSINESS_DB_NAME"] = "shipflow_qa"
    with pytest.raises(pytest.skip.Exception) as error:
        require_isolated_module2_environment(environment)
    assert "SHIPFLOW_MODULE2_BUSINESS_DB_NAME" in str(error.value)


def test_mutating_static_case_is_skipped_until_teardown_contract_exists():
    with pytest.raises(pytest.skip.Exception, match="TENANT-001"):
        skip_if_static_case_needs_unavailable_teardown("TENANT-001", "create_tenant")

    skip_if_static_case_needs_unavailable_teardown("TENANT-002", "list_tenants")
