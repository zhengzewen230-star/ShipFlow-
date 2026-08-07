import pytest


@pytest.mark.auth
@pytest.mark.smoke
def test_refresh_token_success(auth_client,authenticated_context):
    old_refresh_token = authenticated_context.refresh_token
    response = auth_client.refresh(xsrf_token=authenticated_context.xsrf_token,
                                   refresh_token=old_refresh_token)

    assert response.status_code == 200
    response_json = response.json()
    assert response_json["success"] is True
    assert response_json["message"] == "OK"
    new_access_token = response_json["data"]["accessToken"]
    assert isinstance(new_access_token, str)
    assert len(new_access_token) > 0
    new_refresh_token=response.cookies.get("REFRESH_TOKEN")
    assert new_refresh_token is not None
    assert len(new_refresh_token) > 0
    assert new_refresh_token != old_refresh_token