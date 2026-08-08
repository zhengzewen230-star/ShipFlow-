import pytest

from common.placeholder_resolver import (
    PlaceholderResolutionError,
    PlaceholderResolver,
)


def test_resolve_full_placeholder_preserves_type():
    resolver = PlaceholderResolver()

    variables = {
        "RETRY_COUNT": 3,
        "ENABLED": True,
    }

    assert resolver.resolve(
        "${RETRY_COUNT}",
        variables,
    ) == 3

    assert resolver.resolve(
        "${ENABLED}",
        variables,
    ) is True


def test_resolve_embedded_placeholder():
    resolver = PlaceholderResolver()

    result = resolver.resolve(
        "Bearer ${ACCESS_TOKEN}",
        {
            "ACCESS_TOKEN": "test-access-token",
        },
    )

    assert result == "Bearer test-access-token"


def test_resolve_nested_template():
    resolver = PlaceholderResolver()

    template = {
        "headers": {
            "Authorization": "Bearer ${ACCESS_TOKEN}",
            "X-XSRF-TOKEN": "${XSRF_TOKEN}",
        },
        "cookies": [
            "${REFRESH_COOKIE}",
        ],
    }

    result = resolver.resolve(
        template,
        {
            "ACCESS_TOKEN": "access-value",
            "XSRF_TOKEN": "xsrf-value",
            "REFRESH_COOKIE": "refresh-value",
        },
    )

    assert result == {
        "headers": {
            "Authorization": "Bearer access-value",
            "X-XSRF-TOKEN": "xsrf-value",
        },
        "cookies": [
            "refresh-value",
        ],
    }


def test_missing_placeholder_raises_error():
    resolver = PlaceholderResolver()

    with pytest.raises(
        PlaceholderResolutionError,
        match="ACCESS_TOKEN",
    ):
        resolver.resolve(
            "${ACCESS_TOKEN}",
            {},
        )


def test_resolve_does_not_modify_original_template():
    resolver = PlaceholderResolver()

    template = {
        "token": "${ACCESS_TOKEN}",
    }

    result = resolver.resolve(
        template,
        {
            "ACCESS_TOKEN": "resolved-value",
        },
    )

    assert template == {
        "token": "${ACCESS_TOKEN}",
    }

    assert result == {
        "token": "resolved-value",
    }


def test_structured_value_cannot_be_embedded():
    resolver = PlaceholderResolver()

    with pytest.raises(
        PlaceholderResolutionError,
        match="ITEMS",
    ):
        resolver.resolve(
            "items=${ITEMS}",
            {
                "ITEMS": [1, 2, 3],
            },
        )