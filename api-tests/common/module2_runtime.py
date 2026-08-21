from __future__ import annotations

import os
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from typing import Any, Callable, Mapping, Sequence

import pytest


def runtime_uuid(prefix: str) -> str:
    """Create a per-run identifier that is safe to use as test data."""
    if not prefix or not prefix.replace("_", "").isalnum():
        raise ValueError("prefix must contain only letters, numbers, or underscores")
    return f"{prefix}_{uuid.uuid4().hex}"


def runtime_idempotency_key(case_no: str) -> str:
    if not case_no or not case_no.replace("-", "").isalnum():
        raise ValueError("case_no must contain only letters, numbers, or hyphens")
    return f"HTTP-ACCEPT-{case_no}-{uuid.uuid4()}"


def runtime_request_id(case_no: str) -> str:
    if not case_no or not case_no.replace("-", "").isalnum():
        raise ValueError("case_no must contain only letters, numbers, or hyphens")
    return f"M2-{case_no}-{uuid.uuid4()}"


def required_runtime_value(name: str) -> str:
    value = os.getenv(name)
    if not value:
        pytest.skip(f"{name} is not configured")
    return value


@dataclass(frozen=True)
class CredentialProfile:
    name: str
    username: str
    tenant_code: str | None
    password_env: str
    password: str | None = field(default=None, repr=False, compare=False)


@dataclass
class IdentityContext:
    name: str
    tenant_code: str | None
    username: str
    access_token: str | None = field(default=None, repr=False)
    refresh_cookie: str | None = field(default=None, repr=False)
    xsrf_token: str | None = field(default=None, repr=False)
    auth_client: Any | None = field(default=None, repr=False, compare=False)

    def __repr__(self) -> str:
        return (
            "IdentityContext("
            f"name={self.name!r}, tenant_code={self.tenant_code!r}, "
            f"username={self.username!r}, authenticated={self.authenticated}"
            ")"
        )

    @property
    def authenticated(self) -> bool:
        return bool(self.access_token)

    @property
    def csrf_ready(self) -> bool:
        return bool(self.xsrf_token)


@dataclass
class ResourceRecord:
    resource_type: str
    resource_id: str
    initial_version: int | None = None
    restore: dict[str, Any] | None = None
    cleanup_action: Callable[[], Any] | None = field(default=None, repr=False)
    cleanup_description: str | None = None


@dataclass
class Module2ResourceContext:
    """IDs and request state owned by one dynamic scenario."""

    tenant_id: str | None = None
    store_id: str | None = None
    user_id: str | None = None
    role_id: str | None = None
    version: int | None = None
    idempotency_key: str | None = None

    def save_resource(self, resource_type: str, resource_id: Any, version: Any = None) -> None:
        field_name = {
            "tenant": "tenant_id",
            "store": "store_id",
            "user": "user_id",
            "role": "role_id",
        }.get(resource_type)
        if field_name is None:
            raise ValueError(f"unsupported resource type: {resource_type}")
        setattr(self, field_name, str(resource_id))
        if version is not None:
            self.version = int(version)

    def require_id(self, resource_type: str) -> str:
        field_name = {
            "tenant": "tenant_id",
            "store": "store_id",
            "user": "user_id",
            "role": "role_id",
        }.get(resource_type)
        if field_name is None:
            raise ValueError(f"unsupported resource type: {resource_type}")
        value = getattr(self, field_name)
        if not value:
            raise RuntimeError(f"missing runtime resource id: {resource_type}")
        return value


