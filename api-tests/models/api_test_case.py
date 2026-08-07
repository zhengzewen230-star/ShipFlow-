from dataclasses import dataclass
from datetime import datetime
from typing import Any


@dataclass(frozen=True, slots=True)
class ApiTestCase:
    id: int
    case_no: str
    module: str
    title: str
    test_type: str
    priority: str
    precondition: str

    http_method: str
    request_path: str
    headers_template: dict[str, Any]
    cookie_template: dict[str, Any]
    request_body_template: str | None

    expected_status: int
    expected_error_code: str | None
    assertions: list[dict[str, Any]]
    data_dependency: str

    enabled: bool
    setup_steps: list[dict[str, Any]]
    extractors: list[dict[str, Any]]
    teardown_steps: list[dict[str, Any]]
    tags: list[str]

    execution_order: int
    automation_status: str
    environment_scope: str

    created_at: datetime
    updated_at: datetime