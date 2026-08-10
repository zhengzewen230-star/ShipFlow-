"""Pure helpers for bounded, read-only module-2 database assertions.

The helpers accept rows returned by an injected read-only fixture. They never
open a connection or construct write SQL, which keeps business-database access
outside the test metadata and explicit at execution time.
"""

from collections.abc import Mapping, Sequence
from typing import Any


class Module2DatabaseAssertionError(AssertionError):
    pass


def assert_scoped_resource(
    row: Mapping[str, Any] | None,
    *,
    resource_id: str,
    tenant_id: str | None,
    version: int | None = None,
    status: str | None = None,
    deleted: int | None = 0,
) -> None:
    """Validate only an explicitly selected resource row and tenant scope."""
    if row is None:
        raise Module2DatabaseAssertionError("expected resource row was not found")
    if str(row.get("id")) != str(resource_id):
        raise Module2DatabaseAssertionError("database row id does not match runtime resource")
    if tenant_id is not None and str(row.get("tenant_id")) != str(tenant_id):
        raise Module2DatabaseAssertionError("database row tenant_id does not match runtime scope")
    if version is not None and row.get("version") != version:
        raise Module2DatabaseAssertionError("database row version does not match expected version")
    if status is not None and row.get("status") != status:
        raise Module2DatabaseAssertionError("database row status does not match expected status")
    if deleted is not None and row.get("deleted") != deleted:
        raise Module2DatabaseAssertionError("database row deleted state does not match expected state")


def assert_no_cross_tenant_effect(rows: Sequence[Mapping[str, Any]], *, tenant_id: str) -> None:
    """Assert a bounded query returned no rows owned by the caller's other tenant."""
    if rows:
        raise Module2DatabaseAssertionError(
            f"cross-tenant request produced {len(rows)} unexpected database rows for tenant {tenant_id}"
        )


def assert_idempotency_single_effect(rows: Sequence[Mapping[str, Any]], *, idempotency_key: str) -> None:
    """Same normalized key must map to exactly one persisted business effect."""
    if len(rows) != 1:
        raise Module2DatabaseAssertionError(
            f"idempotency key {idempotency_key} produced {len(rows)} persisted effects"
        )


def assert_unchanged_version(before: Mapping[str, Any], after: Mapping[str, Any]) -> None:
    if before.get("version") != after.get("version"):
        raise Module2DatabaseAssertionError("failed request changed the persisted version")
