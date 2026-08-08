import pytest


@pytest.mark.auth
@pytest.mark.smoke
def test_logout_success(auth_client,authenticated_context):
    xsrf_token = authenticated_context.xsrf_token
    refresh_token = authenticated_context.refresh_token
    logout_response = auth_client.logout(xsrf_token,refresh_token)
    assert logout_response.status_code == 200
    response_json = logout_response.json()
    assert response_json["message"] == "OK"
    assert response_json["success"] is True
    assert response_json["data"] is  None

    refresh_token = auth_client.refresh(xsrf_token,refresh_token)
    assert refresh_token.status_code == 401
    refresh_json = refresh_token.json()
    assert refresh_json["success"] is False
    assert refresh_json["error"]["code"] == "AUTH-1002"