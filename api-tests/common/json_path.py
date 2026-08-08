import re
from collections.abc import Mapping, Sequence
from typing import Any


class JsonPathError(ValueError):
    pass


class JsonPathResolver:
    TOKEN_PATTERN = re.compile(
        r"""
        \.([A-Za-z_][A-Za-z0-9_-]*)
        |
        \[(\d+)\]
        |
        \[['"]([^'"]+)['"]\]
        """,
        re.VERBOSE,
    )

    def resolve(
        self,
        document: Any,
        path: str,
    ) -> Any:
        if path == "$":
            return document

        if not path.startswith("$"):
            raise JsonPathError(
                f"JSONPath必须以$开头：{path}"
            )

        current_value = document
        current_position = 1

        while current_position < len(path):
            match = self.TOKEN_PATTERN.match(
                path,
                current_position,
            )

            if match is None:
                raise JsonPathError(
                    f"不支持的JSONPath：{path}"
                )

            object_key = match.group(1)
            array_index = match.group(2)
            bracket_key = match.group(3)

            if object_key is not None:
                current_value = self._get_object_value(
                    current_value,
                    object_key,
                    path,
                )

            elif bracket_key is not None:
                current_value = self._get_object_value(
                    current_value,
                    bracket_key,
                    path,
                )

            else:
                current_value = self._get_array_value(
                    current_value,
                    int(array_index),
                    path,
                )

            current_position = match.end()

        return current_value

    @staticmethod
    def _get_object_value(
        current_value: Any,
        object_key: str,
        path: str,
    ) -> Any:
        if not isinstance(current_value, Mapping):
            raise JsonPathError(
                f"JSONPath目标不是对象：{path}"
            )

        if object_key not in current_value:
            raise JsonPathError(
                f"JSONPath字段不存在："
                f"{object_key}"
            )

        return current_value[object_key]

    @staticmethod
    def _get_array_value(
        current_value: Any,
        array_index: int,
        path: str,
    ) -> Any:
        if (
            not isinstance(current_value, Sequence)
            or isinstance(
                current_value,
                (str, bytes, bytearray),
            )
        ):
            raise JsonPathError(
                f"JSONPath目标不是数组：{path}"
            )

        if array_index >= len(current_value):
            raise JsonPathError(
                f"JSONPath数组下标越界：{path}"
            )

        return current_value[array_index]