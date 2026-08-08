import allure
import pytest


PRIORITY_SEVERITY = {
    "P0": allure.severity_level.CRITICAL,
    "P1": allure.severity_level.NORMAL,
    "P2": allure.severity_level.MINOR,
    "P3": allure.severity_level.TRIVIAL,
}


@pytest.mark.auth
@pytest.mark.database_driven
def test_database_driven_auth_case(
    database_api_test_case,
    scenario_executor,
    database_scenario_context,
):
    test_case = database_api_test_case

    allure.dynamic.title(
        f"{test_case.case_no} "
        f"{test_case.title}"
    )

    allure.dynamic.feature(
        "认证与权限模块"
    )

    allure.dynamic.story(
        test_case.module
    )

    allure.dynamic.severity(
        PRIORITY_SEVERITY.get(
            test_case.priority,
            allure.severity_level.NORMAL,
        )
    )

    allure.dynamic.parameter(
        "case_no",
        test_case.case_no,
    )

    allure.dynamic.parameter(
        "test_type",
        test_case.test_type,
    )

    for tag in test_case.tags:
        allure.dynamic.tag(tag)

    description = (
        f"前置条件：{test_case.precondition}\n\n"
        f"数据依赖：{test_case.data_dependency}\n\n"
        f"请求：{test_case.http_method} "
        f"{test_case.request_path}"
    )

    allure.dynamic.description(description)

    with allure.step(
        f"执行接口用例：{test_case.case_no}"
    ):
        result = scenario_executor.execute(
            test_case=test_case,
            scenario_context=database_scenario_context,
        )

    assert result.response is not None