import pytest

from common.module2_database_assertions import (
    Module2DatabaseAssertionError,
    assert_idempotency_single_effect,
    assert_no_cross_tenant_effect,
    assert_scoped_resource,
    assert_unchanged_version,
)


def test_scoped_resource_assertion_checks_id_tenant_version_status_and_deleted_state():
    assert_scoped_resource(
        {"id": "store-1", "tenant_id": "tenant-a", "version": 2, "status": "ACTIVE", "deleted": 0},
        resource_id="store-1",
        tenant_id="tenant-a",
        version=2,
        status="ACTIVE",
    )
    with pytest.raises(Module2DatabaseAssertionError, match="tenant_id"):
        assert_scoped_resource({"id": "store-1", "tenant_id": "tenant-b", "deleted": 0}, resource_id="store-1", tenant_id="tenant-a")


def test_database_assertions_cover_cross_tenant_idempotency_and_unchanged_version():
    assert_no_cross_tenant_effect([], tenant_id="tenant-a")
    assert_idempotency_single_effect([{"id": "tenant-1"}], idempotency_key="key-1")
    assert_unchanged_version({"version": 3}, {"version": 3})

    with pytest.raises(Module2DatabaseAssertionError):
        assert_no_cross_tenant_effect([{"id": "store-b"}], tenant_id="tenant-a")
    with pytest.raises(Module2DatabaseAssertionError):
        assert_idempotency_single_effect([], idempotency_key="key-1")
    with pytest.raises(Module2DatabaseAssertionError):
        assert_unchanged_version({"version": 3}, {"version": 4})
