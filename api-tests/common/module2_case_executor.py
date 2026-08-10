"""第二模块冻结用例的真实 HTTP 执行器。"""

from __future__ import annotations

import uuid
import os
from concurrent.futures import ThreadPoolExecutor
from clients.auth_client import AuthClient
from clients.http_client import HttpClient
from dataclasses import dataclass
from urllib.parse import urlparse
import pymysql


@dataclass
class Module2CaseExecutor:
    repository: object
    clients: dict
    http_client: object
    runtime: object

    def __post_init__(self):
        tenants = [record for record in self.runtime.records if record.resource_type == "tenant"]
        if len(tenants) < 2:
            raise RuntimeError("模块二资源图缺少租户 A/B")
        self.tenant_a_id = tenants[0].resource_id
        self.tenant_b_id = tenants[1].resource_id
        self.tenant_versions = {self.tenant_a_id: tenants[0].initial_version or 0, self.tenant_b_id: tenants[1].initial_version or 0}

    def execute(self, case_no: str) -> None:
        cases = {case.case_no: case for case in self.repository.find_module2_frozen_cases()}
        case = cases[case_no]
        response = self._request(case_no)
        assert response.status_code == case.expected_status, f"{case_no} HTTP 状态码不匹配"
        payload = response.json()
        assert payload["success"] is (case.expected_error_code is None), f"{case_no} 业务成功标记不匹配"
        assert payload.get("traceId"), f"{case_no} 缺少 traceId"
        if case.expected_error_code:
            assert payload["error"]["code"] == case.expected_error_code, f"{case_no} 业务错误码不匹配"

    def _request(self, case_no: str):
        if case_no == "TENANT-001":
            suffix = uuid.uuid4().hex[:18].upper()
            return self.clients["platform_admin"].create_tenant({
                "tenantCode": f"M2{suffix}", "tenantName": f"模块二租户{suffix}",
                "initialAdmin": {"username": f"m2_admin_{suffix.lower()}", "displayName": "模块二管理员", "temporaryPassword": f"M2!{uuid.uuid4().hex[:16]}Aa9"},
            }, f"M2-TENANT-001-{uuid.uuid4()}")
        if case_no == "STORE-001":
            suffix = uuid.uuid4().hex[:18].upper()
            return self.clients["tenant_admin_a"].create_store({
                "storeCode": f"M2S{suffix}", "storeName": f"模块二店铺{suffix}",
                "platformCode": "M2", "platformAccount": f"m2_{suffix.lower()}",
            }, f"M2-STORE-001-{uuid.uuid4()}")
        if case_no == "USER-001":
            roles = self.clients["tenant_admin_a"].list_roles(status="ACTIVE").json()["data"]
            role_id = next(str(role["id"]) for role in roles if role.get("permissionIds"))
            suffix = uuid.uuid4().hex[:16]
            return self.clients["tenant_admin_a"].create_user({
                "username": f"m2_user_{suffix}", "displayName": "模块二用户",
                "temporaryPassword": f"M2!{uuid.uuid4().hex[:16]}Aa9", "roleIds": [role_id],
            }, f"M2-USER-001-{uuid.uuid4()}")
        if case_no == "RBAC-001":
            return self.clients["tenant_admin_a"].list_roles(status="ACTIVE")
        if case_no == "TENANT-002":
            return self.clients["platform_admin"].list_tenants(page="1", pageSize="20")
        if case_no == "TENANT-003":
            return self.clients["platform_admin"].get_tenant(self.tenant_a_id)
        if case_no == "TENANT-004":
            response = self.clients["platform_admin"].update_tenant(self.tenant_a_id, {"tenantName": f"模块二更新{uuid.uuid4().hex[:8]}"}, "unused", self.tenant_versions[self.tenant_a_id])
            if response.status_code == 200:
                self.tenant_versions[self.tenant_a_id] = response.json()["data"]["version"]
            return response
        if case_no == "TENANT-005":
            current = self.clients["platform_admin"].get_tenant(self.tenant_a_id).json()["data"]
            self.tenant_versions[self.tenant_a_id] = current["version"]
            response = self.clients["platform_admin"].change_tenant_status(self.tenant_a_id, "ACTIVE", self.tenant_versions[self.tenant_a_id])
            if response.status_code == 200:
                self.tenant_versions[self.tenant_a_id] = response.json()["data"]["version"]
            return response
        if case_no == "TENANT-006":
            csrf = AuthClient(self.http_client).get_csrf_token().cookies.get("XSRF-TOKEN")
            return self.http_client.request("POST", "/api/v1/platform/tenants", json={}, headers={"X-XSRF-TOKEN": csrf})
        if case_no == "TENANT-007":
            return self.http_client.request("GET", "/api/v1/platform/tenants")
        if case_no == "TENANT-008":
            return self.clients["tenant_admin_a"].create_tenant({}, f"M2-TENANT-008-{uuid.uuid4()}")
        if case_no == "TENANT-009":
            return self.clients["tenant_admin_a"].list_tenants(page="1", pageSize="20")
        if case_no == "TENANT-010":
            return self.clients["tenant_admin_a"].get_tenant(self.tenant_a_id)
        if case_no == "TENANT-011":
            return self.clients["platform_admin"].request(
                "POST", "/api/v1/platform/tenants",
                raw_body="{", idempotency_key=f"M2-TENANT-011-{uuid.uuid4()}",
                headers={"Content-Type": "application/json"},
            )
        if case_no == "TENANT-012":
            body = self._tenant_body("duplicate")
            self.clients["platform_admin"].create_tenant(body, f"M2-TENANT-012-A-{uuid.uuid4()}")
            return self.clients["platform_admin"].create_tenant(body, f"M2-TENANT-012-B-{uuid.uuid4()}")
        if case_no in {"TENANT-013", "TENANT-014"}:
            key = f"M2-{case_no}-{uuid.uuid4()}"; body = self._tenant_body(case_no)
            self.clients["platform_admin"].create_tenant(body, key)
            if case_no == "TENANT-014": body["tenantName"] += "变更"
            return self.clients["platform_admin"].create_tenant(body, key)
        if case_no == "TENANT-015":
            return self.clients["platform_admin"].update_tenant(self.tenant_a_id, {"tenantName": "过期版本"}, "unused", 0)
        if case_no == "TENANT-016":
            created = self.clients["platform_admin"].create_tenant(self._tenant_body("disabled"), f"M2-TENANT-016-{uuid.uuid4()}").json()["data"]
            self.clients["platform_admin"].change_tenant_status(created["id"], "DISABLED", created["version"])
            current = self.clients["platform_admin"].get_tenant(created["id"]).json()["data"]
            return self.clients["platform_admin"].update_tenant(created["id"], {"tenantName": "不得更新"}, "unused", current["version"])
        if case_no == "TENANT-017":
            key = f"M2-TENANT-017-{uuid.uuid4()}"; body = self._tenant_body("concurrent")
            with ThreadPoolExecutor(max_workers=2) as workers:
                responses = list(workers.map(lambda _: self.clients["platform_admin"].create_tenant(body, key), range(2)))
            assert all(item.status_code == 201 for item in responses), "同键并发创建未全部返回成功"
            return responses[0]
        if case_no == "STORE-002":
            return self.clients["tenant_admin_a"].list_stores(page="1", pageSize="20")
        if case_no == "STORE-003":
            return self.clients["tenant_admin_a"].get_store(self._ensure_store_pair()[0]["id"])
        if case_no == "STORE-004":
            store, _ = self._ensure_store_pair()
            response = self.clients["tenant_admin_a"].update_store(store["id"], self._store_body("updated"), "unused", store["version"])
            if response.status_code == 200:
                store.update(response.json()["data"])
            return response
        if case_no == "STORE-005":
            store, _ = self._ensure_store_pair()
            response = self.clients["tenant_admin_a"].change_store_status(store["id"], "ACTIVE", store["version"])
            if response.status_code == 200:
                store.update(response.json()["data"])
            return response
        if case_no == "STORE-006":
            return self._unauthenticated_write("/api/v1/stores", self._store_body("unauth"), f"M2-STORE-006-{uuid.uuid4()}")
        if case_no == "STORE-007":
            return self.clients["no_permission"].list_stores(page="1", pageSize="20")
        if case_no == "STORE-008":
            return self.clients["tenant_admin_a"].get_store(self._ensure_store_pair()[1]["id"])
        if case_no == "STORE-009":
            return self.clients["tenant_admin_a"].update_store(self._ensure_store_pair()[1]["id"], self._store_body("foreign"), "unused", 0)
        if case_no == "STORE-010":
            return self.clients["tenant_admin_a"].request("POST", "/api/v1/stores", raw_body="{", idempotency_key=f"M2-STORE-010-{uuid.uuid4()}", headers={"Content-Type": "application/json"})
        if case_no == "STORE-011":
            body = self._store_body("duplicate")
            self.clients["tenant_admin_a"].create_store(body, f"M2-STORE-011-A-{uuid.uuid4()}")
            return self.clients["tenant_admin_a"].create_store(body, f"M2-STORE-011-B-{uuid.uuid4()}")
        if case_no in {"STORE-012", "STORE-013"}:
            key = f"M2-{case_no}-{uuid.uuid4()}"; body = self._store_body(case_no)
            self.clients["tenant_admin_a"].create_store(body, key)
            if case_no == "STORE-013": body["storeName"] += "变更"
            return self.clients["tenant_admin_a"].create_store(body, key)
        if case_no == "STORE-014":
            created = self.clients["tenant_admin_a"].create_store(self._store_body("stale"), f"M2-STORE-014-{uuid.uuid4()}").json()["data"]
            self.clients["tenant_admin_a"].update_store(created["id"], self._store_body("advance"), "unused", created["version"])
            return self.clients["tenant_admin_a"].update_store(created["id"], self._store_body("staleagain"), "unused", created["version"])
        if case_no in {"STORE-015", "STORE-016"}:
            created = self.clients["tenant_admin_a"].create_store(self._store_body("disabled"), f"M2-{case_no}-{uuid.uuid4()}").json()["data"]
            self.clients["tenant_admin_a"].change_store_status(created["id"], "DISABLED", created["version"])
            current = self.clients["tenant_admin_a"].get_store(created["id"]).json()["data"]
            if case_no == "STORE-016": return self.clients["tenant_admin_a"].get_store(created["id"])
            return self.clients["tenant_admin_a"].update_store(created["id"], self._store_body("denied"), "unused", current["version"])
        if case_no == "STORE-017":
            body = self._store_body("invalid"); body["platformCode"] = ""
            return self.clients["tenant_admin_a"].create_store(body, f"M2-STORE-017-{uuid.uuid4()}")
        if case_no == "USER-002":
            return self.clients["tenant_admin_a"].list_users(page="1", pageSize="20")
        if case_no == "USER-003":
            return self.clients["tenant_admin_a"].get_user(self._ensure_user_pair()[0]["id"])
        if case_no == "USER-004":
            user, _ = self._ensure_user_pair()
            response = self.clients["tenant_admin_a"].update_user(user["id"], {"displayName": "模块二用户更新"}, f"M2-USER-004-{uuid.uuid4()}", user["version"])
            if response.status_code == 200: user.update(response.json()["data"])
            return response
        if case_no == "USER-005":
            user, _ = self._ensure_user_pair()
            response = self.clients["tenant_admin_a"].change_user_status(user["id"], "ACTIVE", user["version"])
            if response.status_code == 200: user.update(response.json()["data"])
            return response
        if case_no == "USER-006":
            user, _ = self._ensure_user_pair()
            response = self.clients["tenant_admin_a"].replace_user_roles(user["id"], [self._active_role_id("tenant_admin_a")], user["version"])
            if response.status_code == 200: user.update(response.json()["data"])
            return response
        if case_no == "USER-007":
            return self._unauthenticated_write("/api/v1/users", self._user_body("unauth", self._active_role_id("tenant_admin_a")), f"M2-USER-007-{uuid.uuid4()}")
        if case_no == "USER-008":
            return self.clients["no_permission"].list_users(page="1", pageSize="20")
        if case_no == "USER-009":
            return self.clients["tenant_admin_a"].get_user(self._ensure_user_pair()[1]["id"])
        if case_no == "USER-010":
            return self.clients["tenant_admin_a"].update_user(self._ensure_user_pair()[1]["id"], {"displayName": "越权"}, "unused", 0)
        if case_no == "USER-011":
            return self.clients["tenant_admin_a"].request("POST", "/api/v1/users", raw_body="{", idempotency_key=f"M2-USER-011-{uuid.uuid4()}", headers={"Content-Type": "application/json"})
        if case_no == "USER-012":
            body = self._user_body("duplicate", self._active_role_id("tenant_admin_a"))
            self.clients["tenant_admin_a"].create_user(body, f"M2-USER-012-A-{uuid.uuid4()}")
            return self.clients["tenant_admin_a"].create_user(body, f"M2-USER-012-B-{uuid.uuid4()}")
        if case_no in {"USER-013", "USER-014"}:
            key = f"M2-{case_no}-{uuid.uuid4()}"; body = self._user_body(case_no, self._active_role_id("tenant_admin_a"))
            self.clients["tenant_admin_a"].create_user(body, key)
            if case_no == "USER-014": body["displayName"] += "变更"
            return self.clients["tenant_admin_a"].create_user(body, key)
        if case_no == "USER-015":
            created = self.clients["tenant_admin_a"].create_user(self._user_body("stale", self._active_role_id("tenant_admin_a")), f"M2-USER-015-{uuid.uuid4()}").json()["data"]
            self.clients["tenant_admin_a"].update_user(created["id"], {"displayName": "前进"}, "M2-USER-015-A", created["version"])
            return self.clients["tenant_admin_a"].update_user(created["id"], {"displayName": "过期"}, "M2-USER-015-B", created["version"])
        if case_no in {"USER-016", "USER-017"}:
            user, _ = self._ensure_user_pair()
            bad_role = self._active_role_id("tenant_admin_b") if case_no == "USER-016" else 999999999
            return self.clients["tenant_admin_a"].replace_user_roles(user["id"], [bad_role], user["version"])
        if case_no == "USER-020":
            return self.clients["tenant_admin_a"].get_user(999999999)
        if case_no == "RBAC-002":
            return self.clients["tenant_admin_a"].get_role(self._role_pair()[0]["id"])
        if case_no == "RBAC-003":
            return self.clients["tenant_admin_a"].list_permissions()
        if case_no == "RBAC-004":
            role, _ = self._role_pair()
            response = self.clients["tenant_admin_a"].replace_role_permissions(role["id"], role["permissionIds"], role["version"])
            if response.status_code == 200: role.update(response.json()["data"])
            return response
        if case_no == "RBAC-005":
            return self.http_client.request("GET", "/api/v1/roles")
        if case_no == "RBAC-006":
            return self.clients["no_permission"].list_roles(status="ACTIVE")
        if case_no == "RBAC-007":
            return self.clients["tenant_admin_a"].get_role(self._role_pair()[1]["id"])
        if case_no == "RBAC-008":
            role = self._role_pair()[1]
            return self.clients["tenant_admin_a"].replace_role_permissions(role["id"], role["permissionIds"], role["version"])
        if case_no == "RBAC-009":
            role = self._role_pair()[0]
            return self.clients["no_permission"].replace_role_permissions(role["id"], role["permissionIds"], role["version"])
        if case_no == "RBAC-010":
            role = self._role_pair()[0]
            return self.clients["tenant_admin_a"].replace_role_permissions(role["id"], [999999999], role["version"])
        if case_no == "RBAC-011":
            role = self._role_pair()[0]
            current = self.clients["tenant_admin_a"].get_role(role["id"]).json()["data"]
            self.clients["tenant_admin_a"].replace_role_permissions(role["id"], current["permissionIds"], current["version"])
            return self.clients["tenant_admin_a"].replace_role_permissions(role["id"], current["permissionIds"], current["version"])
        if case_no in {"RBAC-012", "RBAC-013"}:
            role = self._disable_no_permission_role()
            if case_no == "RBAC-012": return self.clients["tenant_admin_a"].get_role(role["id"])
            return self.clients["tenant_admin_a"].replace_role_permissions(role["id"], [], role["version"])
        if case_no == "RBAC-014":
            role = self._role_pair()[0]
            return self.clients["tenant_admin_a"].replace_role_permissions(role["id"], [999999999], role["version"])
        raise RuntimeError(f"{case_no} 尚未实现真实 HTTP 场景，禁止作为全量验收运行")

    @staticmethod
    def _tenant_body(label: str) -> dict:
        suffix = uuid.uuid4().hex[:18].upper()
        return {"tenantCode": f"M2{suffix}", "tenantName": f"模块二{label}{suffix}", "initialAdmin": {"username": f"m2_{suffix.lower()}", "displayName": "模块二管理员", "temporaryPassword": f"M2!{uuid.uuid4().hex[:16]}Aa9"}}

    @staticmethod
    def _store_body(label: str) -> dict:
        suffix = uuid.uuid4().hex[:16]
        return {"storeCode": f"M2S{suffix.upper()}", "storeName": f"模块二店铺{label}{suffix}", "platformCode": "M2", "platformAccount": f"m2_{suffix}"}

    def _ensure_store_pair(self):
        if hasattr(self, "store_pair"):
            return self.store_pair
        first = self.clients["tenant_admin_a"].create_store(self._store_body("a"), f"M2-STORE-A-{uuid.uuid4()}")
        second = self.clients["tenant_admin_b"].create_store(self._store_body("b"), f"M2-STORE-B-{uuid.uuid4()}")
        assert first.status_code == 201 and second.status_code == 201, "动态店铺前置创建失败"
        self.store_pair = (first.json()["data"], second.json()["data"])
        return self.store_pair

    def _active_role_id(self, identity_name: str):
        roles = self.clients[identity_name].list_roles(status="ACTIVE").json()["data"]
        return next(role["id"] for role in roles if role.get("permissionIds"))

    @staticmethod
    def _user_body(label: str, role_id: int) -> dict:
        suffix = uuid.uuid4().hex[:16]
        return {"username": f"m2_user_{label}_{suffix}", "displayName": f"模块二用户{label}", "temporaryPassword": f"M2!{uuid.uuid4().hex[:16]}Aa9", "roleIds": [role_id]}

    def _ensure_user_pair(self):
        if hasattr(self, "user_pair"):
            return self.user_pair
        first = self.clients["tenant_admin_a"].create_user(self._user_body("a", self._active_role_id("tenant_admin_a")), f"M2-USER-A-{uuid.uuid4()}")
        second = self.clients["tenant_admin_b"].create_user(self._user_body("b", self._active_role_id("tenant_admin_b")), f"M2-USER-B-{uuid.uuid4()}")
        assert first.status_code == 201 and second.status_code == 201, "动态用户前置创建失败"
        self.user_pair = (first.json()["data"], second.json()["data"])
        return self.user_pair

    def _role_pair(self):
        if hasattr(self, "role_pair"):
            return self.role_pair
        first = next(role for role in self.clients["tenant_admin_a"].list_roles(status="ACTIVE").json()["data"] if role.get("permissionIds"))
        second = next(role for role in self.clients["tenant_admin_b"].list_roles(status="ACTIVE").json()["data"] if role.get("permissionIds"))
        self.role_pair = (first, second)
        return self.role_pair

    def _unauthenticated_write(self, path, body, key):
        client = HttpClient(self.http_client.base_url, timeout=self.http_client.timeout, verify_ssl=self.http_client.verify_ssl)
        try:
            csrf = AuthClient(client).get_csrf_token().cookies.get("XSRF-TOKEN")
            return client.request("POST", path, json=body, headers={"X-XSRF-TOKEN": csrf, "Cookie": f"XSRF-TOKEN={csrf}", "Idempotency-Key": key})
        finally:
            client.close()

    def _disable_no_permission_role(self):
        roles = self.clients["tenant_admin_a"].list_roles().json()["data"]
        role = next(item for item in roles if item["roleCode"] == "TEST_NO_PERMISSION")
        parsed = urlparse(os.environ["SHIPFLOW_HTTP_TEST_DB_URL"].removeprefix("jdbc:"))
        connection = pymysql.connect(host=parsed.hostname, port=parsed.port or 3306,
            user=os.environ["SHIPFLOW_HTTP_TEST_DB_USERNAME"], password=os.environ["SHIPFLOW_HTTP_TEST_DB_PASSWORD"],
            database=parsed.path.lstrip("/").split("?", 1)[0], autocommit=True)
        try:
            with connection.cursor() as cursor:
                cursor.execute("UPDATE sys_role SET status='DISABLED', version=version+1 WHERE id=%s AND tenant_id=%s AND role_code='TEST_NO_PERMISSION'", (role["id"], self.tenant_a_id))
                if cursor.rowcount != 1: raise RuntimeError("停用角色前置未生效")
        finally:
            connection.close()
        return self.clients["tenant_admin_a"].get_role(role["id"]).json().get("data", role)