@dataclass
class Module2RunContext:
    """Per-run resource graph. It contains aliases and IDs, never secret values."""

    run_id: str = field(default_factory=lambda: runtime_uuid("m2"))
    identities: dict[str, IdentityContext] = field(default_factory=dict, repr=False)
    resources: dict[str, Module2ResourceContext] = field(default_factory=dict)
    role_permission_snapshots: dict[str, tuple[str, ...]] = field(default_factory=dict)
    user_role_snapshots: dict[str, tuple[str, ...]] = field(default_factory=dict)

    def unique_name(self, kind: str, suffix: str) -> str:
        if not kind or not suffix or not kind.replace("_", "").isalnum() or not suffix.replace("_", "").isalnum():
            raise ValueError("kind and suffix must contain only letters, numbers, or underscores")
        return f"{self.run_id}_{kind}_{suffix}"

    def add_identity(self, identity: IdentityContext) -> None:
        self.identities[identity.name] = identity

    def require_identity(self, name: str) -> IdentityContext:
        try:
            return self.identities[name]
        except KeyError as exc:
            raise RuntimeError(f"missing runtime identity: {name}") from exc

    def add_resource(self, alias: str, context: Module2ResourceContext) -> None:
        if not alias.replace("_", "").isalnum():
            raise ValueError("resource alias must contain only letters, numbers, or underscores")
        self.resources[alias] = context

    def require_resource(self, alias: str) -> Module2ResourceContext:
        try:
            return self.resources[alias]
        except KeyError as exc:
            raise RuntimeError(f"missing runtime resource: {alias}") from exc


@dataclass
class TeardownResult:
    attempted: int = 0
    completed: int = 0
    failures: list[str] = field(default_factory=list)
    limitations: list[str] = field(default_factory=list)


