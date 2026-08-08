def assert_success_response(
    response,
    expected_status=200
):
    assert response.status_code == expected_status, (
        f"预期状态码：{expected_status}，"
        f"实际状态码：{response.status_code}，"
        f"响应内容：{response.text}"
    )

    response_json = response.json()

    assert response_json["success"] is True
    assert response_json["message"] == "OK"
    assert response_json["traceId"] is not None

    return response_json


def assert_error_response(
    response,
    expected_status,
    expected_error_code
):
    assert response.status_code == expected_status, (
        f"预期状态码：{expected_status}，"
        f"实际状态码：{response.status_code}，"
        f"响应内容：{response.text}"
    )

    response_json = response.json()

    assert response_json["success"] is False
    assert response_json["traceId"] is not None
    assert "error" in response_json

    actual_error_code = (
        response_json["error"]["code"]
    )

    assert actual_error_code == expected_error_code, (
        f"预期错误码：{expected_error_code}，"
        f"实际错误码：{actual_error_code}"
    )

    return response_json