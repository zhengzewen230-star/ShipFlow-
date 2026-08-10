from __future__ import annotations

import os

import pytest


def missing_environment_variables(names: list[str]) -> list[str]:
    return [name for name in names if not os.getenv(name)]


def skip_if_missing_environment(names: list[str], *, reason: str) -> None:
    missing = missing_environment_variables(names)
    if missing:
        pytest.skip(f"{reason}: {', '.join(missing)}")