@dataclass
class Module2Runtime:
    """Tracks only resources and identities created by the current test run."""

    records: list[ResourceRecord] = field(default_factory=list)
    cleanup_failures: list[str] = field(default_factory=list)
    cleanup_limitations: list[str] = field(default_factory=list)
    identities: dict[str, IdentityContext] = field(default_factory=dict)
    active_identity_name: str | None = None
    run_context: Module2RunContext = field(default_factory=Module2RunContext)

    @property
    def enabled(self) -> bool:
        return os.getenv("SHIPFLOW_MODULE2_RUN") == "1"

    def register(
        self,
        resource_type: str,
        resource_id: str,
        *,
        version: int | None = None,
        restore: dict[str, Any] | None = None,
        cleanup_action: Callable[[], Any] | None = None,
        cleanup_description: str | None = None,
    ) -> None:
        self.records.append(
            ResourceRecord(
                resource_type=resource_type,
                resource_id=str(resource_id),
                initial_version=version,
                restore=restore,
                cleanup_action=cleanup_action,
                cleanup_description=cleanup_description,
            )
        )

    def ids(self, resource_type: str | None = None) -> list[str]:
        return [
            record.resource_id
            for record in self.records
            if resource_type is None or record.resource_type == resource_type
        ]

    def add_identity(self, identity: IdentityContext) -> None:
        self.identities[identity.name] = identity
        self.run_context.add_identity(identity)
        if self.active_identity_name is None:
            self.active_identity_name = identity.name

    def identity(self, name: str) -> IdentityContext:
        try:
            return self.identities[name]
        except KeyError as exc:
            raise RuntimeError(f"unknown runtime identity: {name}") from exc

    def activate_identity(self, name: str) -> IdentityContext:
        identity = self.identity(name)
        self.active_identity_name = name
        return identity

    @property
    def active_identity(self) -> IdentityContext:
        if self.active_identity_name is None:
            raise RuntimeError("no active runtime identity")
        return self.identity(self.active_identity_name)

    def cleanup(self, client: Any = None) -> TeardownResult:
        """Run explicit API cleanup actions only; never falls back to SQL or delete guesses."""
        result = TeardownResult()
        for record in reversed(self.records):
            result.attempted += 1
            try:
                if record.cleanup_action is not None:
                    record.cleanup_action()
                    result.completed += 1
                    continue

                # Kept for compatibility with the original fake-client unit test. A
                # concrete client method must be supplied; no generic delete is inferred.
                if record.restore is not None and client is not None:
                    restore_method = getattr(client, "restore", None)
                    if callable(restore_method):
                        response = restore_method(record, record.restore)
                        if getattr(response, "status_code", 500) >= 300:
                            failure = (
                                f"{record.resource_type}:{record.resource_id}:"
                                f"{getattr(response, 'status_code', 'unknown')}"
                            )
                            result.failures.append(failure)
                            continue
                        result.completed += 1
                        continue

                limitation = record.cleanup_description or (
                    f"{record.resource_type}:{record.resource_id}: "
                    "no documented API cleanup action"
                )
                result.limitations.append(limitation)
            except Exception as exc:  # cleanup must not hide the primary assertion
                result.failures.append(
                    f"{record.resource_type}:{record.resource_id}:{type(exc).__name__}"
                )

        self.cleanup_failures.extend(result.failures)
        self.cleanup_limitations.extend(result.limitations)
        self.records.clear()
        return result

    def register_status_restore(
        self,
        *,
        resource_type: str,
        resource_id: str,
        client: Any,
        status: str,
        version: int | Callable[[], int],
    ) -> None:
        """Register a documented status API restore; never infer deletion."""
        method_name = {
            "tenant": "change_tenant_status",
            "store": "change_store_status",
            "user": "change_user_status",
        }.get(resource_type)
        if method_name is None:
            raise ValueError(f"status restore is unsupported for {resource_type}")
        method = getattr(client, method_name, None)
        if not callable(method):
            raise ValueError(f"client does not expose {method_name}")

        def restore_status() -> Any:
            current_version = version() if callable(version) else version
            response = method(resource_id, status, current_version)
            if getattr(response, "status_code", 500) >= 300:
                raise RuntimeError(
                    f"status restore failed for {resource_type}:{resource_id}"
                )
            return response

        self.register(
            resource_type,
            resource_id,
            version=version,
            cleanup_action=restore_status,
            cleanup_description=f"restore {resource_type} status through documented API",
        )

    def register_user_role_restore(
        self,
        *,
        user_id: str,
        role_ids: Sequence[str],
        client: Any,
        version: int | Callable[[], int],
    ) -> None:
        """Restore a captured user-role binding through the documented API only."""
        def restore_roles() -> Any:
            current_version = version() if callable(version) else version
            response = client.replace_user_roles(user_id, list(role_ids), current_version)
            if getattr(response, "status_code", 500) >= 300:
                raise RuntimeError(f"user role restore failed for user:{user_id}")
            return response

        self.register(
            "user",
            user_id,
            cleanup_action=restore_roles,
            cleanup_description="restore user-role binding through documented API",
        )

    def register_role_permission_restore(
        self,
        *,
        role_id: str,
        permission_ids: Sequence[str],
        client: Any,
        version: int | Callable[[], int],
    ) -> None:
        """Restore a captured role-permission binding through the documented API only."""
        def restore_permissions() -> Any:
            current_version = version() if callable(version) else version
            response = client.replace_role_permissions(role_id, list(permission_ids), current_version)
            if getattr(response, "status_code", 500) >= 300:
                raise RuntimeError(f"role permission restore failed for role:{role_id}")
            return response

        self.register(
            "role",
            role_id,
            cleanup_action=restore_permissions,
            cleanup_description="restore role-permission binding through documented API",
        )


