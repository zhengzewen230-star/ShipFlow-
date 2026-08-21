import os
from urllib.parse import urlparse

import pytest

from clients.auth_client import AuthClient
from clients.http_client import HttpClient
from clients.module2_client import Module2Client
from common.config_loader import settings
from common.module2_environment import (
    require_isolated_module2_environment,
    require_platform_admin_credentials,
)
from common.module2_runtime import Module2DynamicAuthenticator, Module2Runtime
from common.module2_case_executor import Module2CaseExecutor
from repositories.api_test_case_repository import ApiTestCaseRepository


@pytest.fixture(scope="module")
def module2_runtime():
    runtime = Module2Runtime()
    yield runtime
    # There is no generic delete API; failed restores remain visible for manual cleanup.
    runtime.records.clear()


@pytest.fixture(scope="module")
def module2_identity_contexts(module2_runtime):
    """Provision isolated identities at runtime; secrets remain in this process only."""
    base_url = require_isolated_module2_environment(os.environ)
    platform_username, _ = require_platform_admin_credentials(os.environ)
    module_http_client = HttpClient(
        base_url=base_url,
        timeout=settings["timeout"],
        verify_ssl=settings["verify_ssl"],
    )
    auth_http_clients = []

    def auth_client_factory():
        auth_http_client = HttpClient(
            base_url=base_url,
            timeout=settings["timeout"],
            verify_ssl=settings["verify_ssl"],
        )
        auth_http_clients.append(auth_http_client)
        return AuthClient(auth_http_client)

    authenticator = Module2DynamicAuthenticator(
        auth_client_factory=auth_client_factory,
        module_client=Module2Client(module_http_client),
        runtime=module2_runtime,
        environment=os.environ,
    )
    identities = authenticator.provision(
        platform_username=platform_username,
        platform_password_env="SHIPFLOW_PLATFORM_TEST_PASSWORD",
    )
    yield identities, module_http_client
    for client in auth_http_clients:
        client.close()
    module_http_client.close()


@pytest.fixture
def module2_client_factory(module2_identity_contexts):
    identities, http_client = module2_identity_contexts
    def create_client(identity):
        identity_context = identities.get(identity)
        if identity_context is None or not identity_context.access_token:
            raise RuntimeError(f"runtime identity is unavailable: {identity}")
        return Module2Client(
            http_client,
            access_token=identity_context.access_token,
        )
    return create_client


@pytest.fixture
def module2_case_executor(module2_identity_contexts, module2_runtime):
    """代表性与全量场景共用的真实 HTTP 执行入口。"""
    _, _http_client = module2_identity_contexts
    qa_url = os.environ["SHIPFLOW_QA_DB_URL"]
    parsed = urlparse(qa_url.removeprefix("jdbc:"))
    database = parsed.path.lstrip("/").split("?", 1)[0]
    repository = ApiTestCaseRepository(database_config={
        "host": parsed.hostname, "port": parsed.port or 3306, "database": database,
        "username": os.environ["SHIPFLOW_QA_DB_USERNAME"], "password_env": "SHIPFLOW_QA_DB_PASSWORD",
    })
    identities, http_client = module2_identity_contexts
    clients = {
        name: Module2Client(http_client, access_token=identity.access_token)
        for name, identity in identities.items()
    }
    return Module2CaseExecutor(repository=repository, clients=clients, http_client=http_client, runtime=module2_runtime)
