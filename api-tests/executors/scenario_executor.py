from dataclasses import dataclass
from typing import Any

from common.scenario_context import ScenarioContext
from models.api_test_case import ApiTestCase


class ScenarioExecutionError(RuntimeError):
    """完整测试场景执行失败。"""


@dataclass(frozen=True, slots=True)
class ScenarioExecutionResult:
    test_case: ApiTestCase
    response: Any
    extracted_values: dict[str, Any]
    scenario_context: ScenarioContext


class ScenarioExecutor:
    def __init__(
        self,
        *,
        action_executor,
        request_executor,
        extractor_executor,
        assertion_executor,
    ):
        self.action_executor = action_executor
        self.request_executor = request_executor
        self.extractor_executor = extractor_executor
        self.assertion_executor = assertion_executor

    def execute(
        self,
        *,
        test_case: ApiTestCase,
        scenario_context: ScenarioContext | None = None,
    ) -> ScenarioExecutionResult:
        self._validate_test_case(test_case)

        context = (
            scenario_context
            if scenario_context is not None
            else ScenarioContext()
        )

        primary_error: BaseException | None = None

        try:
            # 1. 执行前置动作
            self.action_executor.execute(
                steps=test_case.setup_steps,
                scenario_context=context,
            )

            # 2. 发送当前测试用例的正式请求
            response = self.request_executor.execute(
                test_case=test_case,
                scenario_context=context,
            )

            # 3. 从正式响应中提取变量
            extracted_values = (
                self.extractor_executor.execute(
                    response=response,
                    extractors=test_case.extractors,
                    scenario_context=context,
                )
            )

            # 4. 执行状态码、错误码和扩展断言
            self.assertion_executor.execute(
                response=response,
                test_case=test_case,
                scenario_context=context,
            )

            # 5. 返回本次场景执行结果
            return ScenarioExecutionResult(
                test_case=test_case,
                response=response,
                extracted_values=extracted_values,
                scenario_context=context,
            )

        except BaseException as error:
            primary_error = error
            raise

        finally:
            # 无论请求或断言成功失败，都尝试清理
            try:
                self.action_executor.execute(
                    steps=test_case.teardown_steps,
                    scenario_context=context,
                )

            except Exception as teardown_error:
                message = (
                    f"用例{test_case.case_no}"
                    "后置清理动作执行失败"
                )

                if primary_error is None:
                    # 主流程成功但清理失败：
                    # 整个测试仍然必须失败
                    raise ScenarioExecutionError(
                        message
                    ) from teardown_error

                # 主流程本来已经失败：
                # 保留原始异常，不用清理异常覆盖它
                primary_error.add_note(message)

    @staticmethod
    def _validate_test_case(
        test_case: ApiTestCase,
    ) -> None:
        if not isinstance(test_case, ApiTestCase):
            raise ScenarioExecutionError(
                "test_case必须是ApiTestCase对象"
            )

        if not test_case.enabled:
            raise ScenarioExecutionError(
                f"用例{test_case.case_no}未启用"
            )

        if test_case.automation_status != "READY":
            raise ScenarioExecutionError(
                f"用例{test_case.case_no}"
                f"自动化状态不是READY："
                f"{test_case.automation_status}"
            )