class Module2SetupActions:
    """API-only setup helpers for live module 2 scenarios."""

    def __init__(
        self,
        *,
        auth_client: Any,
        module_client: Any,
        runtime: Module2Runtime,
        environment: Mapping[str, str] | None = None,
    ):
        self.auth_client = auth_client
        self.module_client = module_client
        self.runtime = runtime
        self.environment = environment if environment is not None else os.environ

    def login(self, profile: CredentialProfile, *, auth_client: Any | None = None) -> IdentityContext:
        password = profile.password or self.environment.get(profile.password_env)
        if not password:
            raise RuntimeError(f"missing authentication environment variable: {profile.password_env}")

        active_auth_client = auth_client or self.auth_client
        csrf_response = active_auth_client.get_csrf_token()
        if getattr(csrf_response, "status_code", None) != 204:
            raise RuntimeError("CSRF setup failed")
        xsrf_token = getattr(csrf_response, "cookies", {}).get("XSRF-TOKEN")
        if not xsrf_token:
            raise RuntimeError("CSRF response did not provide XSRF-TOKEN")

        response = active_auth_client.login(
            tenant_code=profile.tenant_code,
            username=profile.username,
            password=password,
            xsrf_token=xsrf_token,
        )
        if getattr(response, "status_code", None) != 200:
            raise RuntimeError("login failed")
        payload = response.json()
        access_token = _required_mapping_value(payload, ("data", "accessToken"))
        refresh_cookie = getattr(response, "cookies", {}).get("REFRESH_TOKEN")
        if not refresh_cookie:
            raise RuntimeError("login response did not provide REFRESH_TOKEN")

        identity = IdentityContext(
            name=profile.name,
            tenant_code=profile.tenant_code,
            username=profile.username,
            access_token=str(access_token),
            refresh_cookie=str(refresh_cookie),
            xsrf_token=str(xsrf_token),
            auth_client=active_auth_client,
        )
        self.runtime.add_identity(identity)
        return identity

    def refresh_identity(self, identity_name: str) -> IdentityContext:
        """Refresh one identity through its own in-memory CSRF/refresh session."""
        identity = self.runtime.identity(identity_name)
        if not identity.auth_client or not identity.xsrf_token or not identity.refresh_cookie:
            raise RuntimeError(f"runtime identity has no refresh session: {identity_name}")
        response = identity.auth_client.refresh(identity.xsrf_token, identity.refresh_cookie)
        _assert_status(response, 200, "refresh identity")
        access_token = _required_mapping_value(response.json(), ("data", "accessToken"))
        refresh_cookie = getattr(response, "cookies", {}).get("REFRESH_TOKEN")
        if not refresh_cookie:
            raise RuntimeError("refresh response did not provide REFRESH_TOKEN")
        identity.access_token = str(access_token)
        identity.refresh_cookie = str(refresh_cookie)
        return identity

    def use_identity(self, identity_name: str) -> Any:
        identity = self.runtime.activate_identity(identity_name)
        if not identity.access_token:
            raise RuntimeError(f"runtime identity is not authenticated: {identity_name}")
        self.module_client.access_token = identity.access_token
        return identity

    def create_tenant_pair(
        self,
        *,
        tenant_a_body: Mapping[str, Any],
        tenant_b_body: Mapping[str, Any],
    ) -> dict[str, Module2ResourceContext]:
        """Create the two isolated tenants and record their independent contexts."""
        self.use_identity("platform_admin")
        tenant_a = self.create_tenant(case_no="TENANT-001", body=tenant_a_body)
        tenant_b = self.create_tenant(case_no="TENANT-001", body=tenant_b_body)
        self.runtime.run_context.add_resource("TENANT_A", tenant_a)
        self.runtime.run_context.add_resource("TENANT_B", tenant_b)
        return {"TENANT_A": tenant_a, "TENANT_B": tenant_b}

    def create_tenant(self, *, case_no: str, body: Mapping[str, Any] | None = None) -> Module2ResourceContext:
        context = Module2ResourceContext(idempotency_key=runtime_idempotency_key(case_no))
        request_body = dict(body or {})
        request_body.setdefault("tenantCode", runtime_uuid("tenant"))
        request_body.setdefault("tenantName", runtime_uuid("tenant_name"))
        request_body.setdefault("initialAdmin", {})
        response = self.module_client.create_tenant(request_body, context.idempotency_key)
        _assert_status(response, 201, "create tenant")
        resource_id, version = extract_resource_id_version(response)
        context.save_resource("tenant", resource_id, version)
        self.runtime.register(
            "tenant",
            resource_id,
            version=version,
            cleanup_description=(
                f"tenant:{resource_id}: no documented tenant delete endpoint; "
                "manual controlled cleanup is required"
            ),
        )
        return context

    def create_store(self, *, case_no: str, body: Mapping[str, Any] | None = None) -> Module2ResourceContext:
        context = Module2ResourceContext(idempotency_key=runtime_idempotency_key(case_no))
        request_body = dict(body or {})
        request_body.setdefault("storeCode", runtime_uuid("store"))
        request_body.setdefault("storeName", runtime_uuid("store_name"))
        request_body.setdefault("platformCode", "RUNTIME_PLACEHOLDER")
        request_body.setdefault("platformAccount", runtime_uuid("platform_account"))
        response = self.module_client.create_store(request_body, context.idempotency_key)
        _assert_status(response, 201, "create store")
        resource_id, version = extract_resource_id_version(response)
        context.save_resource("store", resource_id, version)
        self.runtime.register(
            "store",
            resource_id,
            version=version,
            cleanup_description=(
                f"store:{resource_id}: no documented store delete endpoint; "
                "manual controlled cleanup is required"
            ),
        )
        return context

    def create_user(
        self,
        *,
        case_no: str,
        temporary_password: str,
        role_ids: Sequence[str],
        body: Mapping[str, Any] | None = None,
    ) -> Module2ResourceContext:
        if not temporary_password:
            raise ValueError("temporary_password is required in memory for API setup")
        context = Module2ResourceContext(idempotency_key=runtime_idempotency_key(case_no))
        request_body = dict(body or {})
        request_body.setdefault("username", runtime_uuid("user"))
        request_body.setdefault("displayName", runtime_uuid("user_name"))
        request_body.setdefault("temporaryPassword", temporary_password)
        request_body.setdefault("roleIds", list(role_ids))
        response = self.module_client.create_user(request_body, context.idempotency_key)
        _assert_status(response, 201, "create user")
        resource_id, version = extract_resource_id_version(response)
        context.save_resource("user", resource_id, version)
        self.runtime.register(
            "user",
            resource_id,
            version=version,
            cleanup_description=(
                f"user:{resource_id}: no documented user delete endpoint; "
                "manual controlled cleanup is required"
            ),
        )
        return context

    def query_role(self, *, role_id: str | None = None, **params: Any) -> Module2ResourceContext:
        response = self.module_client.get_role(role_id) if role_id else self.module_client.list_roles(**params)
        _assert_status(response, 200, "query role")
        resource_id, version = extract_resource_id_version(response, preferred_id=role_id)
        context = Module2ResourceContext()
        context.save_resource("role", resource_id, version)
        return context

    def snapshot_role_permissions(self, role_id: str) -> tuple[str, ...]:
        response = self.module_client.get_role(role_id)
        _assert_status(response, 200, "get role for permission snapshot")
        payload = response.json()
        data = payload.get("data") if isinstance(payload, Mapping) else None
        permission_ids = data.get("permissionIds") if isinstance(data, Mapping) else None
        if not isinstance(permission_ids, list) or not all(isinstance(item, (str, int)) for item in permission_ids):
            raise RuntimeError("role response did not contain permissionIds")
        snapshot = tuple(str(item) for item in permission_ids)
        self.runtime.run_context.role_permission_snapshots[str(role_id)] = snapshot
        return snapshot

    def snapshot_user_roles(self, user_id: str) -> tuple[str, ...]:
        response = self.module_client.get_user(user_id)
        _assert_status(response, 200, "get user for role snapshot")
        payload = response.json()
        data = payload.get("data") if isinstance(payload, Mapping) else None
        role_ids = data.get("roleIds") if isinstance(data, Mapping) else None
        if not isinstance(role_ids, list) or not all(isinstance(item, (str, int)) for item in role_ids):
            raise RuntimeError("user response did not contain roleIds")
        snapshot = tuple(str(item) for item in role_ids)
        self.runtime.run_context.user_role_snapshots[str(user_id)] = snapshot
        return snapshot

    def capture_permissions(self) -> tuple[str, ...]:
        """Capture the approved permission IDs visible to the active tenant identity."""
        response = self.module_client.list_permissions()
        _assert_status(response, 200, "list permissions")
        payload = response.json()
        data = payload.get("data") if isinstance(payload, Mapping) else None
        if not isinstance(data, Sequence) or isinstance(data, (str, bytes, bytearray)):
            raise RuntimeError("permission response did not contain a list")
        permission_ids = []
        for item in data:
            if not isinstance(item, Mapping) or item.get("id") is None:
                raise RuntimeError("permission response contained an item without id")
            permission_ids.append(str(item["id"]))
        return tuple(permission_ids)


