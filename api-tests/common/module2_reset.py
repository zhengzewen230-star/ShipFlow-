"""Reset gate for the dedicated module-2 isolated database only.

This module deliberately performs no DDL/DML.  A future reset command must call
``validate_reset_gate`` immediately before executing the reviewed baseline
scripts, so every destructive operation has one auditable, exact gate.
"""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from urllib.parse import urlparse
from pathlib import Path
import hashlib
import os
import pymysql
from pymysql.constants import CLIENT


TARGET_DATABASE = "shipflow_http_test"
BASELINE_FILENAMES = tuple(f"0{i}_" for i in range(1, 7))
BASELINE_HASHES = {
    "01_schema_empty_target.sql": "64466DC9B9798FFE352CCE2100CC4B305F49830007616AC8305D84998AAF31BA",
    "02_init_data.sql": "E96C5BEB2EFFA4CC57DEB621193CA61083BC62B7E06EBD83CD5098267EC1AFBA",
    "03_V002__add_api_support_tables.sql": "406E6B87B7274B8857EF1974D870E37810CDCB61F61F461E2584B643CFD178DC",
    "04_V003__repair_v002_comments.sql": "0B592C1F94ECDF5AB77B388CA9E1998F5DBACA576C9E68C5D779818488864C41",
    "05_V004__add_tenant_management_permissions.sql": "990152A3E543BD681FF1D7CD55A19E0176727E7AD61B8B8B337C80A2019219F6",
    "06_V005__add_tenant_rbac_permissions.sql": "608B1398443921BF04935F2E10CECE270740CE2E9C74D66977EE18B6027B743B",
}
REQUIRED_RESET_ENVIRONMENT = (
    "SHIPFLOW_MODULE2_ALLOW_RESET",
    "SHIPFLOW_MODULE2_RESET_DB_URL",
    "SHIPFLOW_MODULE2_RESET_DB_USERNAME",
    "SHIPFLOW_MODULE2_RESET_DB_PASSWORD",
    "SHIPFLOW_MODULE2_RESET_DB_ACCOUNT",
    "SHIPFLOW_MODULE2_BASE_URL",
    "SHIPFLOW_MODULE2_ISOLATED_BASE_URL",
)


class Module2ResetGateError(RuntimeError):
    """Raised before any reset statement when an exact precondition is absent."""


def reviewed_baseline_files(repository_root: Path) -> tuple[Path, ...]:
    directory = repository_root / "database" / "http-test-control"
    files = tuple(sorted(directory.glob("0[1-6]_*.sql")))
    if len(files) != 6 or any(not path.name.startswith(prefix) for path, prefix in zip(files, BASELINE_FILENAMES)):
        raise Module2ResetGateError("reviewed baseline whitelist is incomplete")
    if any(hashlib.sha256(path.read_bytes()).hexdigest().upper() != BASELINE_HASHES.get(path.name) for path in files):
        raise Module2ResetGateError("reviewed baseline hash validation failed")
    return files


def run_reviewed_baseline(repository_root: Path, environment: Mapping[str, str]) -> tuple[str, ...]:
    """Execute only the six reviewed scripts against the dedicated target."""
    target = parse_reset_target(environment)
    if environment.get("SHIPFLOW_MODULE2_ALLOW_RESET") != "1":
        raise Module2ResetGateError("SHIPFLOW_MODULE2_ALLOW_RESET must equal 1")
    password = environment.get("SHIPFLOW_MODULE2_RESET_DB_PASSWORD")
    if not password:
        raise Module2ResetGateError("reset database password is not configured")
    files = reviewed_baseline_files(repository_root)
    connection = pymysql.connect(host=target.host, port=target.port, user=target.username,
        password=password, database=target.database, autocommit=True, client_flag=CLIENT.MULTI_STATEMENTS)
    completed = []
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT DATABASE(), CURRENT_USER()")
            database, account = cursor.fetchone()
            validate_reset_gate(environment, selected_database=database, current_account=account)
            for path in files:
                cursor.execute(path.read_text(encoding="utf-8"))
                while cursor.nextset():
                    pass
                cursor.execute("SELECT DATABASE()")
                if cursor.fetchone()[0] != TARGET_DATABASE:
                    raise Module2ResetGateError(f"target changed after {path.name}")
                completed.append(path.name)
            cursor.execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=%s AND table_name IN ('tenant','sys_user','sys_role','sys_permission')", (TARGET_DATABASE,))
            if cursor.fetchone()[0] != 4:
                raise Module2ResetGateError("baseline verification failed")
    finally:
        connection.close()
    return tuple(completed)


@dataclass(frozen=True)
class ResetConnectionTarget:
    host: str
    port: int
    database: str
    username: str


def parse_reset_target(environment: Mapping[str, str]) -> ResetConnectionTarget:
    """Parse one explicit JDBC URL; no fallback, replacement, or fuzzy matching."""
    url = environment.get("SHIPFLOW_MODULE2_RESET_DB_URL")
    username = environment.get("SHIPFLOW_MODULE2_RESET_DB_USERNAME")
    if not url or not username:
        raise Module2ResetGateError("reset database URL or username is not configured")
    parsed = urlparse(url.removeprefix("jdbc:"))
    if parsed.scheme != "mysql" or not parsed.hostname or not parsed.path:
        raise Module2ResetGateError("reset database URL must be an absolute MySQL JDBC URL")
    database = parsed.path.lstrip("/").split("?", 1)[0]
    if database != TARGET_DATABASE:
        raise Module2ResetGateError("reset database name is not shipflow_http_test")
    return ResetConnectionTarget(parsed.hostname, parsed.port or 3306, database, username)


def validate_reset_gate(
    environment: Mapping[str, str],
    *,
    selected_database: str,
    current_account: str,
) -> ResetConnectionTarget:
    """Validate all reset conditions before baseline DDL/DML is even considered."""
    missing = [name for name in REQUIRED_RESET_ENVIRONMENT if not environment.get(name)]
    if missing:
        raise Module2ResetGateError("reset environment is incomplete: " + ", ".join(missing))
    if environment["SHIPFLOW_MODULE2_ALLOW_RESET"] != "1":
        raise Module2ResetGateError("SHIPFLOW_MODULE2_ALLOW_RESET must equal 1")
    if environment["SHIPFLOW_MODULE2_BASE_URL"] != environment["SHIPFLOW_MODULE2_ISOLATED_BASE_URL"]:
        raise Module2ResetGateError("module 2 API URL is not the explicitly approved isolated URL")
    target = parse_reset_target(environment)
    if selected_database != TARGET_DATABASE:
        raise Module2ResetGateError("SELECT DATABASE() is not shipflow_http_test")
    if current_account != environment["SHIPFLOW_MODULE2_RESET_DB_ACCOUNT"]:
        raise Module2ResetGateError("CURRENT_USER() is not the dedicated reset account")
    return target
