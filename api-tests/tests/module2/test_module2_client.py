from dataclasses import dataclass

import pytest

from clients.module2_client import Module2Client
from common.module2_runtime import Module2Runtime, runtime_idempotency_key, runtime_uuid


@dataclass
class FakeResponse:
    status_code: int = 200

    def json(self):
        return {"data": {"id": "resource-1", "version": 1}}


class FakeHttpClient:
    def __init__(self):
        self.calls = []

    def request(self, method, path, **kwargs):
        self.calls.append((method, path, kwargs))
        return FakeResponse()


def test_client_sends_auth_idempotency_and_version_without_secrets_in_client_state():
    http = FakeHttpClient()
    client = Module2Client(http, access_token="runtime-token")

    client.update_store("store-1", {"storeName": "changed"}, "key-1", 3)

    method, path, kwargs = http.calls[0]
    assert (method, path) == ("PUT", "/api/v1/stores/store-1")
    assert kwargs["headers"]["Authorization"] == "Bearer runtime-token"
    assert kwargs["headers"]["Idempotency-Key"] == "key-1"
    assert kwargs["json"] == {"storeName": "changed", "version": 3}


def test_client_can_send_a_runtime_request_id():
    http = FakeHttpClient()
    client = Module2Client(http, access_token="runtime-token")

    client.request("POST", "/api/v1/stores", body={"storeCode": "m2_store"}, request_id="M2-STORE-001-id")

    assert http.calls[0][2]["headers"]["X-Request-Id"] == "M2-STORE-001-id"


def test_all_module2_paths_are_explicit_and_use_runtime_ids():
    http = FakeHttpClient()
    client = Module2Client(http)
    client.get_tenant("tenant-a")
    client.get_store("store-a")
    client.get_user("user-a")
    client.get_role("role-a")
    client.list_permissions()

    assert [call[1] for call in http.calls] == [
        "/api/v1/platform/tenants/tenant-a",
        "/api/v1/stores/store-a",
        "/api/v1/users/user-a",
        "/api/v1/roles/role-a",
        "/api/v1/permissions",
    ]


def test_list_client_uses_query_parameters():
    http = FakeHttpClient()
    client = Module2Client(http)

    client.list_stores(status="ACTIVE", platformCode="SHOP")

    assert http.calls[0][2]["params"] == {
        "status": "ACTIVE",
        "platformCode": "SHOP",
    }


def test_runtime_generates_unique_safe_identifiers_and_tracks_cleanup():
    runtime = Module2Runtime()
    assert runtime_uuid("tenant").startswith("tenant_")
    assert runtime_idempotency_key("STORE-001").startswith("HTTP-ACCEPT-STORE-001-")
    runtime.register("store", "store-1", version=1, restore={"status": "ACTIVE"})
    assert runtime.ids("store") == ["store-1"]


def test_runtime_cleanup_records_failures_and_clears_records():
    class CleanupClient:
        def restore(self, record, body):
            return FakeResponse(status_code=409)

    runtime = Module2Runtime()
    runtime.register("user", "user-1", restore={"status": "ACTIVE"})
    runtime.cleanup(CleanupClient())
    assert runtime.ids() == []
    assert runtime.cleanup_failures == ["user:user-1:409"]


@pytest.mark.parametrize("case_no", ["TENANT-001", "STORE-001", "USER-001", "RBAC-001"])
def test_module2_runtime_is_opt_in(case_no, monkeypatch):
    monkeypatch.delenv("SHIPFLOW_MODULE2_RUN", raising=False)
    assert Module2Runtime().enabled is False
    assert case_no