class Module2DynamicAuthenticator:
    """Create all module-2 identities in process memory from one durable login."""

    def __init__(
        self,
        *,
        auth_client_factory: Callable[[], Any],
        module_client: Any,
        runtime: Module2Runtime,
        environment: Mapping[str, str] | None = None,
    ):
        self.auth_client_factory = auth_client_factory
        self.module_client = module_client
        self.runtime = runtime
        self.environment = environment if environment is not None else os.environ

    def provision(self, *, platform_username: str, platform_password_env: str) -> dict[str, IdentityContext]:
        setup = Module2SetupActions(
            auth_client=self.auth_client_factory(), module_client=self.module_client,
            runtime=self.runtime, environment=self.environment,
        )
        setup.login(CredentialProfile("platform_admin", platform_username, None, platform_password_env))
        tenant_credentials = {
            alias: self._tenant_credentials(alias) for alias in ("tenant_admin_a", "tenant_admin_b")
        }
        setup.create_tenant_pair(
            tenant_a_body=self._tenant_body("a", tenant_credentials["tenant_admin_a"]),
            tenant_b_body=self._tenant_body("b", tenant_credentials["tenant_admin_b"]),
        )
        for alias, credentials in tenant_credentials.items():
            setup.login(
                CredentialProfile(alias, credentials["username"], credentials["tenant_code"], "runtime-only", credentials["password"]),
                auth_client=self.auth_client_factory(),
            )
        self._create_no_permission_identity(setup, tenant_credentials["tenant_admin_a"])
        return dict(self.runtime.identities)

    def _tenant_credentials(self, alias: str) -> dict[str, str]:
        suffix = runtime_uuid(alias)
        return {
            "tenant_code": f"M2{suffix[-20:].upper()}",
            "username": f"{alias}_{suffix[-16:]}",
            "password": f"M2!{uuid.uuid4().hex[:16]}Aa9",
        }

    def _tenant_body(self, suffix: str, credentials: Mapping[str, str]) -> dict[str, Any]:
        return {
            "tenantCode": credentials["tenant_code"],
            "tenantName": self.runtime.run_context.unique_name("tenant", suffix),
            "initialAdmin": {
                "username": credentials["username"],
                "displayName": self.runtime.run_context.unique_name("admin", suffix),
                "temporaryPassword": credentials["password"],
            },
        }

    def _create_no_permission_identity(self, setup: Module2SetupActions, tenant_a: Mapping[str, str]) -> None:
        setup.use_identity("tenant_admin_a")
        roles_response = self.module_client.list_roles(status="ACTIVE")
        _assert_status(roles_response, 200, "list roles for low-permission identity")
        roles = _required_mapping_value(roles_response.json(), ("data",))
        role_id = next(
            (
                str(role["id"]) for role in roles
                if isinstance(role, Mapping) and role.get("id") is not None and role.get("permissionIds") == []
            ),
            None,
        )
        if role_id is None:
            raise RuntimeError("module 2 requires an ACTIVE tenant role with no permissions for the low-permission identity")
        password = f"M2!{uuid.uuid4().hex[:16]}Aa9"
        username = self.runtime.run_context.unique_name("no_permission", "user")
        setup.create_user(
            case_no="USER-001", temporary_password=password, role_ids=[role_id],
            body={"username": username, "displayName": self.runtime.run_context.unique_name("limited", "user")},
        )
        setup.login(
            CredentialProfile("no_permission", username, tenant_a["tenant_code"], "runtime-only", password),
            auth_client=self.auth_client_factory(),
        )


