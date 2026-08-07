from datetime import datetime

import pytest

from common.scenario_context import ScenarioContext
from executors.scenario_executor import (
    ScenarioExecutionError,
    ScenarioExecutionResult,
    ScenarioExecutor,
)
from models.api_test_case import ApiTestCase


class FakeResponse:
    def __init__(self, status_code=200):
        self.status_code = status_code


class FakeActionExecutor:
    def __init__(
        self,
        events,
        *,
        setup_error=None,
        teardown_error=None,
    ):
        self.events = events
        self.setup_error = setup_error
        self.teardown_error = teardown_error
        self.call_count = 0

    def execute(
        self,
        *,
        steps,
        scenario_context,
    ):
        self.call_count += 1

        if self.call_count == 1:
            self.events.append("setup")

            if self.setup_error is not None:
                raise self.setup_error

        else:
            self.events.append("teardown")

            if self.teardown_error is not None:
                raise self.teardown_error

        return []


class FakeRequestExecutor:
    def __init__(
        self,
        events,
        *,
        response=None,
        error=None,
    ):
        self.events = events
        self.response = response or FakeResponse()
        self.error = error

    def execute(
        self,
        *,
        test_case,
        scenario_context,
    ):
        self.events.append("request")

        if self.error is not None:
            raise self.error

        return self.response


class FakeExtractorExecutor:
    def __init__(
        self,
        events,
        *,
        extracted_values=None,
    ):
        self.events = events
        self.extracted_values = (
            extracted_values
            if extracted_values is not None
            else {"TRACE_ID": "trace-value"}
        )

    def execute(
        self,
        *,
        response,
        extractors,
        scenario_context,
    ):
        self.events.append("extract")

        scenario_context.update_variables(
            self.extracted_values
        )

        return dict(self.extracted_values)


class FakeAssertionExecutor:
    def __init__(
        self,
        events,
        *,
        error=None,
    ):
        self.events = events
        self.error = error

    def execute(
        self,
        *,
        response,
        test_case,
        scenario_context,
    ):
        self.events.append("assert")

        if self.error is not None:
            raise self.error


def make_test_case(**overrides):
    values = {
        "id": 1,
        "case_no": "AUTH-TEST-001",
        "module": "AUTH",
        "title": "测试场景执行器",
        "test_type": "POSITIVE",
        "priority": "P0",
        "precondition": "",
        "http_method": "GET",
        "request_path": "/api/v1/test",
        "headers_template": {},
        "cookie_template": {},
        "request_body_template": None,
        "expected_status": 200,
        "expected_error_code": None,
        "assertions": [],
        "data_dependency": "",
        "enabled": True,
        "setup_steps": [
            {
                "action": "get_csrf",
            }
        ],
        "extractors": [
            {
                "source": "header",
                "path": "X-Trace-Id",
                "save_as": "TRACE_ID",
            }
        ],
        "teardown_steps": [
            {
                "action": "logout",
            }
        ],
        "tags": ["auth", "ready"],
        "execution_order": 1,
        "automation_status": "READY",
        "environment_scope": "QA",
        "created_at": datetime(2026, 8, 7),
        "updated_at": datetime(2026, 8, 7),
    }

    values.update(overrides)

    return ApiTestCase(**values)


def build_scenario_executor(
    *,
    events,
    action_executor=None,
    request_executor=None,
    extractor_executor=None,
    assertion_executor=None,
):
    return ScenarioExecutor(
        action_executor=(
            action_executor
            or FakeActionExecutor(events)
        ),
        request_executor=(
            request_executor
            or FakeRequestExecutor(events)
        ),
        extractor_executor=(
            extractor_executor
            or FakeExtractorExecutor(events)
        ),
        assertion_executor=(
            assertion_executor
            or FakeAssertionExecutor(events)
        ),
    )


def test_successful_scenario_execution_order():
    events = []
    context = ScenarioContext()
    test_case = make_test_case()

    executor = build_scenario_executor(
        events=events
    )

    result = executor.execute(
        test_case=test_case,
        scenario_context=context,
    )

    assert isinstance(
        result,
        ScenarioExecutionResult,
    )

    assert events == [
        "setup",
        "request",
        "extract",
        "assert",
        "teardown",
    ]

    assert result.test_case is test_case
    assert result.response.status_code == 200

    assert result.extracted_values == {
        "TRACE_ID": "trace-value",
    }

    assert result.scenario_context is context

    assert (
        context["TRACE_ID"]
        == "trace-value"
    )


def test_teardown_runs_when_assertion_fails():
    events = []

    assertion_error = AssertionError(
        "assertion failed"
    )

    executor = build_scenario_executor(
        events=events,
        assertion_executor=FakeAssertionExecutor(
            events,
            error=assertion_error,
        ),
    )

    with pytest.raises(
        AssertionError,
        match="assertion failed",
    ):
        executor.execute(
            test_case=make_test_case()
        )

    assert events == [
        "setup",
        "request",
        "extract",
        "assert",
        "teardown",
    ]


def test_teardown_runs_when_setup_fails():
    events = []

    action_executor = FakeActionExecutor(
        events,
        setup_error=ValueError(
            "setup failed"
        ),
    )

    executor = build_scenario_executor(
        events=events,
        action_executor=action_executor,
    )

    with pytest.raises(
        ValueError,
        match="setup failed",
    ):
        executor.execute(
            test_case=make_test_case()
        )

    assert events == [
        "setup",
        "teardown",
    ]


def test_teardown_failure_fails_successful_case():
    events = []

    action_executor = FakeActionExecutor(
        events,
        teardown_error=RuntimeError(
            "logout failed"
        ),
    )

    executor = build_scenario_executor(
        events=events,
        action_executor=action_executor,
    )

    with pytest.raises(
        ScenarioExecutionError,
        match="后置清理动作执行失败",
    ):
        executor.execute(
            test_case=make_test_case()
        )

    assert events[-1] == "teardown"


def test_teardown_error_does_not_hide_request_error():
    events = []

    action_executor = FakeActionExecutor(
        events,
        teardown_error=RuntimeError(
            "logout failed"
        ),
    )

    request_executor = FakeRequestExecutor(
        events,
        error=ValueError(
            "request failed"
        ),
    )

    executor = build_scenario_executor(
        events=events,
        action_executor=action_executor,
        request_executor=request_executor,
    )

    with pytest.raises(
        ValueError,
        match="request failed",
    ) as exception_info:
        executor.execute(
            test_case=make_test_case()
        )

    notes = getattr(
        exception_info.value,
        "__notes__",
        [],
    )

    assert any(
        "后置清理动作执行失败" in note
        for note in notes
    )

    assert events == [
        "setup",
        "request",
        "teardown",
    ]


@pytest.mark.parametrize(
    ("changes", "expected_message"),
    [
        (
            {
                "enabled": False,
            },
            "未启用",
        ),
        (
            {
                "automation_status": "BLOCKED",
            },
            "不是READY",
        ),
    ],
)
def test_non_ready_case_is_rejected(
    changes,
    expected_message,
):
    executor = build_scenario_executor(
        events=[]
    )

    with pytest.raises(
        ScenarioExecutionError,
        match=expected_message,
    ):
        executor.execute(
            test_case=make_test_case(
                **changes
            )
        )