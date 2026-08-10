from __future__ import annotations

from dataclasses import dataclass

import pytest

from common.module2_runtime import (
    CredentialProfile,
    IdentityContext,
    Module2RunContext,
    Module2ResourceContext,
    Module2Runtime,
    Module2DynamicAuthenticator,
    Module2SetupActions,
    assert_error,
    assert_cross_tenant_not_found,
    assert_forbidden,
    assert_idempotency_replay,
    assert_no_version_change,
    assert_live_rejection,
    assert_version_conflict,
    assert_version_increment,
    extract_resource_id_version,
    poll_until,
    run_concurrently,
    runtime_idempotency_key,
    runtime_request_id,
    runtime_uuid,
)


@dataclass
class FakeResponse:
    status_code: int
    body: dict
    cookies: dict | None = None

    def json(self):
        return self.body


class FakeAuthClient:
    def __init__(self):
        self.calls = []

    def get_csrf_token(self):
        return FakeResponse(204, {}, {"XSRF-TOKEN": "xsrf"})

    def login(self, **kwargs):
        self.calls.append(kwargs)
        return FakeResponse(
            200,
            {"data": {"accessToken": "access"}},
            {"REFRESH_TOKEN": "refresh"},
        )

    def refresh(self, xsrf_token, refresh_token):
        self.calls.append({"refresh": True, "xsrf": xsrf_token, "refresh_cookie": refresh_token})
        return FakeResponse(200, {"data": {"accessToken": "refreshed-access"}}, {"REFRESH_TOKEN": "refreshed-cookie"})


class FakeModuleClient:
    def __init__(self):
        self.access_token = None
        self.calls = []

    def create_tenant(self, body, key):
        self.calls.append(("tenant", body, key))
        return FakeResponse(201, {"data": {"id": "tenant-1", "version": 1}})

    def create_store(self, body, key):
        self.calls.append(("store", body, key))
        return FakeResponse(201, {"data": {"id": "store-1", "version": 1}})

    def create_user(self, body, key):
        self.calls.append(("user", body, key))
        return FakeResponse(201, {"data": {"id": "user-1", "version": 1}})

    def get_role(self, role_id):
        return FakeResponse(
            200,
            {"data": {"id": role_id, "version": 2, "permissionIds": ["permission-1"]}},
        )

    def list_roles(self, **params):
        return FakeResponse(200, {"data": [{"id": "role-1", "version": 1, "permissionIds": []}]})

    def get_user(self, user_id):
        return FakeResponse(200, {"data": {"id": user_id, "roleIds": ["role-1"]}})

    def replace_user_roles(self, user_id, role_ids, version):
        self.calls.append(("restore-user-roles", user_id, role_ids, version))
        return FakeResponse(200, {"data": {"id": user_id, "version": version + 1}})

    def replace_role_permissions(self, role_id, permission_ids, version):
        self.calls.append(("restore-role-permissions", role_id, permission_ids, version))
        return FakeResponse(200, {"data": {"id": role_id, "version": version + 1}})

    def list_permissions(self):
        return FakeResponse(200, {"data": [{"id": "permission-1"}, {"id": "permission-2"}]})

    def change_store_status(self, resource_id, status, version):
        self.calls.append(("restore-store", resource_id, status, version))
        return FakeResponse(200, {"data": {"id": resource_id, "version": version + 1}})


def test_runtime_context_keeps_dynamic_ids_version_and_key():
    context = Module2ResourceContext(idempotency_key=runtime_idempotency_key("USER-001"))
    context.save_resource("tenant", "tenant-1", 1)
    context.save_resource("store", "store-1", 2)
    context.save_resource("user", "user-1", 3)
    context.save_resource("role", "role-1", 4)

    assert context.tenant_id == "tenant-1"
    assert context.store_id == "store-1"
    assert context.user_id == "user-1"
    assert context.role_id == "role-1"
    assert context.version == 4
    assert context.require_id("user") == "user-1"