def extract_resource_id_version(response: Any, *, preferred_id: str | None = None) -> tuple[str, int]:
    payload = response.json()
    data = payload.get("data") if isinstance(payload, Mapping) else None
    if isinstance(data, Mapping):
        resource_id = data.get("id") or data.get("roleId") or preferred_id
        version = data.get("version")
    elif isinstance(data, Sequence) and not isinstance(data, (str, bytes, bytearray)) and data:
        first = data[0]
        resource_id = first.get("id") if isinstance(first, Mapping) else preferred_id
        version = first.get("version") if isinstance(first, Mapping) else None
    else:
        resource_id = preferred_id
        version = None
    if resource_id is None or version is None:
        raise ValueError("response does not contain a resource id and version")
    return str(resource_id), int(version)


def assert_version_increment(previous: int, current: int) -> None:
    if current != previous + 1:
        raise AssertionError(f"expected version {previous + 1}, got {current}")


def assert_version_conflict(response: Any, error_code: str = "COMMON-1005") -> None:
    assert_error(response, 409, error_code)


def assert_forbidden(response: Any, error_code: str = "COMMON-1004") -> None:
    assert_error(response, 403, error_code)


def assert_cross_tenant_not_found(response: Any, error_code: str = "COMMON-1006") -> None:
    assert_error(response, 404, error_code)


