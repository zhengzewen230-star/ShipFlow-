from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from clients.http_client import HttpClient


class Module2Client:
    """Small resource client for module 2; it never owns credentials."""

    def __init__(self, http_client: HttpClient, access_token: str | None = None):
        self.http_client = http_client
        self.access_token = access_token

    def _headers(self, extra: Mapping[str, Any] | None = None) -> dict[str, str]:
        headers = {"Accept": "application/json"}
        if self.access_token:
            headers["Authorization"] = f"Bearer {self.access_token}"
        if extra:
            headers.update({str(key): str(value) for key, value in extra.items()})
        return headers

    def request(
        self,
        method: str,
        path: str,
        *,
        body: Any = None,
        idempotency_key: str | None = None,
        version: int | None = None,
        request_id: str | None = None,
        headers: Mapping[str, Any] | None = None,
        raw_body: str | bytes | None = None,
    ):
        request_headers = self._headers(headers)
        if idempotency_key is not None:
            request_headers["Idempotency-Key"] = idempotency_key
        if request_id is not None:
            request_headers["X-Request-Id"] = request_id
        request_kwargs: dict[str, Any] = {"headers": request_headers}
        if body is not None:
            request_kwargs["json"] = body
        if raw_body is not None:
            request_kwargs["data"] = raw_body
        if version is not None and isinstance(body, dict):
            request_kwargs["json"] = {**body, "version": version}
        return self.http_client.request(method, path, **request_kwargs)

    def create_tenant(self, body, idempotency_key):
        return self.request("POST", "/api/v1/platform/tenants", body=body, idempotency_key=idempotency_key)

    def list_tenants(self, **params):
        return self._list("/api/v1/platform/tenants", params)

    def get_tenant(self, tenant_id):
        return self.request("GET", f"/api/v1/platform/tenants/{tenant_id}")

    def update_tenant(self, tenant_id, body, idempotency_key, version):
        return self.request("PUT", f"/api/v1/platform/tenants/{tenant_id}", body=body, idempotency_key=idempotency_key, version=version)

    def change_tenant_status(self, tenant_id, status, version):
        return self.request("POST", f"/api/v1/platform/tenants/{tenant_id}/status", body={"status": status}, version=version)

    def create_store(self, body, idempotency_key):
        return self.request("POST", "/api/v1/stores", body=body, idempotency_key=idempotency_key)

    def list_stores(self, **params):
        return self._list("/api/v1/stores", params)

    def get_store(self, store_id):
        return self.request("GET", f"/api/v1/stores/{store_id}")

    def update_store(self, store_id, body, idempotency_key, version):
        return self.request("PUT", f"/api/v1/stores/{store_id}", body=body, idempotency_key=idempotency_key, version=version)

    def change_store_status(self, store_id, status, version):
        return self.request("POST", f"/api/v1/stores/{store_id}/status", body={"status": status}, version=version)

    def create_user(self, body, idempotency_key):
        return self.request("POST", "/api/v1/users", body=body, idempotency_key=idempotency_key)

    def list_users(self, **params):
        return self._list("/api/v1/users", params)

    def get_user(self, user_id):
        return self.request("GET", f"/api/v1/users/{user_id}")

    def update_user(self, user_id, body, idempotency_key, version):
        return self.request("PUT", f"/api/v1/users/{user_id}", body=body, idempotency_key=idempotency_key, version=version)

    def change_user_status(self, user_id, status, version):
        return self.request("POST", f"/api/v1/users/{user_id}/status", body={"status": status}, version=version)

    def replace_user_roles(self, user_id, role_ids, version):
        return self.request("PUT", f"/api/v1/users/{user_id}/roles", body={"roleIds": role_ids}, version=version)

    def list_roles(self, **params):
        return self._list("/api/v1/roles", params)

    def get_role(self, role_id):
        return self.request("GET", f"/api/v1/roles/{role_id}")

    def list_permissions(self):
        return self.request("GET", "/api/v1/permissions")

    def replace_role_permissions(self, role_id, permission_ids, version):
        return self.request("PUT", f"/api/v1/roles/{role_id}/permissions", body={"permissionIds": permission_ids}, version=version)

    def _list(self, path: str, params: Mapping[str, Any]):
        request_headers = self._headers()
        return self.http_client.request("GET", path, headers=request_headers, params=dict(params))


def response_data(response) -> Any:
    """Decode the standard response without logging its contents."""
    payload = response.json()
    if not isinstance(payload, dict) or "data" not in payload:
        raise ValueError("response does not contain data")
    return payload["data"]
