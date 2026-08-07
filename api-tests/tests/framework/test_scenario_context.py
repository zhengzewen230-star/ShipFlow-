import pytest

from common.placeholder_resolver import (
    PlaceholderResolver,
)
from common.scenario_context import (
    ScenarioContext,
    ScenarioVariableError,
)


def test_set_and_require_variable():
    context = ScenarioContext()

    context.set("ACCESS_TOKEN", "token-value")

    assert context.require(
        "ACCESS_TOKEN"
    ) == "token-value"

    assert context.contains("ACCESS_TOKEN") is True


def test_require_missing_variable_raises_error():
    context = ScenarioContext()

    with pytest.raises(
        ScenarioVariableError,
        match="ACCESS_TOKEN",
    ):
        context.require("ACCESS_TOKEN")


def test_invalid_variable_name_raises_error():
    context = ScenarioContext()

    with pytest.raises(
        ScenarioVariableError,
        match="access_token",
    ):
        context.set(
            "access_token",
            "token-value",
        )


def test_compatible_token_properties():
    context = ScenarioContext()

    context.xsrf_token = "xsrf-value"
    context.access_token = "access-value"
    context.refresh_token = "refresh-value"

    assert context["XSRF_TOKEN"] == "xsrf-value"
    assert context["ACCESS_TOKEN"] == "access-value"
    assert (
        context["REFRESH_COOKIE"]
        == "refresh-value"
    )


def test_context_can_be_used_by_placeholder_resolver():
    context = ScenarioContext()
    resolver = PlaceholderResolver()

    context.set(
        "ACCESS_TOKEN",
        "access-value",
    )

    result = resolver.resolve(
        "Bearer ${ACCESS_TOKEN}",
        context,
    )

    assert result == "Bearer access-value"


def test_context_repr_does_not_expose_values():
    context = ScenarioContext()

    context.set(
        "ACCESS_TOKEN",
        "secret-token-value",
    )

    context_text = repr(context)

    assert "ACCESS_TOKEN" in context_text
    assert "secret-token-value" not in context_text


def test_clear_removes_all_variables():
    context = ScenarioContext()

    context.set(
        "ACCESS_TOKEN",
        "access-value",
    )

    context.set(
        "XSRF_TOKEN",
        "xsrf-value",
    )

    context.clear()

    assert len(context) == 0