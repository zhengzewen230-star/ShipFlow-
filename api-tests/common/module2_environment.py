from __future__ import annotations

from collections.abc import Mapping
from urllib.parse import urlparse

import pytest


STATIC_MODULE2_ENVIRONMENT = (
    "SHIPFLOW_MODULE2_RUN",
    "SHIPFLOW_MODULE2_ISOLATED_ENV",
    "SHIPFLOW_MODULE2_BASE_URL",
    "SHIPFLOW_MODULE2_BUSINESS_DB_NAME",
)

MODULE2_BUSINESS_DATABASE = "shipflow_http_test"

MUTATING_MODULE2_METHODS = {
    "create_tenant",
    "update_tenant",
    "change_tenant_status",
    "create_store",
    "update_store",
    "change_store_status",
    "create_user",
    "update_user",
    "change_user_status",
    "replace_user_roles",
    "replace_role_permissions",
}


def require_isolated_module2_environment(environment: Mapping[str, str]) -> str:
    """Return an explicitly approved isolated backend URL, or skip without secrets."""
    missing = [name for name in STATIC_MODULE2_ENVIRONMENT if not environment.get(name)]
    if missing:
        pytest.skip("module 2 isolated environment is not configured: " + ", ".join(missing))

    required_values = {
        "SHIPFLOW_MODULE2_RUN": "1",
        "SHIPFLOW_MODULE2_ISOLATED_ENV": "1",
    }
    invalid = [name for name, expected in required_values.items() if environment.get(name) != expected]
    if invalid:
        pytest.skip("module 2 isolated environment is not explicitly approved: " + ", ".join(invalid))

    base_url = environment["SHIPFLOW_MODULE2_BASE_URL"]
    parsed = urlparse(base_url)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        pytest.skip("SHIPFLOW_MODULE2_BASE_URL must be an absolute HTTP(S) URL")

    if environment.get("SHIPFLOW_MODULE2_BUSINESS_DB_NAME") != MODULE2_BUSINESS_DATABASE:
        pytest.skip(
            "module 2 business database is not explicitly approved: "
            "SHIPFLOW_MODULE2_BUSINESS_DB_NAME"
        )
    return base_url.rstrip("/")


def skip_if_static_case_needs_unavailable_teardown(case_no: str, method: str) -> None:
    if method in MUTATING_MODULE2_METHODS:
        pytest.skip(
            f"{case_no} requires a per-case API teardown and isolated-resource reset; "
            "the cleanup contract is not available"
        )


def require_platform_admin_credentials(environment: Mapping[str, str]) -> tuple[str, str]:
    """Return only the durable platform-admin login inputs, never a token."""
    password_name = "SHIPFLOW_PLATFORM_TEST_PASSWORD"
    password = environment.get(password_name)
    if not password:
        pytest.skip(f"module 2 platform credential is not configured: {password_name}")
    return environment.get("SHIPFLOW_MODULE2_PLATFORM_USERNAME", "platform_admin"), password
