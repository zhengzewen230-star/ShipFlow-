import re
from collections.abc import Iterator, Mapping
from typing import Any


class ScenarioVariableError(ValueError):
    pass


class ScenarioContext(Mapping[str, Any]):
    VARIABLE_NAME_PATTERN = re.compile(
        r"[A-Z][A-Z0-9_]*"
    )

    def __init__(self):
        self._variables: dict[str, Any] = {}

    def __getitem__(self, variable_name: str) -> Any:
        return self._variables[variable_name]

    def __iter__(self) -> Iterator[str]:
        return iter(self._variables)

    def __len__(self) -> int:
        return len(self._variables)

    def set(
        self,
        variable_name: str,
        variable_value: Any,
    ) -> None:
        self._validate_name(variable_name)

        if variable_value is None:
            raise ScenarioVariableError(
                f"运行时变量不能为None："
                f"{variable_name}"
            )

        self._variables[variable_name] = variable_value

    def require(self, variable_name: str) -> Any:
        if (
            variable_name not in self._variables
            or self._variables[variable_name] is None
        ):
            raise ScenarioVariableError(
                f"缺少运行时变量：{variable_name}"
            )

        return self._variables[variable_name]

    def contains(self, variable_name: str) -> bool:
        return variable_name in self._variables

    def remove(self, variable_name: str) -> None:
        self._variables.pop(variable_name, None)

    def clear(self) -> None:
        self._variables.clear()

    def update_variables(
        self,
        variables: Mapping[str, Any],
    ) -> None:
        for variable_name, variable_value in variables.items():
            self.set(variable_name, variable_value)

    def as_dict(self) -> dict[str, Any]:
        return dict(self._variables)

    def __repr__(self) -> str:
        variable_names = sorted(self._variables)

        return (
            "ScenarioContext("
            f"variable_names={variable_names}"
            ")"
        )

    @classmethod
    def _validate_name(cls, variable_name: str) -> None:
        if not cls.VARIABLE_NAME_PATTERN.fullmatch(
            variable_name
        ):
            raise ScenarioVariableError(
                f"运行时变量名称不合法："
                f"{variable_name}"
            )

    @property
    def xsrf_token(self) -> Any | None:
        return self._variables.get("XSRF_TOKEN")

    @xsrf_token.setter
    def xsrf_token(self, value: Any | None) -> None:
        self._set_compatible_value(
            "XSRF_TOKEN",
            value,
        )

    @property
    def access_token(self) -> Any | None:
        return self._variables.get("ACCESS_TOKEN")

    @access_token.setter
    def access_token(self, value: Any | None) -> None:
        self._set_compatible_value(
            "ACCESS_TOKEN",
            value,
        )

    @property
    def refresh_token(self) -> Any | None:
        return self._variables.get("REFRESH_COOKIE")

    @refresh_token.setter
    def refresh_token(self, value: Any | None) -> None:
        self._set_compatible_value(
            "REFRESH_COOKIE",
            value,
        )

    def _set_compatible_value(
        self,
        variable_name: str,
        value: Any | None,
    ) -> None:
        if value is None:
            self.remove(variable_name)
        else:
            self.set(variable_name, value)