def test_setup_login_and_create_actions_use_runtime_values_without_logging_secrets():
    auth = FakeAuthClient()
    client = FakeModuleClient()
    runtime = Module2Runtime()
    setup = Module2SetupActions(
        auth_client=auth,
        module_client=client,
        runtime=runtime,
        environment={"PLATFORM_PASSWORD": "secret-in-memory"},
    )

    identity = setup.login(
        CredentialProfile("platform-admin", "platform", None, "PLATFORM_PASSWORD")
    )
    setup.use_identity("platform-admin")
    tenant = setup.create_tenant(case_no="TENANT-001")
    store = setup.create_store(case_no="STORE-001")
    user = setup.create_user(case_no="USER-001", temporary_password="runtime-only", role_ids=["role-1"])
    role = setup.query_role(role_id="role-1")

    assert identity.authenticated
    assert client.access_token == "access"
    assert tenant.tenant_id == "tenant-1"
    assert store.store_id == "store-1"
    assert user.user_id == "user-1"
    assert role.role_id == "role-1"
    assert "secret-in-memory" not in repr(identity)
    assert all("secret-in-memory" not in repr(record) for record in runtime.records)


def test_missing_password_reports_only_environment_name():
    setup = Module2SetupActions(
        auth_client=FakeAuthClient(),
        module_client=FakeModuleClient(),
        runtime=Module2Runtime(),
        environment={},
    )
    with pytest.raises(RuntimeError, match="PLATFORM_PASSWORD") as error:
        setup.login(CredentialProfile("platform-admin", "platform", None, "PLATFORM_PASSWORD"))
    assert "secret" not in str(error.value)


def test_teardown_records_manual_limitations_and_runs_only_explicit_restore():
    runtime = Module2Runtime()
    client = FakeModuleClient()
    runtime.register("tenant", "tenant-1", cleanup_description="manual tenant cleanup")
    runtime.register_status_restore(
        resource_type="store",
        resource_id="store-1",
        client=client,
        status="ACTIVE",
        version=3,
    )

    result = runtime.cleanup()

    assert result.completed == 1
    assert result.limitations == ["manual tenant cleanup"]
    assert runtime.ids() == []
    assert client.calls == [("restore-store", "store-1", "ACTIVE", 3)]


def test_assertions_cover_version_conflict_and_live_rejection():
    assert_version_increment(2, 3)
    with pytest.raises(AssertionError):
        assert_version_increment(2, 4)

    conflict = FakeResponse(409, {"error": {"code": "COMMON-1005"}})
    assert_version_conflict(conflict)
    rejected = FakeResponse(401, {"error": {"code": "COMMON-1002"}})
    assert_live_rejection(rejected)
    assert_error(conflict, 409, "COMMON-1005")
    assert_forbidden(FakeResponse(403, {"error": {"code": "COMMON-1004"}}))
    assert_cross_tenant_not_found(FakeResponse(404, {"error": {"code": "COMMON-1006"}}))
    assert_no_version_change(3, 3)
    assert_idempotency_replay(
        FakeResponse(201, {"data": {"id": "tenant-1", "version": 1}}),
        FakeResponse(201, {"data": {"id": "tenant-1", "version": 1}}),
    )


def test_polling_uses_injected_clock_without_fixed_sleep():
    clock_values = iter([0.0, 0.0, 0.2, 0.4])
    observed = []

    def clock():
        return next(clock_values)

    def sleep(seconds):
        observed.append(seconds)

    calls = iter([False, False, {"state": "READY"}])
    assert poll_until(lambda: next(calls), timeout_seconds=1, clock=clock, sleep=sleep) == {"state": "READY"}
    assert observed


def test_concurrency_helper_runs_injected_action():
    assert sorted(run_concurrently(lambda value: value * 2, [1, 2, 3])) == [2, 4, 6]


def test_response_id_and_version_extraction_supports_detail_and_list():
    detail = FakeResponse(200, {"data": {"id": "store-1", "version": 7}})
    assert extract_resource_id_version(detail) == ("store-1", 7)
    listing = FakeResponse(200, {"data": [{"id": "role-1", "version": 4}]})
    assert extract_resource_id_version(listing) == ("role-1", 4)


