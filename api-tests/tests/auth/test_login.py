import os

import allure
import pytest

from common.config_loader import (PROJECT_ROOT, load_yaml, settings)
from common.assertions import (
    assert_error_response,
    assert_success_response
)
auth_data_path=PROJECT_ROOT/"data"/"auth_data.yaml"
auth_data=load_yaml(auth_data_path)
login_failure_cases=auth_data["login_failure_cases"]

@allure.feature("认证管理")
@allure.story("用户登录")
@allure.title("租户管理员使用正确账号密码登陆成功")
@allure.severity(allure.severity_level.CRITICAL)
@pytest.mark.auth
@pytest.mark.smoke
def test_login_success(auth_client):
    password=os.getenv("SHIPFLOW_TEST_PASSWORD")
    if not password:
        pytest.fail("Please set SHIPFLOW_TEST_PASSWORD env variable")
    with allure.step("获取CSRF Token"):
        csrf_response=auth_client.get_csrf_token()
        assert csrf_response.status_code == 204
        xsrf_token=csrf_response.cookies.get("XSRF-TOKEN")
        assert xsrf_token is not None
        assert len(xsrf_token)>0

    with allure.step("使用正确的租户、用户名和密码登录"):
        login_response=auth_client.login(tenant_code=settings["auth"]["tenant_code"],
                                         username=settings["auth"]["username"],
                                         password=password,
                                         xsrf_token=xsrf_token)

        assert login_response.status_code == 200

    with allure.step("验证码登录响应"):
        response_data = assert_success_response(
            login_response
        )
    with allure.step("验证响应中返回的Access Token"):
        access_token=response_data["data"]["accessToken"]
        assert access_token is not None
        assert isinstance(access_token,str)
        assert len(access_token)>0
        assert len(login_response.cookies)>0

login_failure_case_map = {case["case_id"]: case for case in login_failure_cases}
@pytest.mark.auth
@pytest.mark.negative
@pytest.mark.parametrize("case_id",login_failure_case_map.keys())
def test_login_failure(auth_client,case_id):
    with allure.step("获取CSRF Token"):
        csrf_response=auth_client.get_csrf_token()
        assert csrf_response.status_code == 204
        xsrf_token=csrf_response.cookies.get("XSRF-TOKEN")
        assert xsrf_token is not None
    case_data=login_failure_case_map[case_id]
    allure.dynamic.title(f"{case_data['title']}:登录返回认证失败")
    allure.dynamic.parameter("case_id",case_id)

    with allure.step(f"执行异常登录场景：{case_id}"):
        response=auth_client.login(tenant_code=case_data["tenant_code"],
                                   username=case_data["username"],
                                   password=case_data["password"],
                                    xsrf_token=xsrf_token)
    with allure.step("验证HTTP状态码和业务错误码"):
        assert_error_response(
            response=response,
            expected_status=case_data["expected_status"],
            expected_error_code=case_data["expected_error_code"]
        )