def assert_idempotency_replay(first_response: Any, replay_response: Any) -> None:
    """Assert that same-key replay returns one logical resource, not a second effect."""
    first_id, _ = extract_resource_id_version(first_response)
    replay_id, _ = extract_resource_id_version(replay_response)
    if first_id != replay_id:
        raise AssertionError("same idempotency key returned a different resource id")


def assert_no_version_change(previous: int, current: int) -> None:
    if previous != current:
        raise AssertionError(f"expected version to remain {previous}, got {current}")


def assert_live_rejection(
    response: Any,
    *,
    statuses: Sequence[int] = (401, 403, 404, 422),
    error_codes: Sequence[str] = ("COMMON-1002", "COMMON-1004", "COMMON-1006"),
) -> None:
    payload = response.json()
    status = getattr(response, "status_code", None)
    if status not in statuses:
        raise AssertionError(f"expected live rejection status, got {status}")
    if isinstance(payload, Mapping) and payload.get("error", {}).get("code") not in error_codes:
        raise AssertionError("live rejection returned an unexpected error code")


def assert_error(response: Any, status: int, error_code: str) -> None:
    if getattr(response, "status_code", None) != status:
        raise AssertionError(f"expected HTTP {status}, got {getattr(response, 'status_code', None)}")
    payload = response.json()
    actual_code = payload.get("error", {}).get("code") if isinstance(payload, Mapping) else None
    if actual_code != error_code:
        raise AssertionError(f"expected error code {error_code}, got {actual_code}")


def poll_until(
    predicate: Callable[[], Any],
    *,
    timeout_seconds: float = 5.0,
    interval_seconds: float = 0.1,
    clock: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
) -> Any:
    if timeout_seconds < 0 or interval_seconds <= 0:
        raise ValueError("timeout must be non-negative and interval must be positive")
    deadline = clock() + timeout_seconds
    while True:
        result = predicate()
        if result:
            return result
        now = clock()
        if now >= deadline:
            raise TimeoutError("polling timed out")
        sleep(min(interval_seconds, deadline - now))


def run_concurrently(
    action: Callable[[Any], Any],
    inputs: Sequence[Any],
    *,
    max_workers: int = 2,
) -> list[Any]:
    """Run an injected action concurrently; backend isolation remains caller-owned."""
    if max_workers < 1:
        raise ValueError("max_workers must be positive")
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        return list(executor.map(action, inputs))


def _required_mapping_value(payload: Any, path: tuple[str, ...]) -> Any:
    current = payload
    for key in path:
        if not isinstance(current, Mapping) or key not in current:
            raise RuntimeError("response did not contain required authentication data")
        current = current[key]
    if current is None or current == "":
        raise RuntimeError("response contained empty authentication data")
    return current


def _assert_status(response: Any, expected: int, action: str) -> None:
    actual = getattr(response, "status_code", None)
    if actual != expected:
        raise RuntimeError(f"{action} failed with HTTP {actual}")