def test_runtime_identifiers_reject_unsafe_prefixes():
    with pytest.raises(ValueError):
        runtime_uuid("tenant-")
    with pytest.raises(ValueError):
        runtime_idempotency_key("USER 001")
    assert runtime_request_id("USER-001").startswith("M2-USER-001-")


def test_run_context_tracks_dynamic_identities_resource_aliases_and_unique_names():
    identities = {
        "platform_admin": IdentityContext("platform_admin", None, "platform_admin", access_token="runtime"),
        "tenant_admin_a": IdentityContext("tenant_admin_a", "tenant-a", "admin-a", access_token="runtime"),
        "tenant_admin_b": IdentityContext("tenant_admin_b", "tenant-b", "admin-b", access_token="runtime"),
        "no_permission": IdentityContext("no_permission", "tenant-a", "limited", access_token="runtime"),
    }
    context = Module2RunContext()
    for identity in identities.values():
        context.add_identity(identity)
    tenant = Module2ResourceContext()
    tenant.save_resource("tenant", "tenant-a", 1)
    context.add_resource("TENANT_A", tenant)

    assert set(context.identities) == {"platform_admin", "tenant_admin_a", "tenant_admin_b", "no_permission"}
    assert context.require_resource("TENANT_A").tenant_id == "tenant-a"
    assert context.unique_name("store", "a").startswith("m2_")
    assert "tenant-a" not in repr(context.require_identity("platform_admin")), "身份上下文展示不得包含访问令牌"


def test_dynamic_authentication_creates_isolated_sessions_refreshes_and_keeps_secrets_out_of_environment():
    runtime = Module2Runtime()
    module_client = FakeModuleClient()
    auth_clients = []

    def auth_client_factory():
        client = FakeAuthClient()
        auth_clients.append(client)
        return client

    environment = {"SHIPFLOW_PLATFORM_TEST_PASSWORD": "platform-password"}
    identities = Module2DynamicAuthenticator(
        auth_client_factory=auth_client_factory,
        module_client=module_client,
        runtime=runtime,
        environment=environment,
    ).provision(platform_username="platform_admin", platform_password_env="SHIPFLOW_PLATFORM_TEST_PASSWORD")

    assert set(identities) == {"platform_admin", "tenant_admin_a", "tenant_admin_b", "no_permission"}
    assert len({id(identity.auth_client) for identity in identities.values()}) == 4
    assert not any(name.startswith("_MODULE2_RUNTIME_") for name in environment)
    assert all("platform-password" not in repr(identity) for identity in identities.values())
    previous_cookie = identities["tenant_admin_a"].refresh_cookie
    refreshed = Module2SetupActions(
        auth_client=auth_clients[0], module_client=module_client, runtime=runtime, environment=environment
    ).refresh_identity("tenant_admin_a")
    assert refreshed.access_token == "refreshed-access"
    assert refreshed.refresh_cookie != previous_cookie


def test_setup_snapshots_bindings_and_teardown_restores_them():
    runtime = Module2Runtime()
    client = FakeModuleClient()
    setup = Module2SetupActions(auth_client=FakeAuthClient(), module_client=client, runtime=runtime)

    assert setup.snapshot_role_permissions("role-1") == ("permission-1",)
    assert setup.snapshot_user_roles("user-1") == ("role-1",)
    assert setup.capture_permissions() == ("permission-1", "permission-2")

    runtime.register_user_role_restore(
        user_id="user-1",
        role_ids=("role-1",),
        client=client,
        version=lambda: 3,
    )
    runtime.register_role_permission_restore(
        role_id="role-1",
        permission_ids=("permission-1",),
        client=client,
        version=lambda: 4,
    )

    result = runtime.cleanup()

    assert result.completed == 2
    assert ("restore-role-permissions", "role-1", ["permission-1"], 4) in client.calls
    assert ("restore-user-roles", "user-1", ["role-1"], 3) in client.calls
