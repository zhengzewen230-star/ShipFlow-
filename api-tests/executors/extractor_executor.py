from typing import Any

from common.json_path import (
    JsonPathError,
    JsonPathResolver,
)
from common.scenario_context import ScenarioContext


class ExtractionError(ValueError):
    pass


class ExtractorExecutor:
    SUPPORTED_SOURCES = {
        "json",
        "cookie",
        "header",
    }

    def __init__(
        self,
        json_path_resolver: JsonPathResolver,
    ):
        self.json_path_resolver = json_path_resolver

    def execute(
        self,
        response,
        extractors: list[dict[str, Any]],
        scenario_context: ScenarioContext,
    ) -> dict[str, Any]:
        if not extractors:
            return {}

        extracted_values: dict[str, Any] = {}
        json_body: Any = None
        json_loaded = False

        for extractor in extractors:
            self._validate_extractor(extractor)

            source = extractor["source"]
            path = extractor["path"]
            save_as = extractor["save_as"]

            if save_as in extracted_values:
                raise ExtractionError(
                    f"提取变量重复：{save_as}"
                )

            if source == "json":
                if not json_loaded:
                    json_body = self._read_json(response)
                    json_loaded = True

                value = self._extract_json(
                    json_body,
                    path,
                )

            elif source == "cookie":
                value = response.cookies.get(path)

            elif source == "header":
                value = response.headers.get(path)

            else:
                raise ExtractionError(
                    f"不支持的提取来源：{source}"
                )

            if value is None:
                raise ExtractionError(
                    f"响应中未提取到值："
                    f"{source}.{path}"
                )

            extracted_values[save_as] = value

        validation_context = ScenarioContext()
        validation_context.update_variables(
            extracted_values
        )

        scenario_context.update_variables(
            extracted_values
        )

        return extracted_values

    @staticmethod
    def _validate_extractor(
        extractor: Any,
    ) -> None:
        if not isinstance(extractor, dict):
            raise ExtractionError(
                "extractor必须是字典"
            )

        required_fields = {
            "source",
            "path",
            "save_as",
        }

        missing_fields = (
            required_fields - extractor.keys()
        )

        if missing_fields:
            raise ExtractionError(
                "extractor缺少字段："
                + ", ".join(sorted(missing_fields))
            )

        if extractor["source"] not in (
            ExtractorExecutor.SUPPORTED_SOURCES
        ):
            raise ExtractionError(
                "不支持的提取来源："
                f"{extractor['source']}"
            )

        if not isinstance(extractor["path"], str):
            raise ExtractionError(
                "extractor.path必须是字符串"
            )

        if not isinstance(
            extractor["save_as"],
            str,
        ):
            raise ExtractionError(
                "extractor.save_as必须是字符串"
            )

    @staticmethod
    def _read_json(response) -> Any:
        try:
            return response.json()
        except ValueError as exception:
            raise ExtractionError(
                "响应体不是合法JSON，无法提取"
            ) from exception

    def _extract_json(
        self,
        json_body: Any,
        path: str,
    ) -> Any:
        try:
            return self.json_path_resolver.resolve(
                json_body,
                path,
            )
        except JsonPathError as exception:
            raise ExtractionError(
                f"JSON提取失败：{path}"
            ) from exception