import pytest

from common.json_path import JsonPathResolver
from common.scenario_context import ScenarioContext
from executors.extractor_executor import (
    ExtractionError,
    ExtractorExecutor,
)


class FakeResponse:
    def __init__(
        self,
        *,
        json_body=None,
        headers=None,
        cookies=None,
        json_error=False,
    ):
        self.json_body = json_body
        self.headers = headers or {}
        self.cookies = cookies or {}
        self.json_error = json_error
        self.json_call_count = 0

    def json(self):
        self.json_call_count += 1

        if self.json_error:
            raise ValueError("Invalid JSON")

        return self.json_body


@pytest.fixture
def extractor_executor():
    return ExtractorExecutor(
        json_path_resolver=JsonPathResolver()
    )


def test_extract_json_and_cookie(
    extractor_executor,
):
    response = FakeResponse(
        json_body={
            "data": {
                "accessToken": "access-value",
            }
        },
        cookies={
            "REFRESH_TOKEN": "refresh-value",
        },
    )

    context = ScenarioContext()

    result = extractor_executor.execute(
        response=response,
        extractors=[
            {
                "source": "json",
                "path": "$.data.accessToken",
                "save_as": "ACCESS_TOKEN",
            },
            {
                "source": "cookie",
                "path": "REFRESH_TOKEN",
                "save_as": "REFRESH_COOKIE",
            },
        ],
        scenario_context=context,
    )

    assert result == {
        "ACCESS_TOKEN": "access-value",
        "REFRESH_COOKIE": "refresh-value",
    }

    assert (
        context["ACCESS_TOKEN"]
        == "access-value"
    )

    assert (
        context["REFRESH_COOKIE"]
        == "refresh-value"
    )


def test_extract_header(
    extractor_executor,
):
    response = FakeResponse(
        headers={
            "X-Trace-Id": "trace-value",
        }
    )

    context = ScenarioContext()

    extractor_executor.execute(
        response=response,
        extractors=[
            {
                "source": "header",
                "path": "X-Trace-Id",
                "save_as": "TRACE_ID",
            }
        ],
        scenario_context=context,
    )

    assert context["TRACE_ID"] == "trace-value"


def test_json_is_loaded_only_once(
    extractor_executor,
):
    response = FakeResponse(
        json_body={
            "data": {
                "accessToken": "access-value",
                "tokenType": "Bearer",
            }
        }
    )

    extractor_executor.execute(
        response=response,
        extractors=[
            {
                "source": "json",
                "path": "$.data.accessToken",
                "save_as": "ACCESS_TOKEN",
            },
            {
                "source": "json",
                "path": "$.data.tokenType",
                "save_as": "TOKEN_TYPE",
            },
        ],
        scenario_context=ScenarioContext(),
    )

    assert response.json_call_count == 1


def test_missing_value_does_not_update_context(
    extractor_executor,
):
    response = FakeResponse(
        json_body={
            "data": {
                "accessToken": "access-value",
            }
        },
        cookies={},
    )

    context = ScenarioContext()

    with pytest.raises(
        ExtractionError,
        match="REFRESH_TOKEN",
    ):
        extractor_executor.execute(
            response=response,
            extractors=[
                {
                    "source": "json",
                    "path": "$.data.accessToken",
                    "save_as": "ACCESS_TOKEN",
                },
                {
                    "source": "cookie",
                    "path": "REFRESH_TOKEN",
                    "save_as": "REFRESH_COOKIE",
                },
            ],
            scenario_context=context,
        )

    assert len(context) == 0


def test_invalid_json_raises_error(
    extractor_executor,
):
    response = FakeResponse(
        json_error=True
    )

    with pytest.raises(
        ExtractionError,
        match="不是合法JSON",
    ):
        extractor_executor.execute(
            response=response,
            extractors=[
                {
                    "source": "json",
                    "path": "$.data.accessToken",
                    "save_as": "ACCESS_TOKEN",
                }
            ],
            scenario_context=ScenarioContext(),
        )


def test_unsupported_source_raises_error(
    extractor_executor,
):
    response = FakeResponse()

    with pytest.raises(
        ExtractionError,
        match="database",
    ):
        extractor_executor.execute(
            response=response,
            extractors=[
                {
                    "source": "database",
                    "path": "token",
                    "save_as": "ACCESS_TOKEN",
                }
            ],
            scenario_context=ScenarioContext(),
        )