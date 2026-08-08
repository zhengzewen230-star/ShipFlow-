import os
import secrets
from collections.abc import Mapping, Sequence
from typing import Any

from common.scenario_context import ScenarioContext


class ActionExecutionError(RuntimeError):
    """前置或后置动作执行失败。"""


class ActionExecutor:
    SUCCESS_STATUS = {
        "get_csrf": 204,
        "login": 200,
        "logout": 200,
    }

    def __init__(
        self,
        *,
        auth_client,
        extractor_executor,
        credential_profiles: Mapping[str, Any],
        environment: Mapping[str, str] | None = None,
    ):
        self.auth_client = auth_client
        self.extractor_executor = extractor_executor
        self.credential_profiles = credential_profiles
        self.environment = (
            environment
            if environment is not None
            else os.environ
        )

    def execute(
        self,
        *,
        steps: Sequence[Mapping[str, Any]],
        scenario_context: ScenarioContext,
    ) -> list[Any]:
        if isinstance(steps, (str, bytes)):
            raise ActionExecutionError(
                "动作列表必须是数组"
            )

        responses = []

        for index, step in enumerate(steps):
            try:
                response = self._execute_step(
                    step=step,
                    scenario_context=scenario_context,
                )
                responses.append(response)

            except ActionExecutionError:
                raise

            except Exception as error:
                action = step.get("action", "unknown")

                raise ActionExecutionError(
                    f"第{index + 1}个动作执行失败：{action}"
                ) from error

        return responses

    def _execute_step(
        self,
        *,
        step: Mapping[str, Any],
        scenario_context: ScenarioContext,
    ) -> Any:
        if not isinstance(step, Mapping):
            raise ActionExecutionError(
                "每个动作必须是对象"
            )

        action = step.get("action")

        if action == "get_csrf":
            return self._execute_get_csrf(
                step=step,
                scenario_context=scenario_context,
            )

        if action == "login":
            return self._execute_login(
                step=step,
                scenario_context=scenario_context,
            )

        if action == "logout":
            return self._execute_logout(
                step=step,
                scenario_context=scenario_context,
            )

        if action == "create_token_fixture":
            self._execute_create_token_fixture(
                step=step,
                scenario_context=scenario_context,
            )
            return None

        raise ActionExecutionError(
            f"不支持的动作类型：{action}"
        )

    def _execute_get_csrf(
        self,
        *,
        step: Mapping[str, Any],
        scenario_context: ScenarioContext,
    ):
        reference_value = self._get_reference_value(
            step=step,
            scenario_context=scenario_context,
        )

        response = self.auth_client.get_csrf_token()

        self._assert_response_status(
            action="get_csrf",
            response=response,
        )

        extracted = self._extract_saved_values(
            step=step,
            response=response,
            scenario_context=scenario_context,
        )

        self._assert_value_changed(
            step=step,
            extracted=extracted,
            reference_value=reference_value,
        )

        return response

    def _execute_login(
        self,
        *,
        step: Mapping[str, Any],
        scenario_context: ScenarioContext,
    ):
        profile_name = step.get("credential_profile")

        if not profile_name:
            raise ActionExecutionError(
                "login动作缺少credential_profile"
            )

        credentials = self._load_credentials(
            profile_name=profile_name
        )

        xsrf_token = scenario_context.require(
            "XSRF_TOKEN"
        )

        response = self.auth_client.login(
            tenant_code=credentials["tenant_code"],
            username=credentials["username"],
            password=credentials["password"],
            xsrf_token=xsrf_token,
        )

        self._assert_response_status(
            action="login",
            response=response,
        )

        self._extract_saved_values(
            step=step,
            response=response,
            scenario_context=scenario_context,
        )

        return response

    def _execute_logout(
        self,
        *,
        step: Mapping[str, Any],
        scenario_context: ScenarioContext,
    ):
        context_config = step.get("context") or {}

        xsrf_variable = context_config.get(
            "xsrf_token",
            "XSRF_TOKEN",
        )

        refresh_variable = context_config.get(
            "refresh_cookie",
            "REFRESH_COOKIE",
        )

        xsrf_token = scenario_context.require(
            xsrf_variable
        )

        refresh_cookie = scenario_context.require(
            refresh_variable
        )

        response = self.auth_client.logout(
            xsrf_token=xsrf_token,
            refresh_token=refresh_cookie,
        )

        self._assert_response_status(
            action="logout",
            response=response,
        )

        self._extract_saved_values(
            step=step,
            response=response,
            scenario_context=scenario_context,
        )

        return response

    def _execute_create_token_fixture(
        self,
        *,
        step: Mapping[str, Any],
        scenario_context: ScenarioContext,
    ) -> None:
        fixture_type = step.get("fixture_type")
        save_as = step.get("save_as")

        if not fixture_type or not save_as:
            raise ActionExecutionError(
                "create_token_fixture缺少"
                "fixture_type或save_as"
            )

        if fixture_type == "TAMPERED_ACCESS_TOKEN":
            input_variable = step.get("input")

            if not input_variable:
                raise ActionExecutionError(
                    "篡改Token动作缺少input"
                )

            original_token = scenario_context.require(
                input_variable
            )

            fixture_value = self._tamper_jwt(
                original_token
            )

        elif fixture_type == "INVALID_REFRESH_TOKEN":
            fixture_value = (
                "invalid-"
                + secrets.token_urlsafe(48)
            )

        else:
            raise ActionExecutionError(
                f"不支持的Token测试数据类型："
                f"{fixture_type}"
            )

        scenario_context.set(
            save_as,
            fixture_value,
        )

    def _load_credentials(
        self,
        *,
        profile_name: str,
    ) -> dict[str, Any]:
        profile = self.credential_profiles.get(
            profile_name
        )

        if not isinstance(profile, Mapping):
            raise ActionExecutionError(
                f"凭据档案不存在：{profile_name}"
            )

        username = profile.get("username")
        password_env = profile.get("password_env")

        if not username or not password_env:
            raise ActionExecutionError(
                f"凭据档案配置不完整：{profile_name}"
            )

        password = self.environment.get(password_env)

        if not password:
            raise ActionExecutionError(
                f"密码环境变量未配置：{password_env}"
            )

        return {
            "tenant_code": profile.get("tenant_code"),
            "username": username,
            "password": password,
        }

    def _extract_saved_values(
        self,
        *,
        step: Mapping[str, Any],
        response,
        scenario_context: ScenarioContext,
    ) -> dict[str, Any]:
        save_config = step.get("save") or {}

        if not save_config:
            return {}

        if not isinstance(save_config, Mapping):
            raise ActionExecutionError(
                "save必须是对象"
            )

        extractors = []

        for source_expression, save_as in save_config.items():
            if "." not in source_expression:
                raise ActionExecutionError(
                    f"保存表达式格式错误："
                    f"{source_expression}"
                )

            source, path = source_expression.split(
                ".",
                1,
            )

            extractors.append(
                {
                    "source": source,
                    "path": path,
                    "save_as": save_as,
                }
            )

        return self.extractor_executor.execute(
            response=response,
            extractors=extractors,
            scenario_context=scenario_context,
        )

    def _get_reference_value(
        self,
        *,
        step: Mapping[str, Any],
        scenario_context: ScenarioContext,
    ) -> Any | None:
        reference_name = step.get(
            "ensure_different_from"
        )

        if not reference_name:
            return None

        return scenario_context.require(
            reference_name
        )

    @staticmethod
    def _assert_value_changed(
        *,
        step: Mapping[str, Any],
        extracted: Mapping[str, Any],
        reference_value: Any | None,
    ) -> None:
        reference_name = step.get(
            "ensure_different_from"
        )

        if not reference_name:
            return

        if not extracted:
            raise ActionExecutionError(
                "动作没有提取可比较的结果"
            )

        new_value = next(iter(extracted.values()))

        if new_value == reference_value:
            raise ActionExecutionError(
                f"新值不应与{reference_name}相同"
            )

    def _assert_response_status(
        self,
        *,
        action: str,
        response,
    ) -> None:
        expected_status = self.SUCCESS_STATUS[action]

        if response.status_code != expected_status:
            raise ActionExecutionError(
                f"{action}动作失败，"
                f"预期HTTP {expected_status}，"
                f"实际HTTP {response.status_code}"
            )

    @staticmethod
    def _tamper_jwt(token: str) -> str:
        if not isinstance(token, str):
            raise ActionExecutionError(
                "Access Token必须是字符串"
            )

        parts = token.split(".")

        if len(parts) != 3 or not parts[2]:
            raise ActionExecutionError(
                "Access Token不是合法JWT结构"
            )

        signature = parts[2]

        replacement = (
            "A"
            if signature[-1] != "A"
            else "B"
        )

        parts[2] = signature[:-1] + replacement

        return ".".join(parts)