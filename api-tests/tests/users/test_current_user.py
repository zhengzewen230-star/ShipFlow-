from http.client import responses

import pytest

from common.config_loader import settings


@pytest.mark.users
@pytest.mark.smoke
def test_get_current_user_success(user_client,authenticated_context):
    response = user_client.get_current_user(authenticated_context.access_token)
    assert response.status_code == 200
    response_data = response.json()
    assert response_data["success"] is True
    assert response_data["message"] =="OK"
    user_data = response_data["data"]
    assert user_data["username"] == settings["auth"]["username"]
    assert user_data["scope"]=="TENANT"
    assert user_data["userId"] is not None
    assert user_data["tenantId"] is not None

@pytest.mark.users
@pytest.mark.smoke
def test_get_current_user_failure(user_client):
    response=user_client.get_current_user()
    assert response.status_code == 401
    response_data = response.json()
    assert response_data["success"] is False
    assert "error" in response_data
    assert response_data["error"]["code"] is not None