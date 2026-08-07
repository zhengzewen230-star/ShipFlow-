import re
from collections.abc import Mapping
from typing import Any


class PlaceholderResolutionError(ValueError):
    pass


class PlaceholderResolver:
    PLACEHOLDER_PATTERN = re.compile(
        r"\$\{([A-Z][A-Z0-9_]*)\}"
    )

    def resolve(
        self,
        template: Any,
        variables: Mapping[str, Any],
    ) -> Any:
        if isinstance(template, dict):
            return {
                key: self.resolve(value, variables)
                for key, value in template.items()
            }

        if isinstance(template, list):
            return [
                self.resolve(item, variables)
                for item in template
            ]

        if isinstance(template, tuple):
            return tuple(
                self.resolve(item, variables)
                for item in template
            )

        if isinstance(template, str):
            return self._resolve_string(
                template=template,
                variables=variables,
            )

        return template

    def _resolve_string(
        self,
        template: str,
        variables: Mapping[str, Any],
    ) -> Any:
        full_match = self.PLACEHOLDER_PATTERN.fullmatch(
            template
        )

        if full_match:
            variable_name = full_match.group(1)

            return self._get_variable(
                variable_name=variable_name,
                variables=variables,
            )

        def replace_match(match: re.Match) -> str:
            variable_name = match.group(1)

            variable_value = self._get_variable(
                variable_name=variable_name,
                variables=variables,
            )

            if isinstance(
                variable_value,
                (dict, list, tuple, set),
            ):
                raise PlaceholderResolutionError(
                    f"结构化变量不能嵌入字符串："
                    f"{variable_name}"
                )

            return str(variable_value)

        return self.PLACEHOLDER_PATTERN.sub(
            replace_match,
            template,
        )

    @staticmethod
    def _get_variable(
        variable_name: str,
        variables: Mapping[str, Any],
    ) -> Any:
        if (
            variable_name not in variables
            or variables[variable_name] is None
        ):
            raise PlaceholderResolutionError(
                f"缺少运行时变量：{variable_name}"
            )

        return variables[variable_name]